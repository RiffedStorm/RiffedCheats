package dev.riffedcheats;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

/** Shared state for the Xray mixin. Everything that is not an enabled ore-type becomes invisible. */
public final class Xray {
    public static volatile boolean enabled;
    private static volatile int mask;
    private static final ConcurrentHashMap<Block, Boolean> CACHE = new ConcurrentHashMap<>();

    public static void set(boolean on, int newMask) {
        mask = newMask;
        CACHE.clear();
        enabled = on;
    }

    public static boolean test(Block b) {
        return CACHE.computeIfAbsent(b, Xray::compute);
    }

    private static boolean compute(Block b) {
        String p = BuiltInRegistries.BLOCK.getKey(b).getPath();
        int bit = -1;
        if (p.endsWith("_ore")) {
            if (p.contains("coal")) bit = 0;
            else if (p.contains("iron")) bit = 1;
            else if (p.contains("copper")) bit = 2;
            else if (p.contains("gold")) bit = 3;
            else if (p.contains("redstone")) bit = 4;
            else if (p.contains("lapis")) bit = 5;
            else if (p.contains("diamond")) bit = 6;
            else if (p.contains("emerald")) bit = 7;
            else if (p.contains("quartz")) bit = 8;
            else bit = 11;
        } else if (p.equals("ancient_debris")) bit = 9;
        else if (p.equals("spawner") || p.contains("chest") || p.equals("barrel") || p.startsWith("end_portal")
                || p.equals("trial_spawner") || p.equals("vault")) bit = 10;
        return bit >= 0 && (mask & (1 << bit)) != 0;
    }
}
