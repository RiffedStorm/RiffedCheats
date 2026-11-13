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
    /** Default colors (RGB) of KEYS, then "other" and "custom". */
    static final int[] COLORS = { 0x4FF0E8, 0x3BE46B, 0xB0703F, 0xFFD43B, 0xE8C0A0, 0xF08A40, 0xFF3535, 0x4070FF, 0x8A8A8A, 0xF4F0E8, 0xCC8CFF, 0xFFFFFF };
    static final String[] NAMES = { "Diamond", "Emerald", "Ancient Debris", "Gold", "Iron", "Copper", "Redstone", "Lapis Lazuli", "Coal", "Nether Quartz", "Modded / Other Ores", "Custom Blocks" };
    static final int OTHER = 10, CUSTOM = 11;

    static String colorKey(int k) { return "ore_col." + (k < KEYS.length ? KEYS[k] : k == OTHER ? "other" : "custom"); }

    /** The color you picked for group k (RGB). */
    static int groupColor(int k) {
        String s = Menu.str(colorKey(k));
        if (s.length() == 6) try { return Integer.parseInt(s, 16); } catch (NumberFormatException ignored) {}
        return COLORS[k];
    }
    private static final int[][] EDGES = { {0,1},{2,3},{4,5},{6,7},{0,2},{1,3},{4,6},{5,7},{0,4},{1,5},{2,6},{3,7} };

    private static final Map<BlockState, Integer> CACHE = new IdentityHashMap<>();
    private static final Map<Long, int[]> FOUND = new HashMap<>();     // chunk -> x, y, z, code, x, y, z, code...
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

    /** 0 = not shown, otherwise (group + 1) << 24 | the block's own map color. */
    private static int code(BlockState s) {
        Integer c = CACHE.get(s);
        if (c == null) { c = classify(s); CACHE.put(s, c); }
        return c;
    }

    private static int code(int group, BlockState s) {
        int c = 0;
        try { c = s.getMapColor(level, BlockPos.ZERO).col; } catch (Throwable ignored) {}
        if (c == 0) c = COLORS[OTHER];
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255, m = Math.max(r, Math.max(g, b));
        if (m < 140) { double f = 140.0 / Math.max(1, m); r = Math.min(255, (int) (r * f) + 30); g = Math.min(255, (int) (g * f) + 30); b = Math.min(255, (int) (b * f) + 30); }
        return (group + 1) << 24 | r << 16 | g << 8 | b;
    }

    /** By name alone, for the "All Ores" button of Custom Blocks. */
    static boolean looksLikeOre(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        return path.endsWith("_ore") || path.startsWith("ore_") || path.contains("_ore_") || path.equals("ancient_debris");
    }

    private static int classify(BlockState s) {
        if (s.isAir()) return 0;
        String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        if (custom.contains(id)) return code(CUSTOM, s);
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
            if (w.contains(KEYS[i]) || (i == 2 && w.contains("netherite"))) return ORE_KINDS[i].on ? code(i, s) : 0;
        return ORE_OTHER.on ? code(OTHER, s) : 0;
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
            if (by + 15 < yMin || by > yMax || !sec.maybeHas(s -> code(s) != 0)) continue;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int c = code(sec.getBlockState(x, y, z));
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

    /** Final ARGB of a found block, with the look options applied. */
    private static int argb(int code, int[] groups, int x, int y, int z, double dist, int alpha) {
        int k = (code >>> 24) - 1, rgb;
        if (ORE_RAINBOW.on) {
            float hue = (float) ((System.currentTimeMillis() % 4000) / 4000.0 + (x + y + z) * 0.02);
            rgb = hue(hue - (float) Math.floor(hue));
        } else if ((k == OTHER && ORE_OTHER_OWN.on) || (k == CUSTOM && ORE_CUSTOM_OWN.on)) rgb = code & 0xFFFFFF;
        else rgb = groups[Math.max(0, Math.min(CUSTOM, k))];
        if (ORE_FADE.on) alpha = (int) (alpha * Math.max(0.2, 1 - dist / Math.max(8, ORE_RANGE.value) * 0.8));
        return Math.max(0, Math.min(255, alpha)) << 24 | rgb;
    }

    static void render(Gfx g, Projector pj) {
        if (!ORE_ON.active() || Compat.hudHidden() || shown.length == 0) return;
        double k = pj.px, W = pj.w * k, H = pj.h * k;
        int t = ORE_THICK.intValue(), lineA = Math.round(ORE_OPACITY.value * 2.55f), fillA = Math.round(ORE_FILL_OPACITY.value * 2.55f);
        int[] s = shown, groups = new int[CUSTOM + 1];
        for (int i = 0; i <= CUSTOM; i++) groups[i] = groupColor(i);
        double[] cam = Compat.camera();
        g.push();
        g.scale((float) (1 / k));
        g.batch(() -> {
            double[][] v = new double[8][];
            double[] sx = new double[8], sy = new double[8];
            for (int i = 0; i < s.length; i += 4) {
                int x = s[i], y = s[i + 1], z = s[i + 2];
                double dist = Math.sqrt(sq(x + 0.5 - cam[0]) + sq(y + 0.5 - cam[1]) + sq(z + 0.5 - cam[2]));
                boolean front = true;
                for (int j = 0; j < 8; j++) {
                    v[j] = pj.view(x + (j & 1), y + (j >> 1 & 1), z + (j >> 2 & 1));
                    if (v[j][2] > -Projector.NEAR) front = false;
                }
                if (ORE_FILL.on && front) {
                    for (int j = 0; j < 8; j++) { double[] q = pj.screen(v[j]); sx[j] = q[0] * k; sy[j] = q[1] * k; }
                    fillHull(g, sx, sy, argb(s[i + 3], groups, x, y, z, dist, fillA), W, H);
                }
                int c = argb(s[i + 3], groups, x, y, z, dist, lineA);
                if (ORE_OUTLINE.on) for (int[] e : EDGES) edge(g, pj, v[e[0]], v[e[1]], k, W, H, t, c);
                if (ORE_TRACERS.on) {
                    double[] m = pj.view(x + 0.5, y + 0.5, z + 0.5);
                    if (m[2] < -Projector.NEAR) {
                        double[] q = pj.screen(m);
                        line(g, W / 2, ORE_TRACER_FROM.intValue() == 0 ? H / 2 : H, q[0] * k, q[1] * k, 1, (c & 0xFFFFFF) | (Math.min(255, (c >>> 24) * 3 / 4) << 24), W, H);
                    }
                }
            }
        });
        g.pop();
        if (ORE_NAMES.on || ORE_DIST.on) labels(g, pj, s, cam);
    }

    private static double sq(double d) { return d * d; }

    /** A bright color at hue h (0..1). */
    private static int hue(float h) {
        float f = h * 6;
        int i = (int) f % 6;
        int up = (int) ((f - (int) f) * 255), down = 255 - up, lo = 64;
        up = Math.max(lo, up); down = Math.max(lo, down);
        return switch (i) {
            case 0 -> 255 << 16 | up << 8 | lo;
            case 1 -> down << 16 | 255 << 8 | lo;
            case 2 -> lo << 16 | 255 << 8 | up;
            case 3 -> lo << 16 | down << 8 | 255;
            case 4 -> up << 16 | lo << 8 | 255;
            default -> 255 << 16 | lo << 8 | down;
        };
    }

    /** Name and / or distance over the nearest blocks (at most 40, so the screen does not fill with text). */
    private static void labels(Gfx g, Projector pj, int[] s, double[] cam) {
        for (int i = 0, n = 0; i < s.length && n < 40; i += 4) {
            int x = s[i], y = s[i + 1], z = s[i + 2];
            double[] q = pj.project(x + 0.5, y + 1.05, z + 0.5);
            if (q == null || q[0] < 0 || q[0] > pj.w || q[1] < 0 || q[1] > pj.h) continue;
            n++;
            String txt = "";
            if (ORE_NAMES.on) try { txt = level.getBlockState(new BlockPos(x, y, z)).getBlock().getName().getString(); } catch (Throwable ignored) {}
            if (ORE_DIST.on) txt += (txt.isEmpty() ? "" : " ") + Math.round(Math.sqrt(sq(x + 0.5 - cam[0]) + sq(y + 0.5 - cam[1]) + sq(z + 0.5 - cam[2]))) + "m";
            if (txt.isEmpty()) continue;
            float sc = 0.75f;
            int cx = (int) q[0], ty = (int) q[1] - 8, hw = (int) (g.width(txt) * sc / 2);
            g.fill(cx - hw - 1, ty - 1, cx + hw + 2, ty + 7, 0x70000000);
            g.push();
            g.translate(cx - g.width(txt) * sc / 2f, ty);
            g.scale(sc);
            g.text(txt, 0, 0, 0xFFFFFFFF);
            g.pop();
        }
    }

    /** Fills the outline of the whole block as seen on screen (the convex hull of its 8 corners), row by row. */
    private static void fillHull(Gfx g, double[] px, double[] py, int c, double W, double H) {
        Integer[] idx = { 0, 1, 2, 3, 4, 5, 6, 7 };
        java.util.Arrays.sort(idx, (a, b) -> px[a] != px[b] ? Double.compare(px[a], px[b]) : Double.compare(py[a], py[b]));
        int[] hull = new int[16];
        int n = 0;
        for (int pass = 0; pass < 2; pass++) {
            int start = n;
            for (int ii = 0; ii < 8; ii++) {
                int p = idx[pass == 0 ? ii : 7 - ii];
                while (n >= start + 2 && cross(px, py, hull[n - 2], hull[n - 1], p) <= 0) n--;
                hull[n++] = p;
            }
            n--;
        }
        if (n < 3) return;
        double top = Double.MAX_VALUE, bottom = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) { top = Math.min(top, py[hull[i]]); bottom = Math.max(bottom, py[hull[i]]); }
        int y0 = (int) Math.max(0, Math.floor(top)), y1 = (int) Math.min(H, Math.ceil(bottom));
        if (y1 - y0 > 4000) return;
        for (int y = y0; y < y1; y++) {
            double yc = y + 0.5, l = Double.MAX_VALUE, r = -Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                int a = hull[i], b = hull[(i + 1) % n];
                double ya = py[a], yb = py[b];
                if ((yc < ya) == (yc < yb)) continue;
                double xx = px[a] + (yc - ya) / (yb - ya) * (px[b] - px[a]);
                l = Math.min(l, xx); r = Math.max(r, xx);
            }
            if (l > r) continue;
            int xl = (int) Math.max(0, Math.round(l)), xr = (int) Math.min(W, Math.round(r));
            if (xr > xl) g.fill(xl, y, xr, y + 1, c);
        }
    }

    private static double cross(double[] px, double[] py, int o, int a, int b) {
        return (px[a] - px[o]) * (py[b] - py[o]) - (py[a] - py[o]) * (px[b] - px[o]);
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
