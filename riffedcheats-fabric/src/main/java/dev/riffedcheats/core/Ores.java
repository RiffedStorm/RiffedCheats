package dev.riffedcheats.core;

import static dev.riffedcheats.core.Menu.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Ore ESP: 3D outlines around ores (and any blocks you pick), seen through walls.
 * The outlines are drawn on the HUD from the block positions, not in the world, so Sodium, Iris and shader packs
 * cannot hide or break them. Modded ores are found by the common "c:ores" tag and by "_ore" in their name.
 */
final class Ores {
    /** Matched in the block id and its ore tags, in this order. Same order as Menu.ORE_KINDS. */
    static final String[] KEYS = { "diamond", "emerald", "ancient_debris", "gold", "iron", "copper", "redstone", "lapis", "coal", "quartz" };
    static final int[] COLORS = { 0xFF4FF0E8, 0xFF3BE46B, 0xFFB0703F, 0xFFFFD43B, 0xFFE8C0A0, 0xFFF08A40, 0xFFFF3535, 0xFF4070FF, 0xFF8A8A8A, 0xFFF4F0E8 };
    private static final int[][] EDGES = { {0,1},{2,3},{4,5},{6,7},{0,2},{1,3},{4,6},{5,7},{0,4},{1,5},{2,6},{3,7} };

    private static final Map<BlockState, Integer> CACHE = new IdentityHashMap<>();
    private static final Map<Long, int[]> FOUND = new HashMap<>();     // chunk -> x, y, z, color, x, y, z, color...
    private static Set<String> custom = Set.of();
    private static ClientLevel level;
    private static String sig = "";
    private static long[] order = new long[0];
    private static int cursor, near, ocx = Integer.MIN_VALUE, ocz, orc, ticks;
    private static int[] shown = new int[0];

    private static long key(int cx, int cz) { return ((long) cx << 32) | (cz & 0xFFFFFFFFL); }

    static void clear() { FOUND.clear(); CACHE.clear(); shown = new int[0]; order = new long[0]; ocx = Integer.MIN_VALUE; level = null; }

    private static String signature() {
        StringBuilder b = new StringBuilder();
        for (Setting s : ORE_KINDS) b.append(s.on ? '1' : '0');
        b.append(ORE_OTHER.on ? '1' : '0').append(ORE_CUSTOM.on ? '1' : '0');
        if (ORE_CUSTOM.on) b.append(Menu.str("ore_custom"));
        return b.toString();
    }

    private static int color(BlockState s) {
        Integer c = CACHE.get(s);
        if (c == null) { c = classify(s); CACHE.put(s, c); }
        return c;
    }

