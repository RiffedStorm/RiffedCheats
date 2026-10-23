package dev.riffedcheats;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Command executor with live suggestions. Runs with full permissions in your own world (no cheats needed). */
public final class ConsolePage implements Page {
    private static final List<String> LOG = new ArrayList<>();
    /** "[123, ~, -456]" style coordinates (locate, etc.): clicking such a line fills in a teleport command. */
    private static final Pattern COORDS = Pattern.compile("\\[(-?\\d+(?:\\.\\d+)?), (~|-?\\d+(?:\\.\\d+)?), (-?\\d+(?:\\.\\d+)?)]");

    public static synchronized void log(String s) {
        for (String line : s.split("\n")) LOG.add(line);
        while (LOG.size() > 200) LOG.remove(0);
    }

    private final TextField field = new TextField();
    private final List<String> history = new ArrayList<>();
    private int histPos = -1;
    private volatile List<String> sugg = List.of();
    private int sel = -1;
    private String lastAsked = "";
    private int seq;
    private int x, y, w, h;
    private final List<int[]> clickable = new ArrayList<>();   // {y, logIndex} of drawn lines that contain coordinates

    @Override public boolean wantsText() { return true; }

    private int clrLogX() { return x + w - 132; }
    private int clrHistX() { return x + w - 66; }

    @Override
    public void render(Gfx g, int mx, int my, int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        if (!field.text.equals(lastAsked)) ask();
        g.text("Enter: run  Tab: complete  Up/Down: history", x, y + 1, Ui.DIM);
        Ui.button(g, "Clear Log", clrLogX(), y - 2, 62, 12, Ui.in(mx, my, clrLogX(), y - 2, 62, 12), false);
        Ui.button(g, "Clear History", clrHistX(), y - 2, 66, 12, Ui.in(mx, my, clrHistX(), y - 2, 66, 12), false);
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
        clickable.clear();
        synchronized (ConsolePage.class) {
            int lines = (y + h - ly - 16) / 11;   // the last line is kept free for the hint
            List<Object[]> wrapped = new ArrayList<>();   // {text, logIndex}
            for (int i = LOG.size() - 1; i >= 0 && wrapped.size() < lines; i--) {
                List<String> parts = Ui.wrap(g, LOG.get(i), w - 12);
                for (int k = parts.size() - 1; k >= 0 && wrapped.size() < lines; k--) wrapped.add(new Object[] { parts.get(k), i });
            }
            for (int i = 0; i < wrapped.size(); i++) {
                Object[] r = wrapped.get(wrapped.size() - 1 - i);
                int idx = (int) r[1], ry = ly + 4 + i * 11;
                String src = LOG.get(idx);
                boolean cmd = src.startsWith("> "), coords = !cmd && COORDS.matcher(src).find();
                if (coords) {
                    clickable.add(new int[] { ry, idx });
                    if (Ui.in(mx, my, x, ry - 1, w, 11)) Ui.rr(g, x + 2, ry - 1, w - 4, 10, Ui.HOVER);
                }
                g.text((String) r[0], x + 5, ry, cmd ? Ui.ACCENT : coords ? Ui.GREEN : Ui.TEXT);
            }
        }
        if (!clickable.isEmpty()) g.text("Click a green line to fill in a teleport to it.", x + 5, y + h - 10, Ui.DIM);
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
                log("> " + c);
                Cmd.console(c);
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
        if (Ui.in(mx, my, clrLogX(), y - 2, 62, 12)) { synchronized (ConsolePage.class) { LOG.clear(); } return true; }
        if (Ui.in(mx, my, clrHistX(), y - 2, 66, 12)) { history.clear(); histPos = -1; return true; }
        int sy = y + 36;
        List<String> s = sugg;
        for (int i = 0; i < Math.min(7, s.size()); i++)
            if (Ui.in(mx, my, x, sy + i * 14, w, 13)) { field.text = s.get(i); lastAsked = field.text; ask(); return true; }
        for (int[] c : clickable) {
            if (!Ui.in(mx, my, x, c[0] - 1, w, 11)) continue;
            String src;
            synchronized (ConsolePage.class) { if (c[1] >= LOG.size()) return true; src = LOG.get(c[1]); }
            Matcher m = COORDS.matcher(src);
            if (m.find()) {
                // no height (locate structures say "~"): spreadplayers puts you on the surface instead of mid-air / underground
                boolean nether = Compat.dimension().equals("minecraft:the_nether");
                field.text = !m.group(2).equals("~") ? "tp @s " + m.group(1) + " " + m.group(2) + " " + m.group(3)
                        : "spreadplayers " + m.group(1) + " " + m.group(3) + " 0 1 " + (nether ? "under 120 " : "") + "false @s";
                lastAsked = "";
            }
            return true;
        }
        return false;
    }
}
