package dev.riffedcheats;

import java.util.ArrayList;
import java.util.List;

/** Command executor with live suggestions. Runs with full permissions in your own world (no cheats needed). */
public final class ConsolePage implements Page {
    private static final List<String> LOG = new ArrayList<>();
    private static long lastRun;

    public static synchronized void log(String s) {
        if (System.currentTimeMillis() - lastRun > 5000) return;
        for (String line : s.split("\n")) LOG.add(line);
        while (LOG.size() > 60) LOG.remove(0);
    }

    private final TextField field = new TextField();
    private final List<String> history = new ArrayList<>();
    private int histPos = -1;
    private volatile List<String> sugg = List.of();
    private int sel = -1;
    private String lastAsked = "";
    private int seq;
    private int x, y, w, h;

    @Override public boolean wantsText() { return true; }

    @Override
    public void render(Gfx g, int mx, int my, int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        if (!field.text.equals(lastAsked)) ask();
        g.text("Enter: run   Tab: complete   Up/Down: pick or history", x, y, Ui.DIM);
        field.draw(g, x, y + 14, w, 18, "e.g. give @s diamond 64");

        List<String> s = sugg;
        int rows = Math.min(7, s.size());
        int sy = y + 36;
        for (int i = 0; i < rows; i++) {
            boolean on = i == sel;
            Ui.rr(g, x, sy + i * 14, w, 13, on ? Ui.SEL : Ui.BTN);
            String full = s.get(i);
            String shown = full.substring(full.lastIndexOf(' ') + 1);
            g.text(Ui.fit(g, shown.isEmpty() ? full : shown, w - 10), x + 5, sy + i * 14 + 3, Ui.TEXT);
        }
        int ly = sy + rows * 14 + 6;
        Ui.rr(g, x, ly, w, y + h - ly, Ui.PANEL);
        synchronized (ConsolePage.class) {
            int lines = (y + h - ly - 6) / 11;
            List<String[]> wrapped = new ArrayList<>();   // {text, isCommand}
            for (int i = LOG.size() - 1; i >= 0 && wrapped.size() < lines; i--) {
                String l = LOG.get(i);
                List<String> parts = Ui.wrap(g, l, w - 12);
                for (int k = parts.size() - 1; k >= 0 && wrapped.size() < lines; k--)
                    wrapped.add(new String[] { parts.get(k), l.startsWith("> ") ? "1" : "0" });
            }
            for (int i = 0; i < wrapped.size(); i++) {
                String[] r = wrapped.get(wrapped.size() - 1 - i);
                g.text(r[0], x + 5, ly + 4 + i * 11, r[1].equals("1") ? Ui.ACCENT : Ui.TEXT);
            }
        }
    }

    private void ask() {
        lastAsked = field.text;
        sel = -1;
        final int my = ++seq;
        if (field.text.isBlank()) { sugg = List.of(); return; }
        Compat.suggest(field.text, list -> { if (my == seq) sugg = list; });
    }

    @Override
    public boolean typed(char c) { return field.typed(c); }

    @Override
    public boolean key(int key, int scan, int mods) {
        if (key == 257 || key == 335) { // enter
            String c = field.text.trim();
            if (!c.isEmpty()) {
                synchronized (ConsolePage.class) { LOG.add("> " + c); }
                lastRun = System.currentTimeMillis();
                Cmd.run(c);
                history.add(c);
                histPos = -1;
                field.text = "";
            }
            return true;
        }
        if (key == 258) { // tab
            List<String> s = sugg;
            if (!s.isEmpty()) { field.text = s.get(sel < 0 ? 0 : sel) ; lastAsked = field.text; ask(); }
            return true;
        }
        if (key == 265 || key == 264) { // up / down
            List<String> s = sugg;
            if (!s.isEmpty()) {
                int n = Math.min(7, s.size());
                sel = key == 264 ? (sel + 1) % n : (sel <= 0 ? n - 1 : sel - 1);
            } else if (!history.isEmpty()) {
                histPos = key == 265 ? (histPos < 0 ? history.size() - 1 : Math.max(0, histPos - 1))
                                     : (histPos < 0 ? -1 : Math.min(history.size() - 1, histPos + 1));
                field.text = histPos < 0 ? "" : history.get(histPos);
            }
            return true;
        }
        return field.key(key, mods);
    }

    @Override public boolean click(int mx, int my, int btn) {
        int sy = y + 36;
        List<String> s = sugg;
        for (int i = 0; i < Math.min(7, s.size()); i++)
            if (Ui.in(mx, my, x, sy + i * 14, w, 13)) { field.text = s.get(i); lastAsked = field.text; ask(); return true; }
        return false;
    }
}
