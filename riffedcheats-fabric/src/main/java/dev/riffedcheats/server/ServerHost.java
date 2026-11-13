package dev.riffedcheats.server;

import dev.riffedcheats.Noclip;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * The part of RiffedCheats that runs on a server (dedicated, or the world you host / Open to LAN). Everyone can join
 * without the mod. Only operators who have the mod can use it, and every message is checked against their current
 * permission. Commands run silently here, so nothing reaches anyone's chat. Must never touch client-only classes.
 */
public final class ServerHost {
    public static final int PROTO = 2;
    public static final String SEP = "\u0001";
    /** Item custom data: the count an infinite stack is kept at. */
    public static final String INF_KEY = "riffedcheats_infinite";
    private static final String INF_RAISED = "riffedcheats_infinite_raised";   // the mod raised its stack limit to 2

    public static BiConsumer<ServerPlayer, String> sender = (p, s) -> {};

    private static final class State {
        boolean god, food, heal, nofall, fly, repair, block, nocd, noclip;
        double magnet;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<UUID, Long> RESYNC = new HashMap<>();
    private static long ticks;

    private static MinecraftServer server(ServerPlayer p) { return p.level().getServer(); }

    private static void send(ServerPlayer p, String... parts) {
        String s = String.join(SEP, parts);
        if (s.length() < 4_000_000) sender.accept(p, s);
    }

    /** A message from a client (server thread). */
    public static void receive(ServerPlayer p, String data) {
        String[] a = data.split(SEP, -1);
        boolean ok = ServerCompat.allowed(p);
        if (a[0].equals("hello")) { send(p, "hello", String.valueOf(PROTO), ok ? "1" : "0", ServerCompat.version()); return; }
        if (!ok || a.length < 2) return;
        switch (a[0]) {
            case "run" -> run(p, a[1]);
            case "q" -> capture(p, a[1], false);
            case "c" -> capture(p, a[1], true);
            case "state" -> state(p, a[1]);
            case "inf" -> { if (a.length >= 4) infinite(p, a[1], a[2], a[3].equals("1")); }
            default -> {}
        }
    }

    private static String clean(String c) { return c.startsWith("/") ? c.substring(1) : c; }

    private static void run(ServerPlayer p, String cmd) {
        server(p).getCommands().performPrefixedCommand(ServerCompat.source(p).withSuppressedOutput(), clean(cmd));
    }

