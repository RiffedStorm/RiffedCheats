package dev.riffedcheats.core;

import java.util.List;

/** Settings > Configs: off by default. When on: save the settings under a name, load, overwrite, delete, and pick one to load at start. */
public final class ConfigsPage implements Page {
    private static final int ROWH = 18, LIST = 62, DOUBLE_MS = 350;

    private final TextField name = new TextField();
    private String selected, armed, loaded, note;
    private long lastClick, noteUntil;
    private String lastClicked;
    private int scroll, x, y, w, h;
    private final Scrollbar bar = new Scrollbar();

    public ConfigsPage() { name.max = 32; }

    @Override public boolean wantsText() { return Menu.MULTI_CONFIG.on; }

    private int rows() { return Math.max(1, (h - LIST - 36) / ROWH); }
    private int btnW() { return (w - 8) / 3; }
    private String typed() { return Configs.clean(name.text); }

    private String saveLabel() {
        if (!typed().isEmpty()) return "Save as New";
        return selected != null ? "Save to Selected" : "Type a Name";
    }

    @Override
    public void render(Gfx g, int mx, int my, int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        Ui.rr(g, x, y, w, 20, Ui.in(mx, my, x, y, w, 20) ? Ui.HOVER : Ui.BTN);
        g.text("Multiple Configs", x + 8, y + 6, Ui.TEXT);
        Ui.pill(g, x + w - 36, y + 4, Menu.MULTI_CONFIG.on);
        if (!Menu.MULTI_CONFIG.on) {
            int ty = y + 34;
            for (String l : Ui.wrap(g, "Off: the menu keeps one set of settings, as always. Turn it on to save named configs and pick one to load at start.", w - 8)) {
                g.text(l, x + 4, ty, Ui.DIM);
                ty += 12;
            }
            return;
        }
        name.focused = true;
        List<String> names = Configs.names();
        if (selected != null && !names.contains(selected)) selected = null;

        name.draw(g, x, y + 26, w - 124, 18, "Config name, Enter = save");
        boolean canSave = !typed().isEmpty() || selected != null;
        if (canSave) Ui.button(g, saveLabel(), x + w - 120, y + 26, 120, 18, Ui.in(mx, my, x + w - 120, y + 26, 120, 18), false);
        else Ui.greyButton(g, saveLabel(), x + w - 120, y + 26, 120, 18);

        g.text("Saved configs (" + names.size() + ")", x, y + 50, Ui.DIM);
        String auto = Configs.autoload();
        int rows = rows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, names.size() - rows)));
        bar.draw(g, x + w + 1, y + LIST, rows * ROWH - 1, names.size(), rows, scroll, mx, my);
        if (names.isEmpty()) g.text("Nothing saved yet.", x + 4, y + LIST + 5, Ui.DIM);
        for (int i = 0; i < rows && scroll + i < names.size(); i++) {
            String n = names.get(scroll + i);
            int ry = y + LIST + i * ROWH;
            boolean isAuto = n.equals(auto);
            Ui.rr(g, x, ry, w, ROWH - 1, n.equals(selected) ? Ui.SEL : Ui.in(mx, my, x, ry, w, ROWH - 1) ? Ui.HOVER : Ui.BTN);
            if (isAuto) g.fill(x, ry, x + 3, ry + ROWH - 1, Ui.GREEN);
            int right = x + w - 6;
            if (isAuto) {
                String t = "AUTOLOAD";
                int tw = g.width(t) + 8;
                Ui.rr(g, right - tw, ry + 2, tw, ROWH - 5, 0xFF2E7D4F);
                g.text(t, right - tw + 4, ry + 5, Ui.TEXT);
                right -= tw + 4;
            }
            if (n.equals(loaded)) { String t = "loaded"; right -= g.width(t); g.text(t, right, ry + 5, Ui.DIM); right -= 6; }
            Ui.label(g, n, x + 8, ry + 5, right - x - 12, Ui.TEXT);
        }

        int by = y + h - 18, bw = btnW();
        boolean sel = selected != null;
        action(g, "Load", x, by, bw, sel, false, mx, my);
        action(g, sel && selected.equals(auto) ? "Stop Autoload" : "Set Autoload", x + bw + 4, by, bw, sel, false, mx, my);
        action(g, sel && selected.equals(armed) ? "Sure?" : "Delete", x + 2 * (bw + 4), by, bw, sel, true, mx, my);
        boolean noted = note != null && System.currentTimeMillis() < noteUntil;
        g.text(Ui.fit(g, noted ? note : "Double-click a config to load it.", w), x, by - 11, noted ? Ui.TEXT : Ui.DIM);
    }

    private static void action(Gfx g, String label, int bx, int by, int bw, boolean enabled, boolean danger, int mx, int my) {
        if (enabled) Ui.button(g, label, bx, by, bw, 18, Ui.in(mx, my, bx, by, bw, 18), danger);
        else Ui.greyButton(g, label, bx, by, bw, 18);
    }

    private void note(String s) { note = s; noteUntil = System.currentTimeMillis() + 3000; }

    private void load(String n) {
        if (Configs.load(n)) { loaded = n; note("Loaded " + n + "."); } else note("Could not read " + n + ".");
    }

    private void save() {
        String n = typed();
        if (n.isEmpty()) n = selected;
        if (n == null) return;
        if (Configs.save(n)) { selected = n; loaded = n; name.text = ""; note("Saved " + n + "."); } else note("Could not save " + n + ".");
    }

    @Override
    public boolean click(int mx, int my, int btn) {
        if (Ui.in(mx, my, x, y, w, 20)) { Menu.MULTI_CONFIG.on = !Menu.MULTI_CONFIG.on; Menu.changed(); return true; }
        if (!Menu.MULTI_CONFIG.on) return false;
        if (bar.press(mx, my)) { scroll = bar.at(my); return true; }
        String wasArmed = armed;
        armed = null;
        if (Ui.in(mx, my, x + w - 120, y + 26, 120, 18)) { save(); return true; }
        List<String> names = Configs.names();
        int rows = rows();
        for (int i = 0; i < rows && scroll + i < names.size(); i++) {
            if (!Ui.in(mx, my, x, y + LIST + i * ROWH, w, ROWH - 1)) continue;
            String n = names.get(scroll + i);
            long now = System.currentTimeMillis();
            selected = n;
            if (n.equals(lastClicked) && now - lastClick < DOUBLE_MS) { load(n); lastClicked = null; }
            else { lastClicked = n; lastClick = now; }
            return true;
        }
        if (selected == null) return false;
        int by = y + h - 18, bw = btnW();
        if (Ui.in(mx, my, x, by, bw, 18)) { load(selected); return true; }
        if (Ui.in(mx, my, x + bw + 4, by, bw, 18)) {
            boolean stop = selected.equals(Configs.autoload());
            Configs.setAutoload(stop ? null : selected);
            note(stop ? "Nothing loads at start now." : selected + " will load at start.");
            return true;
        }
        if (Ui.in(mx, my, x + 2 * (bw + 4), by, bw, 18)) {
            if (selected.equals(wasArmed)) { Configs.delete(selected); note("Deleted " + selected + "."); selected = null; }
            else armed = selected;
            return true;
        }
        return false;
    }

    @Override public boolean drag(int mx, int my) { if (!bar.dragging()) return false; scroll = bar.at(my); return true; }
    @Override public void release() { bar.release(); }
    @Override public boolean scroll(int mx, int my, double dy) { scroll -= (int) Math.signum(dy); return true; }
    @Override public boolean typed(char c) { return Menu.MULTI_CONFIG.on && name.typed(c); }

    @Override
    public boolean key(int key, int scan, int mods) {
        if (!Menu.MULTI_CONFIG.on) return false;
        if (key == 257 || key == 335) { save(); return true; }
        return name.key(key, mods);
    }
}