    private static int mapColor(BlockState s) {
        int c = 0;
        try { c = s.getMapColor(level, BlockPos.ZERO).col; } catch (Throwable ignored) {}
        if (c == 0) return 0xFFCC8CFF;
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255, m = Math.max(r, Math.max(g, b));
        if (m < 140) { double f = 140.0 / Math.max(1, m); r = Math.min(255, (int) (r * f) + 30); g = Math.min(255, (int) (g * f) + 30); b = Math.min(255, (int) (b * f) + 30); }
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** By name alone, for the "All Ores" button of Custom Blocks. */
    static boolean looksLikeOre(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        return path.endsWith("_ore") || path.startsWith("ore_") || path.contains("_ore_") || path.equals("ancient_debris");
    }

    private static int classify(BlockState s) {
        if (s.isAir()) return 0;
        String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        if (custom.contains(id)) return mapColor(s);
        String path = id.substring(id.indexOf(':') + 1);
        boolean ore = looksLikeOre(id);
        StringBuilder words = new StringBuilder(path);
        try {
            for (String t : Compat.blockTags(s)) {
                if (t.equals("c:ores") || t.startsWith("c:ores/") || t.startsWith("forge:ores") || (t.startsWith("minecraft:") && t.endsWith("_ores"))) {
                    ore = true;
                    words.append(' ').append(t);
                }
            }
        } catch (Throwable ignored) {}
        if (!ore) return 0;
        String w = words.toString();
        for (int i = 0; i < KEYS.length; i++)
            if (w.contains(KEYS[i]) || (i == 2 && w.contains("netherite"))) return ORE_KINDS[i].on ? COLORS[i] : 0;
        return ORE_OTHER.on ? mapColor(s) : 0;
    }

    static void tick(LocalPlayer p, ClientLevel lv) {
        if (!ORE_ON.active()) { if (level != null) clear(); return; }
        if (lv != level) { clear(); level = lv; }
        String now = signature();
        if (!now.equals(sig)) {
            sig = now;
            custom = ORE_CUSTOM.on ? Targets.list("ore_custom") : Set.of();
            CACHE.clear(); FOUND.clear(); cursor = 0;
        }
        int range = ORE_RANGE.intValue(), rc = (range >> 4) + 1;
        int pcx = p.getBlockX() >> 4, pcz = p.getBlockZ() >> 4;
        if (pcx != ocx || pcz != ocz || rc != orc) {
            ocx = pcx; ocz = pcz; orc = rc;
            FOUND.keySet().removeIf(k -> Math.abs((int) (k >> 32) - pcx) > rc || Math.abs((int) (long) k - pcz) > rc);
            List<long[]> l = new ArrayList<>();
            for (int dx = -rc; dx <= rc; dx++) for (int dz = -rc; dz <= rc; dz++) l.add(new long[] { (long) dx * dx + (long) dz * dz, key(pcx + dx, pcz + dz) });
            l.sort((a, b) -> Long.compare(a[0], b[0]));
            order = new long[l.size()];
            for (int i = 0; i < order.length; i++) order[i] = l.get(i)[1];
            cursor = 0;
        }
        int y0 = p.getBlockY() - range, y1 = p.getBlockY() + range;
        // the 3x3 chunks around you are scanned again all the time, the rest in turn
        scan(order[near++ % Math.min(9, order.length)], y0, y1);
        for (int i = 0; i < 6 && order.length > 0; i++) { scan(order[cursor], y0, y1); cursor = (cursor + 1) % order.length; }
        if (++ticks % 4 == 0) pick(p, range);
    }

    private static void scan(long k, int yMin, int yMax) {
        int cx = (int) (k >> 32), cz = (int) k;
        if (!level.hasChunk(cx, cz)) { FOUND.remove(k); return; }
        LevelChunk ch = level.getChunk(cx, cz);
        LevelChunkSection[] secs = ch.getSections();
        int[] out = new int[64];
        int n = 0;
        for (int i = 0; i < secs.length; i++) {
            LevelChunkSection sec = secs[i];
            if (sec == null || sec.hasOnlyAir()) continue;
            int by = ch.getSectionYFromSectionIndex(i) << 4;
            if (by + 15 < yMin || by > yMax || !sec.maybeHas(s -> color(s) != 0)) continue;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int c = color(sec.getBlockState(x, y, z));
                if (c == 0) continue;
                if (n + 4 > out.length) out = Arrays.copyOf(out, out.length * 2);
                out[n++] = (cx << 4) + x; out[n++] = by + y; out[n++] = (cz << 4) + z; out[n++] = c;
            }
        }
        if (n == 0) FOUND.remove(k); else FOUND.put(k, Arrays.copyOf(out, n));
    }

    /** The nearest found blocks within range, at most Max Outlines of them. */
    private static void pick(LocalPlayer p, int range) {
        double px = p.getX(), py = p.getEyeY(), pz = p.getZ(), r2 = (double) range * range;
        List<double[]> l = new ArrayList<>();
        for (int[] a : FOUND.values())
            for (int i = 0; i < a.length; i += 4) {
                double dx = a[i] + 0.5 - px, dy = a[i + 1] + 0.5 - py, dz = a[i + 2] + 0.5 - pz, d = dx * dx + dy * dy + dz * dz;
                if (d <= r2) l.add(new double[] { d, a[i], a[i + 1], a[i + 2], a[i + 3] });
            }
        l.sort((a, b) -> Double.compare(a[0], b[0]));
        int max = Math.min(l.size(), ORE_MAX.intValue());
        int[] s = new int[max * 4];
        for (int i = 0; i < max; i++) { double[] e = l.get(i); s[i * 4] = (int) e[1]; s[i * 4 + 1] = (int) e[2]; s[i * 4 + 2] = (int) e[3]; s[i * 4 + 3] = (int) e[4]; }
        shown = s;
    }