    /** Runs a command and sends its output to this player only, never into anyone's chat. */
    private static void capture(ServerPlayer p, String cmd, boolean console) {
        CommandSource sink = new CommandSource() {
            @Override public void sendSystemMessage(Component c) { output(p, c, console); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        server(p).getCommands().performPrefixedCommand(ServerCompat.source(p).withSource(sink), clean(cmd));
    }

    private static void output(ServerPlayer p, Component c, boolean console) {
        java.util.List<String> parts = new java.util.ArrayList<>(java.util.List.of("out", console ? "1" : "0", c.getString()));
        if (c.getContents() instanceof TranslatableContents t) {
            parts.add(t.getKey());
            for (Object o : t.getArgs()) parts.add(o instanceof Component k ? k.getString() : String.valueOf(o));
        } else parts.add("");
        send(p, parts.toArray(new String[0]));
    }

    private static void state(ServerPlayer p, String s) {
        UUID id = p.getUUID();
        State old = STATES.get(id);
        if (s.isEmpty()) { if (old != null) stop(p, old); STATES.remove(id); return; }
        State n = new State();
        for (String kv : s.split(";")) {
            String[] f = kv.split("=", 2);
            if (f.length < 2) continue;
            boolean on = f[1].equals("1");
            switch (f[0]) {
                case "god" -> n.god = on;
                case "food" -> n.food = on;
                case "heal" -> n.heal = on;
                case "nofall" -> n.nofall = on;
                case "fly" -> n.fly = on;
                case "repair" -> n.repair = on;
                case "block" -> n.block = on;
                case "nocd" -> n.nocd = on;
                case "noclip" -> n.noclip = on;
                case "magnet" -> { try { n.magnet = Math.max(0, Math.min(64, Double.parseDouble(f[1]))); } catch (NumberFormatException ignored) {} }
                default -> {}
            }
        }
        if (old != null && old.fly && !n.fly) stopFlying(p);
        if (n.noclip) Noclip.SERVER.add(id); else Noclip.SERVER.remove(id);
        STATES.put(id, n);
    }

    private static void stopFlying(ServerPlayer p) {
        boolean keep = p.isCreative() || p.isSpectator();
        if (p.getAbilities().mayfly == keep) return;
        p.getAbilities().mayfly = keep;
        if (!keep) p.getAbilities().flying = false;
        p.onUpdateAbilities();
    }

    private static void stop(ServerPlayer p, State s) {
        Noclip.SERVER.remove(p.getUUID());
        if (s.fly) stopFlying(p);
    }

    /** Leaving the server: flight is saved with the player, so it is turned off before that happens. */
    public static void left(ServerPlayer p) {
        State s = STATES.remove(p.getUUID());
        if (s != null) stop(p, s);
    }

    /** True when this damage must not hurt: God Mode. /kill and the void still work. */
    public static boolean blocked(LivingEntity e, DamageSource src) {
        if (!(e instanceof ServerPlayer p)) return false;
        State s = STATES.get(p.getUUID());
        return s != null && s.god && !src.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /** Every server tick. */
    public static void tick(MinecraftServer srv) {
        ticks++;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            keepInfinite(p);
            State s = STATES.get(p.getUUID());
            if (s == null) continue;
            if (!ServerCompat.allowed(p)) {     // lost operator: everything stops at once
                stop(p, s);
                STATES.remove(p.getUUID());
                send(p, "hello", String.valueOf(PROTO), "0", ServerCompat.version());
                continue;
            }
            apply(p, s);
        }
        STATES.keySet().removeIf(id -> srv.getPlayerList().getPlayer(id) == null);
        RESYNC.keySet().removeIf(id -> srv.getPlayerList().getPlayer(id) == null);
        Noclip.SERVER.removeIf(id -> srv.getPlayerList().getPlayer(id) == null);
    }

    private static void apply(ServerPlayer p, State s) {
        if (s.food) { p.getFoodData().setFoodLevel(20); p.getFoodData().setSaturation(20f); }
        if (s.heal && !p.isDeadOrDying() && p.getHealth() < p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        if (s.nofall || s.noclip) p.resetFallDistance();
        if (s.fly && !p.getAbilities().mayfly) { p.getAbilities().mayfly = true; p.onUpdateAbilities(); }
        if (s.block) ServerCompat.instantBlock(p);
        if (s.nocd) ServerCompat.clearShieldCooldown(p);
        if (s.repair && ticks % 20 == 0) {
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (!st.isEmpty() && st.isDamaged()) st.setDamageValue(0);
            }
        }
        if (s.magnet > 0) magnet(p, s.magnet);
    }

    private static void magnet(ServerPlayer p, double r) {
        for (ItemEntity e : p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(r), x -> x.getAge() > 30)) {
            e.setNoPickUpDelay();
            e.setPos(p.getX(), p.getY(), p.getZ());
        }
        for (ExperienceOrb e : p.level().getEntitiesOfClass(ExperienceOrb.class, p.getBoundingBox().inflate(r), x -> true))
            e.setPos(p.getX(), p.getY(), p.getZ());
    }

    // ------------------------------------------------------------------ infinite items
    /** Slot numbers used by the inventory editor -> index in the player's inventory. */
    private static int index(int slot) { return slot == 106 ? 40 : slot >= 100 ? slot - 64 : slot; }

    /** Marks a stack: used-up items come back on the next tick. A count of at least 2 means using one never empties it. */
    private static void infinite(ServerPlayer op, String target, String slot, boolean on) {
        ServerPlayer t = ServerCompat.byName(server(op), target);
        if (t == null) return;
        int i;
        try { i = index(Integer.parseInt(slot)); } catch (NumberFormatException e) { return; }
        if (i < 0 || i >= t.getInventory().getContainerSize()) return;
        ItemStack st = t.getInventory().getItem(i);
        if (st.isEmpty()) return;
        if (on) {
            if (st.isDamageableItem()) return;      // a damageable item cannot stack: the menu makes it unbreakable instead
            boolean raise = st.getMaxStackSize() < 2;
            if (raise) st.set(DataComponents.MAX_STACK_SIZE, 2);
            int n = Math.max(2, Math.min(st.getCount(), st.getMaxStackSize()));
            CustomData.update(DataComponents.CUSTOM_DATA, st, tag -> { tag.putInt(INF_KEY, n); if (raise) tag.putBoolean(INF_RAISED, true); });
            st.setCount(n);
        } else {
            CustomData d = st.get(DataComponents.CUSTOM_DATA);
            boolean raised = d != null && d.copyTag().contains(INF_RAISED);
            CustomData.update(DataComponents.CUSTOM_DATA, st, tag -> { tag.remove(INF_KEY); tag.remove(INF_RAISED); });
            if (raised) { st.remove(DataComponents.MAX_STACK_SIZE); st.setCount(Math.min(st.getCount(), st.getMaxStackSize())); }
        }
        t.getInventory().setChanged();
    }

    /**
     * The client takes the used item off its own count, and the server would not tell it about the refill because by its
     * own bookkeeping the count never changed. So after a refill the inventory is sent again, and once more a moment
     * later in case the client finished using the item after the first copy arrived.
     */
    private static void keepInfinite(ServerPlayer p) {
        boolean refilled = false;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            CustomData d = st.get(DataComponents.CUSTOM_DATA);
            if (d == null) continue;
            CompoundTag tag = d.copyTag();
            if (!tag.contains(INF_KEY)) continue;
            int n = ServerCompat.getInt(tag, INF_KEY);
            if (n > 0 && st.getCount() != n) { st.setCount(n); refilled = true; }
        }
        UUID id = p.getUUID();
        if (refilled) {
            p.containerMenu.sendAllDataToRemote();
            RESYNC.put(id, ticks + 8);
        } else if (RESYNC.containsKey(id) && ticks >= RESYNC.get(id)) {
            RESYNC.remove(id);
            p.containerMenu.sendAllDataToRemote();
        }
    }
}
