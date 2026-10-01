package dev.riffedcheats;

import java.util.ArrayList;
import java.util.List;

public final class Ui {
    public static final int BG = 0xF0101426, PANEL = 0xFF171B34, BTN = 0xFF26305A, SEL = 0xFF3B4C8C,
            HOVER = 0xFF2F3B70, ACCENT = 0xFF6C8CF0, TEXT = 0xFFE8EAF6, DIM = 0xFF8E94B8,
            GREEN = 0xFF4CAF7A, RED = 0xFFE05C5C, REDBTN = 0xFF5A2630, REDHOV = 0xFF7A3040, DARK = 0xFF10132A;

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

    public static void center(Gfx g, String s, int x, int y, int w, int color) {
        g.text(s, x + (w - g.width(s)) / 2, y, color);
    }

    public static void button(Gfx g, String label, int x, int y, int w, int h, boolean hover, boolean danger) {
        rr(g, x, y, w, h, danger ? (hover ? REDHOV : REDBTN) : (hover ? HOVER : BTN));
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
}
