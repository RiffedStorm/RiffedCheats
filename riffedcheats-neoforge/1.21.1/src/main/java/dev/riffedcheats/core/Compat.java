package dev.riffedcheats.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
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
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
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

    /** Runs a command in your own world and hands every line of its output to {@code out} (server thread), instead of the chat. */
    public static void runCapture(String cmd, Consumer<Component> out) {
        LocalPlayer p = mc().player;
        IntegratedServer srv = mc().getSingleplayerServer();
        if (p == null || srv == null) return;
        UUID id = p.getUUID();
        CommandSource sink = new CommandSource() {
            @Override public void sendSystemMessage(Component c) { out.accept(c); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        srv.execute(() -> {
            ServerPlayer sp = srv.getPlayerList().getPlayer(id);
            if (sp != null) srv.getCommands().performPrefixedCommand(elevated(sp).withSource(sink), cmd);
        });
    }

    // ---------------- menu screen / updates ----------------
    public static void openMenu() { mc().setScreen(new MenuScreen()); }
    public static void closeMenu() { if (mc().screen instanceof MenuScreen) mc().setScreen(null); }
    public static Path modsDir() { return FMLPaths.MODSDIR.get(); }
    /** Release jars are named riffedcheats-neoforge-1.21.1-<version>.jar */
    public static String assetPrefix() { return "riffedcheats-neoforge-1.21.1-"; }
    public static String buildName() { return "NeoForge 1.21.1"; }

    public static boolean modLoaded(String id) { return ModList.get() != null && ModList.get().isLoaded(id); }

    public static String selfName() { return mc().player == null ? "" : mc().player.getGameProfile().getName(); }

    // ---------------- other players' inventories ----------------
    /** 1.21.1 keeps armor (Slot 100-103) and offhand (Slot -106) inside the Inventory list. */
    public static String[] inventoryPaths() { return new String[] { "Inventory" }; }
    public static String[] inventoryQueries(String name) { return new String[] { "data get entity " + name + " Inventory" }; }

    public static void parseInventory(String snbt, Map<Integer, ItemStack> into) throws Exception {
        if (!snbt.startsWith("[")) return;
        ListTag list = TagParser.parseTag("{L:" + snbt + "}").getList("L", Tag.TAG_COMPOUND);
        into.clear();
        var reg = mc().level.registryAccess();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            int slot = t.getByte("Slot");
            if (slot == -106) slot = 106;
            int s = slot;
            ItemStack.parse(reg, t).ifPresent(st -> into.put(s, st));
        }
    }

    /** Enchantments on a stack (stored ones for enchanted books): id -> level. */
    public static Map<String, Integer> enchantsOf(ItemStack st) {
        ItemEnchantments e = st.get(st.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS);
        Map<String, Integer> out = new LinkedHashMap<>();
        if (e != null) for (var en : e.entrySet()) en.getKey().unwrapKey().ifPresent(k -> out.put(k.location().toString(), en.getIntValue()));
        return out;
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
            case "phantoms" -> "doInsomnia";
            case "patrols" -> "doPatrolSpawning";
            case "traders" -> "doTraderSpawning";
            case "immediate_respawn" -> "doImmediateRespawn";
            case "regen" -> "naturalRegeneration";
            case "fall_damage" -> "fallDamage";
            case "fire_damage" -> "fireDamage";
            case "mob_drops" -> "doMobLoot";
            case "block_drops" -> "doTileDrops";
            case "death_messages" -> "showDeathMessages";
            case "feedback" -> "sendCommandFeedback";
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

    // ---------------- teleports ----------------
    /** Key for saved teleports: the save folder name in singleplayer / LAN host, the address on a server. */
    public static String worldId() {
        IntegratedServer s = mc().getSingleplayerServer();
        if (s != null) {
            try { return "sp:" + s.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName(); }
            catch (Throwable t) { return "sp:" + s.getWorldData().getLevelName(); }
        }
        ServerData d = mc().getCurrentServer();
        return d != null ? "mp:" + d.ip.toLowerCase(java.util.Locale.ROOT) : "unknown";
    }

    public static String dimension() { return mc().level == null ? "minecraft:overworld" : mc().level.dimension().location().toString(); }

    /** The death position vanilla keeps for the recovery compass (sent to the client on join / respawn). */
    public static Teleports.Loc lastDeath() {
        LocalPlayer p = mc().player;
        if (p == null) return null;
        Optional<GlobalPos> o = p.getLastDeathLocation();
        if (o.isEmpty()) return null;
        BlockPos b = o.get().pos();
        return new Teleports.Loc(worldId(), o.get().dimension().location().toString(), "Last Death",
                b.getX() + 0.5, b.getY(), b.getZ() + 0.5, p.getYRot(), p.getXRot());
    }

    /** 1.21.1: CustomName is a JSON text component inside an NBT string. */
    public static String customName(String n) { return "CustomName:'\"" + n + "\"'"; }

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

    /** Modifier keys held right now (multi-select in the inventory editor). */
    public static boolean shift() { return Screen.hasShiftDown(); }
    public static boolean ctrl() { return Screen.hasControlDown(); }

    /** The item in your main hand (empty if none). */
    public static ItemStack heldItem() { return mc().player == null ? ItemStack.EMPTY : mc().player.getMainHandItem(); }
    /** The stack's data components as NBT (enchantments, name...), or null when it has none. */
    public static CompoundTag componentsTag(ItemStack st) {
        if (st.isEmpty()) return null;
        CompoundTag c = ((CompoundTag) st.save(mc().level.registryAccess())).getCompound("components");
        return c.isEmpty() ? null : c;
    }
    public static java.util.Set<String> keys(CompoundTag t) { return t.getAllKeys(); }
    /** Sets one enchantment level on the stack itself (stored enchantments on books); 0 removes it. */
    public static void setEnchant(ItemStack st, String id, int lv) {
        reg().getHolder(ResourceLocation.parse(id)).ifPresent(h ->
                net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(st, m -> m.set(h, lv)));
    }

    /** A client-only villager (or wandering trader) for the summoner's preview; null when there is no world. */
    public static LivingEntity villagerPreview(boolean trader, String profession, String type, int level) {
        if (mc().level == null) return null;
        if (trader) {
            var w = new net.minecraft.world.entity.npc.WanderingTrader(net.minecraft.world.entity.EntityType.WANDERING_TRADER, mc().level);
            if (mc().player != null) w.setPos(mc().player.getX(), mc().player.getY(), mc().player.getZ());
            return w;
        }
        var v = new net.minecraft.world.entity.npc.Villager(net.minecraft.world.entity.EntityType.VILLAGER, mc().level);
        v.setVillagerData(new net.minecraft.world.entity.npc.VillagerData(
                net.minecraft.core.registries.BuiltInRegistries.VILLAGER_TYPE.get(ResourceLocation.parse(type)),
                net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(profession)), level));
        if (mc().player != null) v.setPos(mc().player.getX(), mc().player.getY(), mc().player.getZ());
        return v;
    }

    /** A client-only entity of this type (for previews); null when there is no world or it cannot be made. */
    public static Entity createEntity(String id) {
        if (mc().level == null) return null;
        Entity e = net.minecraft.world.entity.EntityType.byString(id).map(t -> (Entity) t.create(mc().level)).orElse(null);
        if (e != null && mc().player != null) e.setPos(mc().player.getX(), mc().player.getY(), mc().player.getZ());
        return e;
    }
    /** Applies summon-style NBT ("{...}") to an entity, the same way /summon would. */
    public static void loadNbt(Entity e, String snbt) throws Exception {
        CompoundTag t = e.saveWithoutId(new CompoundTag());
        t.merge(TagParser.parseTag(snbt));
        e.load(t);
    }
    /** Sorted ids of a registry (e.g. "minecraft:wolf_variant"); empty if this version does not have it. */
    public static List<String> registryIds(String registry) {
        List<String> l = new ArrayList<>();
        if (mc().level == null) return l;
        mc().level.registryAccess().registry(net.minecraft.resources.ResourceKey.createRegistryKey(ResourceLocation.parse(registry)))
                .ifPresent(r -> { for (Object k : r.keySet()) l.add(k.toString()); });
        Collections.sort(l);
        return l;
    }
}
