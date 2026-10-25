package dev.riffedcheats.core;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

public final class Ui {
    public static final int BG = 0xF0101426, PANEL = 0xFF171B34, BTN = 0xFF26305A, SEL = 0xFF3B4C8C,
            HOVER = 0xFF2F3B70, ACCENT = 0xFF6C8CF0, TEXT = 0xFFE8EAF6, DIM = 0xFF8E94B8,
            GREEN = 0xFF4CAF7A, RED = 0xFFE05C5C, REDBTN = 0xFF5A2630, REDHOV = 0xFF7A3040, DARK = 0xFF10132A;

    private static final java.util.Set<String> BROKEN_PREVIEWS = new java.util.HashSet<>();

    /**
     * Draws a 3D preview; if this kind of entity fails (some renderers or mods do not like client-only entities) it is
     * remembered and skipped from then on, the error is printed once to the game log, and false is returned so the caller can show an icon.
     */
    public static boolean entity(Gfx g, net.minecraft.world.entity.Entity e, int x0, int y0, int x1, int y1, float size, int mx, int my) {
        if (!(e instanceof net.minecraft.world.entity.LivingEntity le)) return false;
        String kind = e.getClass().getName();
        if (BROKEN_PREVIEWS.contains(kind)) return false;
        try {
            g.entity(le, x0, y0, x1, y1, size, mx, my);
            return true;
        } catch (Throwable t) {
            BROKEN_PREVIEWS.add(kind);
            System.err.println("[RiffedCheats] 3D preview failed for " + kind + ", showing an icon instead:");
            t.printStackTrace();
            return false;
        }
    }

    public static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Rounded-ish rectangle (corners cut by 1px). */
    public static void rr(Gfx g, int x, int y, int w, int h, int c) {
        if (w < 3 || h < 3) { g.fill(x, y, x + w, y + h, c); return; }
        g.fill(x + 1, y, x + w - 1, y + h, c);
        g.fill(x, y + 1, x + w, y + h - 1, c);
    }

    public static String fit(Gfx g, String s, int maxW) {
        if (g.width(s) <= maxW) return s;
        while (s.length() > 1 && g.width(s + "..") > maxW) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    /** Text that shrinks (down to 70%) to fit maxW instead of being cut; only very long text still gets "..". */
    public static void label(Gfx g, String s, int x, int y, int maxW, int color) {
        int tw = g.width(s);
        if (tw <= maxW) { g.text(s, x, y, color); return; }
        float sc = Math.max(0.7f, (float) maxW / tw);
        g.push();
        g.translate(x, y + (1 - sc) * 4);   // keep it vertically centered on the normal line
        g.scale(sc);
        g.text(fit(g, s, (int) (maxW / sc)), 0, 0, color);
        g.pop();
    }

    public static void center(Gfx g, String s, int x, int y, int w, int color) {
        g.text(s, x + (w - g.width(s)) / 2, y, color);
    }

    public static void button(Gfx g, String label, int x, int y, int w, int h, boolean hover, boolean danger) {
        rr(g, x, y, w, h, danger ? (hover ? REDHOV : REDBTN) : (hover ? HOVER : BTN));
        center(g, fit(g, label, w - 6), x, y + (h - 8) / 2, w, TEXT);
    }

    /** Button that stays highlighted while selected (e.g. the count preset that is currently active). */
    public static void selButton(Gfx g, String label, int x, int y, int w, int h, boolean hover, boolean selected) {
        rr(g, x, y, w, h, selected ? SEL : hover ? HOVER : BTN);
        center(g, fit(g, label, w - 6), x, y + (h - 8) / 2, w, TEXT);
    }

    /** Button colored by state: green = on, red = off, normal = unknown. */
    public static void stateButton(Gfx g, String label, int x, int y, int w, int h, boolean hover, Boolean on) {
        int c = on == null ? (hover ? HOVER : BTN) : on ? (hover ? 0xFF3A9A63 : 0xFF2E7D4F) : (hover ? 0xFFA03A48 : 0xFF86303C);
        rr(g, x, y, w, h, c);
        center(g, fit(g, label, w - 6), x, y + (h - 8) / 2, w, TEXT);
    }

    public static void pill(Gfx g, int x, int y, boolean on) {
        rr(g, x, y, 28, 12, on ? GREEN : DARK);
        rr(g, on ? x + 16 : x + 2, y + 2, 10, 8, TEXT);
    }

    public static List<String> wrap(Gfx g, String text, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String t = line.length() == 0 ? word : line + " " + word;
            if (g.width(t) > maxW && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(word); }
            else { line = new StringBuilder(t); }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    /** [<] icon + name [>]: click the middle or > for the next value, < (or right click) for the previous one. */
    public static void cycler(Gfx g, int mx, int my, int cx, int cy, int cw, String label, ItemStack icon) {
        button(g, "<", cx, cy, 14, 16, in(mx, my, cx, cy, 14, 16), false);
        rr(g, cx + 16, cy, cw - 32, 16, in(mx, my, cx + 16, cy, cw - 32, 16) ? HOVER : BTN);
        int tx = cx + 20;
        if (icon != null) { g.itemScaled(icon, cx + 18, cy + 2, 0.75f); tx = cx + 32; }
        label(g, label, tx, cy + 4, cx + cw - 18 - tx, TEXT);
        button(g, ">", cx + cw - 14, cy, 14, 16, in(mx, my, cx + cw - 14, cy, 14, 16), false);
    }

    /** -1 = previous, 1 = next, 0 = not clicked. */
    public static int cycled(int mx, int my, int btn, int cx, int cy, int cw) {
        if (in(mx, my, cx, cy, 14, 16)) return -1;
        if (in(mx, my, cx + cw - 14, cy, 14, 16)) return 1;
        if (in(mx, my, cx + 16, cy, cw - 32, 16)) return btn == 1 ? -1 : 1;
        return 0;
    }
}
