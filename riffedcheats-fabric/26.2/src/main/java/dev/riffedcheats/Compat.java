package dev.riffedcheats;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Everything specific to Minecraft 26.2 / Fabric lives here. If the build complains after a Minecraft update,
 * the fix is almost always a one-line change in this file, in Gfx.java, in MenuScreen.java or in BlockMixin.java.
 */
public final class Compat {
    private static Minecraft mc() { return Minecraft.getInstance(); }

    public static Path configDir() { return FabricLoader.getInstance().getConfigDir(); }

    // ---------------- permissions / commands ----------------
    public static boolean hostingLocalServer() { return mc().getSingleplayerServer() != null; }

    /** 26.x replaced the numeric op level with a permission set, so this tries the new API first, then the old one. */
    public static boolean hasOp(LocalPlayer p) {
        Object[] sources = { p, p.connection == null ? null : p.connection.getSuggestionsProvider() };
        for (Object src : sources) {
            if (src == null) continue;
            try {
                Object perms = Refl.call(src, "permissions");
                Object gm = Class.forName("net.minecraft.server.permissions.Permissions").getField("COMMANDS_GAMEMASTER").get(null);
                return (boolean) Refl.call(perms, "hasPermission", gm);
            } catch (Throwable ignored) {}
        }
        try { return (boolean) Refl.call(p, "hasPermissions", 2); } catch (Throwable ignored) {}
        return false;
    }

    private static CommandSourceStack elevated(ServerPlayer sp) {
        CommandSourceStack s = sp.createCommandSourceStack();
        try {
            Object owner = Class.forName("net.minecraft.server.permissions.LevelBasedPermissionSet").getField("OWNER").get(null);
            return (CommandSourceStack) Refl.call(s, "withPermission", owner);
        } catch (Throwable t) {
            try { return (CommandSourceStack) Refl.call(s, "withPermission", 4); } catch (Throwable t2) { return s; }
        }
    }

