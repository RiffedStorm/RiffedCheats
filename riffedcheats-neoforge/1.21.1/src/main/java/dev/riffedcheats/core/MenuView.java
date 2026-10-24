package dev.riffedcheats.core;

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
    private final Scrollbar bar = new Scrollbar();

    private static Menu.Tab cur() {
        Menu.Category c = Menu.CATEGORIES.get(cat);
        tab = Math.min(tab, c.tabs().size() - 1);
        return c.tabs().get(tab);
    }

    public boolean capturesText() { return Access.allowed && cur().page() != null && cur().page().wantsText(); }

    // ------------------------------------------------------------------ render
    public void render(Gfx g, int mx, int my) {
        Updater.autoCheck();
        Ui.rr(g, 0, 0, DW, DH, Ui.BG);
        String title = "RiffedCheats " + Updater.current();
        g.text(title, 6, 6, Ui.TEXT);
        String b = badge();
        if (b != null) {
            int bx = 6 + g.width(title) + 10;
            Ui.rr(g, bx - 4, 3, g.width(b) + 8, 14, Ui.in(mx, my, bx - 4, 3, g.width(b) + 8, 14) ? Ui.HOVER : Ui.BTN);
            g.text(b, bx, 6, Updater.justUpdated() ? Ui.GREEN : Ui.ACCENT);
        }
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
        bar.draw(g, BX + BW + 1, BY, rows * STEP - 2, list.size(), rows, scroll, mx, my);
    }

    /** Header notice: an update is available / was just installed. Clicking it opens Settings > Updates. */
    private static String badge() {
        if (Updater.justUpdated()) return "Updated to " + Updater.UPDATED_TO;
        if (Updater.state == Updater.State.AVAILABLE) return "Update available: " + Updater.latest;
        return null;
    }

    private static boolean badgeHit(int mx, int my) {
        String b = badge();
        if (b == null) return false;
        int bx = 6 + Gfx.textWidth("RiffedCheats " + Updater.current()) + 10;
        return Ui.in(mx, my, bx - 4, 3, Gfx.textWidth(b) + 8, 14);
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
        if (s.kind == Setting.Kind.BUTTONS) {
            if (!s.name.isEmpty()) g.text(Ui.fit(g, s.name, LBL - 12), BX + 8, ry + 6, Ui.TEXT);
            for (int i = 0; i < s.opts.length; i++) {
                int bx = optX(s, i), bw = optW(s);
                Ui.button(g, s.opts[i], bx, ry, bw, ROW, Ui.in(mx, my, bx, ry, bw, ROW), false);
            }
            return;
        }
        if (s.kind == Setting.Kind.RULE) {
            g.text(Ui.fit(g, s.name, BW - 110), BX + 8, ry + 6, Ui.TEXT);
            Boolean v = Rules.get(s.opts[0]);
            int bx = BX + BW - 90;
            Ui.stateButton(g, v == null ? "..." : v ? "ON" : "OFF", bx, ry, 90, ROW, Ui.in(mx, my, bx, ry, 90, ROW), v);
            return;
        }
        boolean hov = Ui.in(mx, my, BX, ry, BW, ROW);
        Ui.rr(g, BX, ry, BW, ROW, hov ? Ui.HOVER : Ui.BTN);
        switch (s.kind) {
            case BUTTON -> Ui.center(g, Ui.fit(g, s.name, BW - 12), BX, ry + 6, BW, Ui.TEXT);
            case TOGGLE -> {
                g.text(Ui.fit(g, s.name, BW - 50), BX + 8, ry + 6, Ui.TEXT);
                Ui.pill(g, BX + BW - 36, ry + 4, s.on);
            }
            case SLIDER -> {
                g.text(Ui.fit(g, s.name, BW - 236), BX + 8, ry + 6, Ui.TEXT);
                if (s.value != s.def) Ui.button(g, "\u21BA", resetX(), ry + 3, 14, 14, Ui.in(mx, my, resetX(), ry + 3, 14, 14), false);
                int tx = trackX(), tw = trackW();
                float frac = (s.value - s.min) / (s.max - s.min);
                int fw = (int) (tw * frac);
                Ui.rr(g, tx, ry + 8, tw, 4, Ui.DARK);
                if (fw > 0) Ui.rr(g, tx, ry + 8, fw, 4, Ui.ACCENT);
                Ui.rr(g, tx + fw - 2, ry + 5, 4, 10, Ui.TEXT);
                String v = String.format(java.util.Locale.ROOT, "%.2f", s.value).replaceAll("0+$", "").replaceAll("\\.$", "");
                g.text(v, tx - 8 - g.width(v), ry + 6, Ui.DIM);
            }
            default -> {}
        }
    }

    /** Button rows: the left LBL units hold the label (rows without a label still align with the ones that have one). */
    private static final int LBL = 104;
    private static int optW(Setting s) { return (BW - LBL - (s.opts.length - 1) * 4) / s.opts.length; }
    private static int optX(Setting s, int i) { return BX + LBL + i * (optW(s) + 4); }

    private int trackX() { return BX + BW - 150; }
    /** Reset-to-default button of a slider row, left of the value text. */
    private int resetX() { return BX + BW - 214; }
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
        if (badgeHit(mx, my)) {
            for (int i = 0; i < Menu.CATEGORIES.size(); i++) {
                List<Menu.Tab> tabs = Menu.CATEGORIES.get(i).tabs();
                for (int j = 0; j < tabs.size(); j++) if (tabs.get(j).page() instanceof UpdatesPage) { cat = i; tab = j; scroll = 0; }
            }
            return false;
        }
        for (int i = 0; i < Menu.CATEGORIES.size(); i++)
            if (Ui.in(mx, my, SX + 3, SY + 30 + i * 18, SW - 6, 16)) {
                if (i == cat && cur().page() != null) cur().page().reselect();
                cat = i; tab = 0; scroll = 0;
                return false;
            }
        Menu.Category c = Menu.CATEGORIES.get(cat);
        int tx = CX + 6;
        for (int j = 0; j < c.tabs().size(); j++) {
            int w = Gfx.textWidth(c.tabs().get(j).name()) + 16;
            if (Ui.in(mx, my, tx, CY + 6, w, 20)) {
                if (j == tab && cur().page() != null) cur().page().reselect();
                tab = j; scroll = 0;
                return false;
            }
            tx += w + 4;
        }
        Menu.Tab t = cur();
        if (t.page() != null) { t.page().click(mx, my, btn); return false; }
        if (bar.press(mx, my)) { scroll = bar.at(my); return false; }
        List<Setting> list = t.settings();
        int rows = BH / STEP;
        for (int i = 0; i < rows && scroll + i < list.size(); i++) {
            int ry = BY + i * STEP;
            if (!Ui.in(mx, my, BX, ry, BW, ROW)) continue;
            Setting s = list.get(scroll + i);
            switch (s.kind) {
                case TOGGLE -> { s.on = !s.on; Menu.changed(); }
                case BUTTON -> s.action.run();
                case BUTTONS -> {
                    for (int k = 0; k < s.opts.length; k++)
                        if (Ui.in(mx, my, optX(s, k), ry, optW(s), ROW)) { s.acts[k].run(); break; }
                }
                case SLIDER -> {
                    if (s.value != s.def && Ui.in(mx, my, resetX(), ry + 3, 14, 14)) { s.value = s.def; Menu.changed(); }
                    else if (mx >= trackX() - 4) { dragging = s; setSlider(s, mx); }
                }
                case RULE -> { if (mx >= BX + BW - 90) Rules.flip(s.opts[0]); }
                default -> {}
            }
            return false;
        }
        return false;
    }

    public void drag(int mx, int my) {
        if (dragging != null) setSlider(dragging, mx);
        else if (bar.dragging()) scroll = bar.at(my);
        else if (Access.allowed && cur().page() != null) cur().page().drag(mx, my);
    }

    public void release() {
        if (dragging != null) { dragging = null; Menu.changed(); }
        bar.release();
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
