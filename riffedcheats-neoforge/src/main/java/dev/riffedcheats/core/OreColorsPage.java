package dev.riffedcheats.core;

import java.util.Locale;

/** Ore ESP > Colors: pick the color of every ore group with RGB sliders, a hex code or a preset. */
public final class OreColorsPage implements Page {
    private static final int LW = 150, ROWH = 16;
    private static final int[] PRESETS = {
        0xFFFFFF, 0xC0C0C0, 0x808080, 0x303030, 0xFF3535, 0xFF8A3A, 0xFFD43B, 0xB8FF3A,
        0x3BE46B, 0x3AFFC8, 0x4FF0E8, 0x3AA8FF, 0x4070FF, 0x9A5AFF, 0xFF5AE0, 0xFF5A8C };

    private final TextField hex = new TextField();
    private int sel, dragging = -1, x, y, w, h;

    /** Built while Menu is still starting, before saved text values exist, so nothing is read here. */
    public OreColorsPage() { hex.max = 7; }
    private boolean loaded;

    @Override public boolean wantsText() { return true; }

    private void load() { hex.text = String.format(Locale.ROOT, "%06X", Ores.groupColor(sel)); }
    private void set(int rgb, boolean save) {
        String v = String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
        if (save) Menu.setStr(Ores.colorKey(sel), v); else Menu.putStr(Ores.colorKey(sel), v);
        hex.text = v;
    }
    private Setting own() { return sel == Ores.OTHER ? Menu.ORE_OTHER_OWN : sel == Ores.CUSTOM ? Menu.ORE_CUSTOM_OWN : null; }

    private int rx() { return x + LW + 10; }
    private int rw() { return w - LW - 10; }
    private int trackX() { return rx() + 14; }
    private int trackW() { return rw() - 50; }
    private int sliderY(int ch) { return y + 50 + ch * 16; }
    private int presetY() { return y + 112; }
    private int presetW() { return (rw() - 7 * 3) / 8; }

    @Override
    public void render(Gfx g, int mx, int my, int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        hex.focused = true;
        if (!loaded) { loaded = true; load(); }
        for (int i = 0; i <= Ores.CUSTOM; i++) {
            int ry = y + i * ROWH;
            Ui.rr(g, x, ry, LW, ROWH - 1, i == sel ? Ui.SEL : Ui.in(mx, my, x, ry, LW, ROWH - 1) ? Ui.HOVER : Ui.BTN);
            g.fill(x + 3, ry + 3, x + 12, ry + 12, 0xFF000000 | Ores.groupColor(i));
            Ui.label(g, Ores.NAMES[i], x + 17, ry + 4, LW - 20, Ui.TEXT);
        }
        Ui.button(g, "Reset All Colors", x, y + (Ores.CUSTOM + 1) * ROWH + 6, LW, 16, Ui.in(mx, my, x, y + (Ores.CUSTOM + 1) * ROWH + 6, LW, 16), true);

        int rx = rx(), rw = rw(), c = Ores.groupColor(sel);
        Ui.rr(g, rx, y, 40, 40, 0xFF000000);
        g.fill(rx + 2, y + 2, rx + 38, y + 38, 0xFF000000 | c);
        g.text(Ores.NAMES[sel], rx + 48, y + 4, Ui.TEXT);
        g.text("#", rx + 48, y + 22, Ui.DIM);
        hex.draw(g, rx + 56, y + 18, 70, 16, "RRGGBB");
        Ui.button(g, "Default", rx + rw - 54, y + 18, 54, 16, Ui.in(mx, my, rx + rw - 54, y + 18, 54, 16), false);

        String[] ch = { "R", "G", "B" };
        int[] cols = { 0xFFFF5050, 0xFF50E070, 0xFF5080FF };
        for (int i = 0; i < 3; i++) {
            int v = c >> (16 - i * 8) & 255, sy = sliderY(i), tx = trackX(), tw = trackW(), fw = tw * v / 255;
            g.text(ch[i], rx, sy + 2, cols[i]);
            Ui.rr(g, tx, sy + 4, tw, 4, Ui.DARK);
            if (fw > 0) Ui.rr(g, tx, sy + 4, fw, 4, cols[i]);
            Ui.rr(g, tx + fw - 2, sy + 1, 4, 10, Ui.TEXT);
            g.text(Integer.toString(v), tx + tw + 8, sy + 2, Ui.DIM);
        }

        g.text("Presets", rx, presetY() - 11, Ui.DIM);
        int pw = presetW();
        for (int i = 0; i < PRESETS.length; i++) {
            int px = rx + (i % 8) * (pw + 3), py = presetY() + (i / 8) * 17;
            boolean hov = Ui.in(mx, my, px, py, pw, 14);
            Ui.rr(g, px, py, pw, 14, hov || PRESETS[i] == c ? Ui.TEXT : 0xFF000000);
            g.fill(px + 1, py + 1, px + pw - 1, py + 13, 0xFF000000 | PRESETS[i]);
        }

        Setting own = own();
        if (own != null) {
            int oy = presetY() + 40;
            Ui.rr(g, rx, oy, rw, 20, Ui.in(mx, my, rx, oy, rw, 20) ? Ui.HOVER : Ui.BTN);
            Ui.label(g, "Use Each Block's Own Color", rx + 8, oy + 6, rw - 50, Ui.TEXT);
            Ui.pill(g, rx + rw - 36, oy + 4, own.on);
            if (own.on) g.text("On: this color is only used when Own Color is off.", rx, oy + 26, Ui.DIM);
        }
        if (Menu.ORE_RAINBOW.on) g.text("Rainbow Colors (Look tab) is on and overrides these.", rx, y + h - 9, 0xFFFFC94A);
    }

