package dev.riffedcheats.core;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.ItemStack;

/**
 * Inventories of other players. The client does not know them, so the menu asks the server with
 * "/data get entity <name> Inventory" and parses the item list out of the command feedback.
 * Slots: 0-35 main (0-8 hotbar), 100 feet, 101 legs, 102 chest, 103 head, 106 offhand.
 */
public final class Inventories {
    private static final Map<String, Map<Integer, ItemStack>> DATA = new HashMap<>();
    private static final Map<String, Long> LOADED = new HashMap<>();
    private static String pending;
    private static long pendingAt;

    public static void request(String name) {
        pending = name;
        pendingAt = System.currentTimeMillis();
        for (String q : Compat.inventoryQueries(name)) Cmd.query(q);
    }

    private static boolean waiting(String who) {
        return pending != null && System.currentTimeMillis() - pendingAt < 15000 && who.contains(pending); // display names may carry a team prefix
    }

    /** Called with the arguments of "commands.data.entity.query" feedback. */
    static void onData(String who, String snbt) {
        if (!waiting(who)) return;
        try {
            Compat.parseInventory(snbt, DATA.computeIfAbsent(pending, k -> new HashMap<>()));
            LOADED.put(pending, System.currentTimeMillis());
        } catch (Throwable ignored) {}
    }

    /** "Found no elements matching equipment": nothing worn (26.x keeps armor outside the Inventory list). */
    static void noEquipment() {
        if (pending == null || System.currentTimeMillis() - pendingAt > 15000) return;
        Map<Integer, ItemStack> m = DATA.get(pending);
        if (m != null) m.keySet().removeIf(k -> k >= 100);
    }

    public static ItemStack get(String name, int slot) {
        Map<Integer, ItemStack> m = DATA.get(name);
        return m == null ? ItemStack.EMPTY : m.getOrDefault(slot, ItemStack.EMPTY);
    }

    public static boolean loaded(String name) { return LOADED.containsKey(name); }

    public static void clear() { DATA.clear(); LOADED.clear(); pending = null; }
}
