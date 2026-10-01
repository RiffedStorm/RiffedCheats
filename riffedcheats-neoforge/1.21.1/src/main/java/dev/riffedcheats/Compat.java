package dev.riffedcheats;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.fml.loading.FMLPaths;

/** Everything that is specific to Minecraft 1.21.1 / NeoForge lives here. */
public final class Compat {
    private static Minecraft mc() { return Minecraft.getInstance(); }

    public static Path configDir() { return FMLPaths.CONFIGDIR.get(); }

    // ---------------- permissions / commands ----------------
    public static boolean hostingLocalServer() { return mc().getSingleplayerServer() != null; }
    public static boolean hasOp(LocalPlayer p) { return p.hasPermissions(2); }

    private static CommandSourceStack elevated(ServerPlayer sp) { return sp.createCommandSourceStack().withPermission(4); }

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
    public static String ench(String csv) { return "enchantments={levels:{" + csv + "}}"; }
    public static String enchBook(String csv) { return "stored_enchantments={levels:{" + csv + "}}"; }

    public static String attr(String key) {
        boolean player = key.equals("block_interaction_range") || key.equals("entity_interaction_range");
        return "minecraft:" + (player ? "player." : "generic.") + key;
    }

    public static String gamerule(String key) {
        return switch (key) {
            case "keep_inventory" -> "keepInventory";
            case "mob_griefing" -> "mobGriefing";
            case "daylight" -> "doDaylightCycle";
            case "weather" -> "doWeatherCycle";
            case "mobspawn" -> "doMobSpawning";
            default -> key;
        };
    }

    // ---------------- player / world ----------------
    public static void setMayFly(LocalPlayer p, boolean v) { p.getAbilities().mayfly = v; }
    public static void setFlying(LocalPlayer p, boolean v) { p.getAbilities().flying = v; }
    public static boolean isFlying(LocalPlayer p) { return p.getAbilities().flying; }
    public static void setFlySpeed(LocalPlayer p, float v) { p.getAbilities().setFlyingSpeed(v); }

    /** Only used in your own world: the integrated server must also allow flying or it may rubber-band. */
    public static void setServerMayFly(boolean want) {
        LocalPlayer p = mc().player;
        IntegratedServer srv = mc().getSingleplayerServer();
        if (p == null || srv == null) return;
        UUID id = p.getUUID();
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayer(id);
            if (sp == null) return;
            Abilities ab = sp.getAbilities();
            boolean w = want || sp.isCreative() || sp.isSpectator();
            if (ab.mayfly != w) { ab.mayfly = w; if (!w) ab.flying = false; sp.onUpdateAbilities(); }
        });
    }

    public static Iterable<Entity> entities(ClientLevel level) { return level.entitiesForRendering(); }
    public static void attack(Entity e) { if (mc().gameMode != null && mc().player != null) mc().gameMode.attack(mc().player, e); }
    public static void glow(Entity e, boolean on) { e.setGlowingTag(on); }

    public static void sendPos(double x, double y, double z, boolean onGround) {
        LocalPlayer p = mc().player;
        if (p != null) p.connection.send(new ServerboundMovePlayerPacket.Pos(x, y, z, onGround));
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
    public static Entity newCamera(ClientLevel level, LocalPlayer p) { return new RemotePlayer(level, p.getGameProfile()); }
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
    public static Object freezeInput(LocalPlayer p) { Input old = p.input; p.input = new Input(); return old; }
    public static void restoreInput(LocalPlayer p, Object saved) { if (saved instanceof Input i) p.input = i; }
    public static void setRenderHand(boolean v) { mc().gameRenderer.setRenderHand(v); }
    public static void setSmartCull(boolean v) { mc().smartCull = v; }
    public static void rebuildChunks() { if (mc().levelRenderer != null) mc().levelRenderer.allChanged(); }

    public static long dayTime() { return mc().level == null ? 0 : mc().level.getDayTime(); }
    public static int[] spawnPos() {
        var b = mc().level.getSharedSpawnPos();
        return new int[] { b.getX(), b.getY(), b.getZ() };
    }

    public static List<String> onlinePlayers() {
        List<String> l = new ArrayList<>();
        ClientPacketListener c = mc().getConnection();
        if (c != null) for (PlayerInfo i : c.getOnlinePlayers()) l.add(i.getProfile().getName());
        Collections.sort(l, String.CASE_INSENSITIVE_ORDER);
        return l;
    }

    public static String clipboard() { return mc().keyboardHandler.getClipboard(); }

    // ---------------- enchantments ----------------
    private static Registry<Enchantment> reg() { return mc().level.registryAccess().registryOrThrow(Registries.ENCHANTMENT); }

    public static List<String> enchantIds() {
        List<String> l = new ArrayList<>();
        for (ResourceLocation r : reg().keySet()) l.add(r.toString());
        Collections.sort(l);
        return l;
    }
    public static int enchantMax(String id) {
        Enchantment e = reg().get(ResourceLocation.parse(id));
        return e == null ? 1 : e.getMaxLevel();
    }
    public static boolean enchantFits(String id, ItemStack st) {
        if (st.is(Items.ENCHANTED_BOOK) || st.is(Items.BOOK)) return true;
        Enchantment e = reg().get(ResourceLocation.parse(id));
        return e != null && e.canEnchant(st);
    }
}
