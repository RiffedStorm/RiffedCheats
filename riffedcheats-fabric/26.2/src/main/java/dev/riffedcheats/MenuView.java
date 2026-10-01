package dev.riffedcheats;

import java.util.List;

/**
 * The whole menu UI in a fixed "design space" of DW x DH units.
 * The platform Screen scales that space so the menu takes the same share of the window on every
 * resolution and GUI scale, and converts the mouse into design coordinates.
 */
public final class MenuView {
    public static final int DW = 520, DH = 355;
    private static final int SX = 6, SY = 26, SW = 108;
    private static final int CX = 120, CY = 26, CW = DW - 126, CH = DH - 32;
    private static final int BX = CX + 6, BY = CY + 32, BW = CW - 12, BH = CH - 38;
    private static final int ROW = 20, STEP = 22;

    private static int cat, tab, scroll;
    private Setting dragging;

    private static Menu.Tab cur() {
        Menu.Category c = Menu.CATEGORIES.get(cat);
        tab = Math.min(tab, c.tabs().size() - 1);
        return c.tabs().get(tab);
    }

    public boolean capturesText() { return Access.allowed && cur().page() != null && cur().page().wantsText(); }

    // ------------------------------------------------------------------ render
    public void render(Gfx g, int mx, int my) {
        Ui.rr(g, 0, 0, DW, DH, Ui.BG);
        g.text("RiffedCheats", 6, 6, Ui.TEXT);
        String badge = Access.label();
        g.text(badge, DW - 6 - g.width(badge), 6, Access.allowed ? Ui.GREEN : Ui.RED);

        Ui.button(g, "Close", SX, SY, SW, 20, Ui.in(mx, my, SX, SY, SW, 20), false);

        if (!Access.allowed) { drawDenied(g); return; }

        // sidebar
        Ui.rr(g, SX, SY + 26, SW, DH - SY - 32, Ui.PANEL);
        for (int i = 0; i < Menu.CATEGORIES.size(); i++) {
            int ry = SY + 32 + i * 18;
            boolean sel = i == cat;
            if (sel) Ui.rr(g, SX + 3, ry - 2, SW - 6, 16, Ui.SEL);
            else if (Ui.in(mx, my, SX + 3, ry - 2, SW - 6, 16)) Ui.rr(g, SX + 3, ry - 2, SW - 6, 16, Ui.HOVER);
            g.text(Menu.CATEGORIES.get(i).name(), SX + 10, ry + 2, sel ? Ui.TEXT : Ui.DIM);
        }

        // content
        Ui.rr(g, CX, CY, CW, CH, Ui.PANEL);
        Menu.Category c = Menu.CATEGORIES.get(cat);
        int tx = CX + 6;
        for (int j = 0; j < c.tabs().size(); j++) {
            String n = c.tabs().get(j).name();
            int w = g.width(n) + 16;
            Ui.rr(g, tx, CY + 6, w, 20, j == tab ? Ui.SEL : Ui.in(mx, my, tx, CY + 6, w, 20) ? Ui.HOVER : Ui.BTN);
            g.text(n, tx + 8, CY + 12, Ui.TEXT);
            tx += w + 4;
        }

        Menu.Tab t = cur();
        if (t.page() != null) { t.page().render(g, mx, my, BX, BY, BW, BH); return; }

        List<Setting> list = t.settings();
        int rows = BH / STEP;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows)));
        for (int i = 0; i < rows && scroll + i < list.size(); i++) drawRow(g, list.get(scroll + i), BY + i * STEP, mx, my);
        if (list.size() > rows) {
            int trackH = rows * STEP - 2, th = Math.max(16, trackH * rows / list.size());
            int ty = BY + (trackH - th) * scroll / Math.max(1, list.size() - rows);
            Ui.rr(g, BX + BW + 1, ty, 3, th, Ui.ACCENT);
        }
    }

    private void drawDenied(Gfx g) {
        Ui.rr(g, SX, SY + 28, DW - 12, DH - SY - 34, Ui.PANEL);
        int cy = SY + 70;
        Ui.center(g, "Operator permissions required", SX, cy, DW - 12, Ui.RED);
        cy += 20;
        String[] lines = {
            "You are not an operator on this server, so every function is disabled.",
            "Your settings are kept in memory and come back automatically when you are OP again, or in your own singleplayer / LAN world.",
            "Ask the server owner to run:  /op <your name>" };
        for (String l : lines)
            for (String w : Ui.wrap(g, l, DW - 60)) { Ui.center(g, w, SX, cy, DW - 12, Ui.TEXT); cy += 12; }
    }

    private void drawRow(Gfx g, Setting s, int ry, int mx, int my) {
        if (s.kind == Setting.Kind.LABEL) { g.text(Ui.fit(g, s.name, BW - 8), BX + 4, ry + 6, Ui.DIM); return; }
        boolean hov = Ui.in(mx, my, BX, ry, BW, ROW);
        Ui.rr(g, BX, ry, BW, ROW, hov ? Ui.HOVER : Ui.BTN);
        switch (s.kind) {
            case BUTTON -> Ui.center(g, Ui.fit(g, s.name, BW - 12), BX, ry + 6, BW, Ui.TEXT);
            case TOGGLE -> {
                g.text(Ui.fit(g, s.name, BW - 50), BX + 8, ry + 6, Ui.TEXT);
                Ui.pill(g, BX + BW - 36, ry + 4, s.on);
            }
            case SLIDER -> {
                g.text(Ui.fit(g, s.name, BW - 200), BX + 8, ry + 6, Ui.TEXT);
                int tx = trackX(), tw = trackW();
                float frac = (s.value - s.min) / (s.max - s.min);
                int fw = (int) (tw * frac);
                Ui.rr(g, tx, ry + 8, tw, 4, Ui.DARK);
                if (fw > 0) Ui.rr(g, tx, ry + 8, fw, 4, Ui.ACCENT);
                Ui.rr(g, tx + fw - 2, ry + 5, 4, 10, Ui.TEXT);
                String v = String.format("%.2f", s.value).replaceAll("0+$", "").replaceAll("\\.$", "");
                g.text(v, tx - 8 - g.width(v), ry + 6, Ui.DIM);
            }
            default -> {}
        }
    }

    private int trackX() { return BX + BW - 150; }
    private int trackW() { return 140; }

    private void setSlider(Setting s, int mx) {
        double f = Math.max(0, Math.min(1, (mx - trackX()) / (double) trackW()));
        double v = s.min + f * (s.max - s.min);
        v = Math.round(v / s.step) * s.step;
        s.value = (float) Math.max(s.min, Math.min(s.max, v));
    }

    // ------------------------------------------------------------------ input
    /** @return true if the Close button was pressed */
    public boolean click(int mx, int my, int btn) {
        if (Ui.in(mx, my, SX, SY, SW, 20)) return true;
        if (!Access.allowed) return false;
        for (int i = 0; i < Menu.CATEGORIES.size(); i++)
            if (Ui.in(mx, my, SX + 3, SY + 30 + i * 18, SW - 6, 16)) { cat = i; tab = 0; scroll = 0; return false; }
        Menu.Category c = Menu.CATEGORIES.get(cat);
        int tx = CX + 6;
        for (int j = 0; j < c.tabs().size(); j++) {
            int w = Gfx.textWidth(c.tabs().get(j).name()) + 16;
            if (Ui.in(mx, my, tx, CY + 6, w, 20)) { tab = j; scroll = 0; return false; }
            tx += w + 4;
        }
        Menu.Tab t = cur();
        if (t.page() != null) { t.page().click(mx, my, btn); return false; }
        List<Setting> list = t.settings();
        int rows = BH / STEP;
        for (int i = 0; i < rows && scroll + i < list.size(); i++) {
            int ry = BY + i * STEP;
            if (!Ui.in(mx, my, BX, ry, BW, ROW)) continue;
            Setting s = list.get(scroll + i);
            switch (s.kind) {
                case TOGGLE -> { s.on = !s.on; Menu.changed(); }
                case BUTTON -> s.action.run();
                case SLIDER -> { dragging = s; setSlider(s, mx); }
                default -> {}
            }
            return false;
        }
        return false;
    }

    public void drag(int mx, int my) {
        if (dragging != null) setSlider(dragging, mx);
        else if (Access.allowed && cur().page() != null) cur().page().drag(mx, my);
    }

    public void release() {
        if (dragging != null) { dragging = null; Menu.changed(); }
        if (Access.allowed && cur().page() != null) cur().page().release();
    }

    public void scroll(int mx, int my, double dy) {
        if (!Access.allowed) return;
        Menu.Tab t = cur();
        if (t.page() != null) { t.page().scroll(mx, my, dy); return; }
        scroll -= (int) Math.signum(dy);
    }

    public boolean key(int key, int scan, int mods) {
        if (!Access.allowed) return false;
        Menu.Tab t = cur();
        return t.page() != null && t.page().key(key, scan, mods);
    }

    public boolean typed(char c) {
        if (!Access.allowed) return false;
        Menu.Tab t = cur();
        return t.page() != null && t.page().typed(c);
    }
}
