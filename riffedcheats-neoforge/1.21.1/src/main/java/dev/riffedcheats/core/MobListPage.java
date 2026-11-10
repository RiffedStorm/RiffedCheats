package dev.riffedcheats.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.world.entity.MobCategory;

/** "Custom Mobs" tab of Wallhack and Aimbot: your own list of mobs, on top of the presets. */
public final class MobListPage implements Page {
    private static final int LW = 172, ROWH = 18, LIST = 20, SEL_Y = 48;

    private final String key;
    private final Setting use;
    private final TextField search = new TextField();
    private final Stars stars = new Stars(Favorites.MOB);
    private final Scrollbar bar = new Scrollbar(), selBar = new Scrollbar();
    private List<MobsPage.Entry> view = List.of();
    private String lastQuery;
    private int lastFav = -1, listScroll, selScroll, x, y, w, h;

    public MobListPage(String key, Setting use) { this.key = key; this.use = use; }

    @Override public boolean wantsText() { return true; }

    private void store(Set<String> s) { Menu.setStr(key, String.join(",", s)); }

    private void refilter() {
        if (search.text.equals(lastQuery) && Favorites.version == lastFav) return;
        if (!search.text.equals(lastQuery)) listScroll = 0;
        lastQuery = search.text;
        lastFav = Favorites.version;
        String[] q = search.text.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<MobsPage.Entry> out = new ArrayList<>();
        for (MobsPage.Entry e : MobsPage.entries()) {
            boolean ok = true;
            for (String t : q) if (!t.isEmpty() && !e.lower().contains(t)) { ok = false; break; }
            if (ok) out.add(e);
        }
        view = Favorites.arrange(Favorites.MOB, out, MobsPage.Entry::id);
    }

    private int rx() { return x + LW + 8; }
    private int rw() { return w - LW - 8; }
    private int listRows() { return Math.max(1, (h - LIST - 11) / ROWH); }
    private int selRows() { return Math.max(1, (h - SEL_Y) / 16); }

    @Override
    public void render(Gfx g, int mx, int my, int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        refilter();
        search.focused = true;
        Set<String> sel = Targets.list(key);

        search.draw(g, x, y, LW, 16, "Search mobs...");
        int rows = listRows();
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, view.size() - rows)));
        bar.draw(g, x + LW + 2, y + LIST, rows * ROWH - 1, view.size(), rows, listScroll, mx, my);
        for (int i = 0; i < rows && listScroll + i < view.size(); i++) {
            MobsPage.Entry e = view.get(listScroll + i);
            int ry = y + LIST + i * ROWH;
            boolean on = sel.contains(e.id());
            Ui.rr(g, x, ry, LW, ROWH - 1, on ? Ui.SEL : Ui.in(mx, my, x, ry, LW, ROWH - 1) ? Ui.HOVER : Ui.BTN);
            g.item(e.icon(), x + 1, ry);
            Ui.label(g, e.name(), x + 20, ry + 5, LW - 36, Ui.TEXT);
            stars.draw(g, e.id(), x + LW - Stars.W, ry, ROWH - 1, mx, my);
        }
        g.text(view.size() + " entities, click to add or remove", x + 2, y + h - 9, Ui.DIM);

        int rx = rx(), rw = rw();
        Ui.rr(g, rx, y, rw, 20, Ui.in(mx, my, rx, y, rw, 20) ? Ui.HOVER : Ui.BTN);
        g.text("Use Custom Mobs", rx + 8, y + 6, Ui.TEXT);
        Ui.pill(g, rx + rw - 36, y + 4, use.on);

        g.text("Selected (" + sel.size() + ")", rx, y + 31, Ui.DIM);
        Ui.button(g, "All Hostile", rx + rw - 110, y + 26, 64, 16, Ui.in(mx, my, rx + rw - 110, y + 26, 64, 16), false);
        Ui.button(g, "Clear", rx + rw - 42, y + 26, 42, 16, Ui.in(mx, my, rx + rw - 42, y + 26, 42, 16), false);

        List<String> order = new ArrayList<>(sel);
        int sr = selRows();
        selScroll = Math.max(0, Math.min(selScroll, Math.max(0, order.size() - sr)));
        selBar.draw(g, x + w + 1, y + SEL_Y, sr * 16 - 1, order.size(), sr, selScroll, mx, my);
        if (order.isEmpty()) g.text("Pick mobs from the list.", rx, y + SEL_Y + 3, Ui.DIM);
        for (int i = 0; i < sr && selScroll + i < order.size(); i++) {
            int ry = y + SEL_Y + i * 16;
            Ui.rr(g, rx, ry, rw, 15, Ui.BTN);
            Ui.label(g, MobsPage.nameOf(order.get(selScroll + i)), rx + 4, ry + 4, rw - 24, Ui.TEXT);
            Ui.button(g, "x", rx + rw - 15, ry, 15, 15, Ui.in(mx, my, rx + rw - 15, ry, 15, 15), true);
        }
    }

    @Override
    public boolean click(int mx, int my, int btn) {
        if (bar.press(mx, my)) { listScroll = bar.at(my); return true; }
        if (selBar.press(mx, my)) { selScroll = selBar.at(my); return true; }
        Set<String> sel = Targets.list(key);
        int rows = listRows();
        for (int i = 0; i < rows && listScroll + i < view.size(); i++) {
            MobsPage.Entry e = view.get(listScroll + i);
            int ry = y + LIST + i * ROWH;
            if (stars.press(e.id(), x + LW - Stars.W, ry, ROWH - 1, mx, my)) return true;
            if (Ui.in(mx, my, x, ry, LW, ROWH - 1)) {
                if (!sel.remove(e.id())) sel.add(e.id());
                store(sel);
                return true;
            }
        }
        int rx = rx(), rw = rw();
        if (Ui.in(mx, my, rx, y, rw, 20)) { use.on = !use.on; Menu.changed(); return true; }
        if (Ui.in(mx, my, rx + rw - 110, y + 26, 64, 16)) {
            for (MobsPage.Entry e : MobsPage.entries()) if (e.cat() == MobCategory.MONSTER) sel.add(e.id());
            store(sel);
            return true;
        }
        if (Ui.in(mx, my, rx + rw - 42, y + 26, 42, 16)) { sel.clear(); store(sel); return true; }
        List<String> order = new ArrayList<>(sel);
        int sr = selRows();
        for (int i = 0; i < sr && selScroll + i < order.size(); i++)
            if (Ui.in(mx, my, rx + rw - 15, y + SEL_Y + i * 16, 15, 15)) { sel.remove(order.get(selScroll + i)); store(sel); return true; }
        return false;
    }

    @Override
    public boolean drag(int mx, int my) {
        if (bar.dragging()) { listScroll = bar.at(my); return true; }
        if (selBar.dragging()) { selScroll = selBar.at(my); return true; }
        if (stars.active()) { stars.drag(my, view.stream().map(MobsPage.Entry::id).toList(), y + LIST, ROWH, listScroll); return true; }
        return false;
    }

    @Override public void release() { stars.release(); bar.release(); selBar.release(); }

    @Override
    public boolean scroll(int mx, int my, double dy) {
        int d = (int) -Math.signum(dy);
        if (mx < x + LW + 4) listScroll += d * 3; else if (my >= y + SEL_Y) selScroll += d;
        return true;
    }

    @Override public boolean typed(char c) { return search.typed(c); }
    @Override public boolean key(int key, int scan, int mods) { return search.key(key, mods); }
}