    static int count() { return shown.length / 4; }

    static void render(Gfx g, Projector pj) {
        if (!ORE_ON.active() || Compat.hudHidden() || shown.length == 0) return;
        double k = pj.px, W = pj.w * k, H = pj.h * k;
        int t = ORE_THICK.intValue();
        int[] s = shown;
        g.push();
        g.scale((float) (1 / k));
        g.batch(() -> {
            double[][] v = new double[8][];
            for (int i = 0; i < s.length; i += 4) {
                int x = s[i], y = s[i + 1], z = s[i + 2], c = s[i + 3];
                for (int j = 0; j < 8; j++) v[j] = pj.view(x + (j & 1), y + (j >> 1 & 1), z + (j >> 2 & 1));
                for (int[] e : EDGES) edge(g, pj, v[e[0]], v[e[1]], k, W, H, t, c);
                if (ORE_TRACERS.on) {
                    double[] m = pj.view(x + 0.5, y + 0.5, z + 0.5);
                    if (m[2] < -Projector.NEAR) { double[] q = pj.screen(m); line(g, W / 2, H / 2, q[0] * k, q[1] * k, 1, c & 0x90FFFFFF, W, H); }
                }
            }
        });
        g.pop();
    }

    /** One edge in camera space, cut where it goes behind the camera. */
    private static void edge(Gfx g, Projector pj, double[] a, double[] b, double k, double W, double H, int t, int c) {
        double n = -Projector.NEAR;
        boolean ia = a[2] <= n, ib = b[2] <= n;
        if (!ia && !ib) return;
        if (!ia || !ib) {
            double f = (n - a[2]) / (b[2] - a[2]);
            double[] cut = { a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, n };
            if (ia) b = cut; else a = cut;
        }
        double[] p = pj.screen(a), q = pj.screen(b);
        line(g, p[0] * k, p[1] * k, q[0] * k, q[1] * k, t, c, W, H);
    }

    /** A straight line out of filled runs (one per pixel row or column), clipped to the screen first. */
    private static void line(Gfx g, double ax, double ay, double bx, double by, int t, int c, double W, double H) {
        double dx = bx - ax, dy = by - ay, t0 = 0, t1 = 1;
        double[] pp = { -dx, dx, -dy, dy }, qq = { ax + t, W + t - ax, ay + t, H + t - ay };
        for (int i = 0; i < 4; i++) {
            if (pp[i] == 0) { if (qq[i] < 0) return; continue; }
            double r = qq[i] / pp[i];
            if (pp[i] < 0) { if (r > t1) return; if (r > t0) t0 = r; }
            else { if (r < t0) return; if (r < t1) t1 = r; }
        }
        double sx = ax + dx * t0, sy = ay + dy * t0, ex = ax + dx * t1, ey = ay + dy * t1;
        dx = ex - sx; dy = ey - sy;
        double half = t / 2.0;
        if (Math.abs(dx) >= Math.abs(dy)) {
            int steps = Math.max(1, (int) Math.ceil(Math.abs(dy)));
            for (int i = 0; i < steps; i++) {
                double x0 = sx + dx * i / steps, x1 = sx + dx * (i + 1) / steps, y = sy + dy * (i + 0.5) / steps;
                int l = (int) Math.floor(Math.min(x0, x1)), r = (int) Math.ceil(Math.max(x0, x1)), yy = (int) Math.floor(y - half);
                g.fill(l, yy, Math.max(r, l + 1), yy + t, c);
            }
        } else {
            int steps = Math.max(1, (int) Math.ceil(Math.abs(dx)));
            for (int i = 0; i < steps; i++) {
                double y0 = sy + dy * i / steps, y1 = sy + dy * (i + 1) / steps, x = sx + dx * (i + 0.5) / steps;
                int u = (int) Math.floor(Math.min(y0, y1)), d = (int) Math.ceil(Math.max(y0, y1)), xx = (int) Math.floor(x - half);
                g.fill(xx, u, xx + t, Math.max(d, u + 1), c);
            }
        }
    }
}