    public static void runLocal(String cmd) {
        LocalPlayer p = mc().player;
        IntegratedServer srv = mc().getSingleplayerServer();
        if (p == null || srv == null) return;
        UUID id = p.getUUID();
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayer(id);
            if (sp != null) srv.getCommands().performPrefixedCommand(elevated(sp), cmd);
        });
    }

    public static void runRemote(String cmd) {
        LocalPlayer p = mc().player;
        if (p != null) p.connection.sendCommand(cmd);
    }

    public static void suggest(String text, Consumer<List<String>> cb) {
        LocalPlayer p = mc().player;
        if (p == null) { cb.accept(List.of()); return; }
        IntegratedServer srv = mc().getSingleplayerServer();
        if (srv != null) {
            UUID id = p.getUUID();
            srv.execute(() -> {
                ServerPlayer sp = srv.getPlayerList().getPlayer(id);
                if (sp == null) { cb.accept(List.of()); return; }
                Suggest.with(srv.getCommands().getDispatcher(), elevated(sp), text, cb);
            });
        } else {
            ClientPacketListener c = p.connection;
            Suggest.with(c.getCommands(), c.getSuggestionsProvider(), text, cb);
        }
    }

    // ---------------- command syntax that changed between versions ----------------
    /** 1.21.5+ flattened the enchantments component: no more {levels:{...}} wrapper. */
    public static String ench(String csv) { return "enchantments={" + csv + "}"; }
    public static String enchBook(String csv) { return "stored_enchantments={" + csv + "}"; }

    /** Attribute ids lost their generic./player. prefix in 1.21.2. */
    public static String attr(String key) { return "minecraft:" + key; }

    /** Game rules became snake_case ids in the 1.21.11 / 26.x era. */
    public static String gamerule(String key) {
        return switch (key) {
            case "daylight" -> "advance_time";
            case "weather" -> "advance_weather";
            case "mobspawn" -> "spawn_mobs";
            default -> key;
        };
    }

    // ---------------- player / world ----------------
    private static void abil(Object player, String field, boolean v) {
        try {
            Object ab = Refl.call(player, "getAbilities");
            try { Refl.setField(ab, field, v); }
            catch (Exception e) { Refl.call(ab, "set" + Character.toUpperCase(field.charAt(0)) + field.substring(1), v); }
        } catch (Throwable ignored) {}
    }
    private static boolean abilGet(Object player, String field) {
        try { return (boolean) Refl.getField(Refl.call(player, "getAbilities"), field); } catch (Throwable t) { return false; }
    }

    public static void setMayFly(LocalPlayer p, boolean v) { abil(p, "mayfly", v); }
    public static void setFlying(LocalPlayer p, boolean v) { abil(p, "flying", v); }
    public static boolean isFlying(LocalPlayer p) { return abilGet(p, "flying"); }
    public static void setFlySpeed(LocalPlayer p, float v) {
        try { Refl.call(Refl.call(p, "getAbilities"), "setFlyingSpeed", v); } catch (Throwable ignored) {}
    }

    public static void setServerMayFly(boolean want) {
        LocalPlayer p = mc().player;
        IntegratedServer srv = mc().getSingleplayerServer();
        if (p == null || srv == null) return;
        UUID id = p.getUUID();
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayer(id);
            if (sp == null) return;
            boolean w = want || sp.isCreative() || sp.isSpectator();
            if (abilGet(sp, "mayfly") != w) {
                abil(sp, "mayfly", w);
                if (!w) abil(sp, "flying", false);
                sp.onUpdateAbilities();
            }
        });
    }

    public static Iterable<Entity> entities(ClientLevel level) { return level.entitiesForRendering(); }
    public static void attack(Entity e) { if (mc().gameMode != null && mc().player != null) mc().gameMode.attack(mc().player, e); }
    public static void glow(Entity e, boolean on) { e.setGlowingTag(on); }

    /** The Pos packet gained extra flags over time, so the constructor is picked by shape instead of hard-coded. */
    public static void sendPos(double x, double y, double z, boolean onGround) {
        LocalPlayer p = mc().player;
        if (p == null) return;
        try {
            for (Constructor<?> k : ServerboundMovePlayerPacket.Pos.class.getConstructors()) {
                Class<?>[] t = k.getParameterTypes();
                if (t.length >= 4 && t[0] == double.class && t[1] == double.class && t[2] == double.class && t[3] == boolean.class) {
                    Object[] a = new Object[t.length];
                    a[0] = x; a[1] = y; a[2] = z; a[3] = onGround;
                    for (int i = 4; i < t.length; i++) a[i] = t[i] == boolean.class ? (Object) Boolean.FALSE : null;
                    p.connection.send((Packet<?>) k.newInstance(a));
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void sendSprint(LocalPlayer p, boolean start) {
        p.connection.send(new ServerboundPlayerCommandPacket(p,
                start ? ServerboundPlayerCommandPacket.Action.START_SPRINTING : ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
    }

    // ---------------- effects ----------------
    private static String effId(MobEffectInstance i) {
        String s = i.getEffect().unwrapKey().map(Object::toString).orElse("");
        int a = s.lastIndexOf("/ ");
        return a < 0 || !s.endsWith("]") ? s : s.substring(a + 2, s.length() - 1);
    }
    public static int effectTicks(LocalPlayer p, String id) {
        for (MobEffectInstance i : p.getActiveEffects()) if (effId(i).equals(id)) return i.isInfiniteDuration() ? 1_000_000 : i.getDuration();
        return -1;
    }
    public static int effectAmp(LocalPlayer p, String id) {
        for (MobEffectInstance i : p.getActiveEffects()) if (effId(i).equals(id)) return i.getAmplifier();
        return -1;
    }
    public static boolean hasNightVision(LocalPlayer p) { return p.hasEffect(MobEffects.NIGHT_VISION); }
    public static void addNightVision(LocalPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
    }
    public static void removeNightVision(LocalPlayer p) { p.removeEffect(MobEffects.NIGHT_VISION); }

    // ---------------- camera / rendering ----------------
    public static Entity newCamera(ClientLevel level, LocalPlayer p) {
        try {
            Object profile = Refl.callAny(p, "getGameProfile", "gameProfile");
            for (Constructor<?> k : RemotePlayer.class.getConstructors())
                if (k.getParameterCount() == 2 && k.getParameterTypes()[0].isInstance(level) && k.getParameterTypes()[1].isInstance(profile))
                    return (Entity) k.newInstance(level, profile);
        } catch (Throwable ignored) {}
        return p; // freecam then simply does nothing instead of crashing
    }
    public static void setCamera(Entity e) { mc().setCameraEntity(e != null ? e : mc().player); }
    public static void moveCamera(Entity c, double x, double y, double z, float yaw, float pitch, boolean interpolateRot) {
        c.xo = c.getX(); c.yo = c.getY(); c.zo = c.getZ();
        if (interpolateRot) {
            c.xRotO = c.getXRot(); c.yRotO = c.getYRot();
            if (c instanceof LivingEntity l) l.yHeadRotO = l.yHeadRot;
        } else {
            c.xRotO = pitch; c.yRotO = yaw;
            if (c instanceof LivingEntity l) l.yHeadRotO = yaw;
        }
        c.setPos(x, y, z);
        c.setYRot(yaw); c.setXRot(pitch);
        // LivingEntity cameras look along the HEAD rotation, not the body rotation
        if (c instanceof LivingEntity l) { l.yHeadRot = yaw; l.yBodyRot = yaw; }
    }
    public static void setCameraAngles(Entity c, float yaw, float pitch) {
        c.xRotO = pitch; c.yRotO = yaw; c.setYRot(yaw); c.setXRot(pitch);
        if (c instanceof LivingEntity l) { l.yHeadRotO = yaw; l.yHeadRot = yaw; l.yBodyRot = yaw; }
    }
    public static void lockRotation(LocalPlayer p, float yaw, float pitch) {
        p.setYRot(yaw); p.setXRot(pitch); p.yRotO = yaw; p.xRotO = pitch;
    }

    /** Replaces the player's input with a blank one so WASD does not move your body while in freecam. */
    public static Object freezeInput(LocalPlayer p) {
        try {
            Object old = Refl.getField(p, "input");
            Constructor<?> k = old.getClass().getSuperclass() == Object.class ? old.getClass().getDeclaredConstructor()
                    : Class.forName("net.minecraft.client.player.ClientInput").getDeclaredConstructor();
            k.setAccessible(true);
            Refl.setField(p, "input", k.newInstance());
            return old;
        } catch (Throwable t) { return null; }
    }
    public static void restoreInput(LocalPlayer p, Object saved) {
        if (saved == null) return;
        try { Refl.setField(p, "input", saved); } catch (Throwable ignored) {}
    }

    public static void setRenderHand(boolean v) { try { Refl.call(mc().gameRenderer, "setRenderHand", v); } catch (Throwable ignored) {} }
    public static void setSmartCull(boolean v) { try { Refl.setField(mc(), "smartCull", v); } catch (Throwable ignored) {} }
    public static void rebuildChunks() {
        try { if (mc().levelRenderer != null) Refl.call(mc().levelRenderer, "allChanged"); } catch (Throwable ignored) {}
    }

    /** Long.MIN_VALUE = unknown (26.x reworked world time into clocks), the menu then falls back to /time set. */
    public static long dayTime() {
        if (mc().level == null) return Long.MIN_VALUE;
        try { return (long) Refl.callAny(mc().level, "getDayTime", "getOverworldClockTime", "getDayTimeTicks"); }
        catch (Throwable t) { return Long.MIN_VALUE; }
    }

    public static int[] spawnPos() {
        try {
            Object b = Refl.callAny(mc().level, "getSharedSpawnPos");
            return new int[] { (int) Refl.call(b, "getX"), (int) Refl.call(b, "getY"), (int) Refl.call(b, "getZ") };
        } catch (Throwable t) { return new int[] { 0, 100, 0 }; }
    }

    public static List<String> onlinePlayers() {
        List<String> l = new ArrayList<>();
        ClientPacketListener c = mc().getConnection();
        if (c != null) for (PlayerInfo i : c.getOnlinePlayers()) {
            try { l.add((String) Refl.callAny(Refl.callAny(i, "getProfile", "profile"), "name", "getName")); } catch (Throwable ignored) {}
        }
        Collections.sort(l, String.CASE_INSENSITIVE_ORDER);
        return l;
    }

    public static String clipboard() { return mc().keyboardHandler.getClipboard(); }

    // ---------------- enchantments ----------------
    private static Registry<Enchantment> reg() { return mc().level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT); }

    public static List<String> enchantIds() {
        List<String> l = new ArrayList<>();
        for (var r : reg().keySet()) l.add(r.toString());
        Collections.sort(l);
        return l;
    }
    public static int enchantMax(String id) {
        Enchantment e = reg().getValue(Identifier.parse(id));
        return e == null ? 1 : e.getMaxLevel();
    }
    public static boolean enchantFits(String id, ItemStack st) {
        if (st.is(Items.ENCHANTED_BOOK) || st.is(Items.BOOK)) return true;
        Enchantment e = reg().getValue(Identifier.parse(id));
        if (e == null) return false;
        try { return (boolean) Refl.call(e, "isSupportedItem", st); } catch (Throwable ignored) {}
        try { return (boolean) Refl.call(e, "canEnchant", st); } catch (Throwable ignored) {}
        return true;
    }
}