    private void slide(int ch, int mx) {
        int v = (int) Math.round(Math.max(0, Math.min(1, (mx - trackX()) / (double) trackW())) * 255);
        int c = Ores.groupColor(sel), sh = 16 - ch * 8;
        set((c & ~(255 << sh)) | v << sh, false);
    }

    @Override
    public boolean click(int mx, int my, int btn) {
        for (int i = 0; i <= Ores.CUSTOM; i++)
            if (Ui.in(mx, my, x, y + i * ROWH, LW, ROWH - 1)) { sel = i; load(); return true; }
        if (Ui.in(mx, my, x, y + (Ores.CUSTOM + 1) * ROWH + 6, LW, 16)) {
            for (int i = 0; i <= Ores.CUSTOM; i++) Menu.putStr(Ores.colorKey(i), "");
            Menu.changed();
            load();
            return true;
        }
        int rx = rx(), rw = rw();
        if (Ui.in(mx, my, rx + rw - 54, y + 18, 54, 16)) { Menu.setStr(Ores.colorKey(sel), ""); load(); return true; }
        for (int i = 0; i < 3; i++)
            if (Ui.in(mx, my, trackX() - 4, sliderY(i), trackW() + 8, 12)) { dragging = i; slide(i, mx); return true; }
        int pw = presetW();
        for (int i = 0; i < PRESETS.length; i++)
            if (Ui.in(mx, my, rx + (i % 8) * (pw + 3), presetY() + (i / 8) * 17, pw, 14)) { set(PRESETS[i], true); return true; }
        Setting own = own();
        if (own != null && Ui.in(mx, my, rx, presetY() + 40, rw, 20)) { own.on = !own.on; Menu.changed(); return true; }
        return false;
    }

    @Override public boolean drag(int mx, int my) { if (dragging < 0) return false; slide(dragging, mx); return true; }
    @Override public void release() { if (dragging >= 0) { dragging = -1; Menu.changed(); } }

    private void typedHex() {
        String t = hex.text.replace("#", "");
        if (t.matches("[0-9a-fA-F]{6}")) Menu.setStr(Ores.colorKey(sel), t.toUpperCase(Locale.ROOT));
    }

    @Override public boolean typed(char c) {
        if (Character.digit(c, 16) < 0 && c != '#') return true;
        boolean r = hex.typed(c);
        typedHex();
        return r;
    }
    @Override public boolean key(int key, int scan, int mods) { boolean r = hex.key(key, mods); typedHex(); return r; }
}
