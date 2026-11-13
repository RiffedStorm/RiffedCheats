package dev.riffedcheats.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.world.entity.MobCategory;

/** Mobs > Kill Field: pick mobs from the list; while the field is on, those die when they come within the radius. */
public final class KillFieldPage implements Page {
    private static final int LW = 172, ROWH = 18, LIST = 38, SEL_Y = 106;
    private static final String[] FILTERS = { "All", "Hostile", "Passive", "Other" };
    private static final int[] FW = { 28, 46, 46, 40 };
    private static final int[] PRESETS = { 16, 32, 64 };

    private final TextField search = new TextField();
    private final Stars stars = new Stars(Favorites.MOB);
    private final Scrollbar bar = new Scrollbar(), selBar = new Scrollbar();
    private List<MobsPage.Entry> view = List.of();
    private String lastQuery;
    private int filter, lastFilter = -1, lastFav = -1, listScroll, selScroll, x, y, w, h;

    @Override public boolean wantsText() { return true; }

    // ------------------------------------------------------------------ the saved selection
    public static Set<String> ids() {
        Set<String> out = new LinkedHashSet<>();
        for (String s : Menu.str("killfield").split(",")) if (!s.isBlank()) out.add(s);
        return out;
    }

    private static void store(Set<String> s) { Menu.setStr("killfield", String.join(",", s)); }

    private boolean pass(MobsPage.Entry e) {
        return switch (filter) {
            case 1 -> e.cat() == MobCategory.MONSTER;
            case 2 -> e.cat() != MobCategory.MONSTER && e.cat() != MobCategory.MISC;
            case 3 -> e.cat() == MobCategory.MISC;
            default -> true;
        };
    }

    private void refilter() {
        if (search.text.equals(lastQuery) && filter == lastFilter && Favorites.version == lastFav) return;
        boolean moved = !search.text.equals(lastQuery) || filter != lastFilter;
        lastQuery = search.text;
        lastFilter = filter;
        lastFav = Favorites.version;
        String[] q = search.text.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<MobsPage.Entry> out = new ArrayList<>();
        for (MobsPage.Entry e : MobsPage.entries()) {
            if (!pass(e)) continue;
            boolean ok = true;
            for (String t : q) if (!t.isEmpty() && !e.lower().contains(t)) { ok = false; break; }
            if (ok) out.add(e);
        }
        view = Favorites.arrange(Favorites.MOB, out, MobsPage.Entry::id);
        if (moved) listScroll = 0;
    }

    private int rx() { return x + LW + 8; }
    private int rw() { return w - LW - 8; }
    private int listRows() { return Math.max(1, (h - LIST - 11) / ROWH); }
    private int selRows() { return Math.max(1, (h - SEL_Y) / 16); }
    private int fx(int i) { int fx = x; for (int k = 0; k < i; k++) fx += FW[k] + 4; return fx; }
    private List<String> ordered() { return new ArrayList<>(ids()); }

    @Override
    public void render(Gfx g, int mx, int my, int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        refilter();
        search.focused = true;
        Set<String> sel = ids();

        search.draw(g, x, y, LW, 16, "Search mobs...");
        for (int i = 0; i < FILTERS.length; i++) {
            int bx = fx(i);
            Ui.rr(g, bx, y + 20, FW[i], 14, i == filter ? Ui.SEL : Ui.in(mx, my, bx, y + 20, FW[i], 14) ? Ui.HOVER : Ui.BTN);
            Ui.center(g, FILTERS[i], bx, y + 23, FW[i], Ui.TEXT);
        }
        int rows = listRows();
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, view.size() - rows)));
        bar.draw(g, x + LW + 2, y + LIST, rows * ROWH - 1, view.size(), rows, listScroll, mx, my);
        for (int i = 0; i < rows && listScroll + i < view.size(); i++) {
            MobsPage.Entry e = view.get(listScroll + i);
            int ry = y + LIST + i * ROWH;
            boolean on = sel.contains(e.id());
            Ui.rr(g, x, ry, LW, ROWH - 1, on ? Ui.REDBTN : Ui.in(mx, my, x, ry, LW, ROWH - 1) ? Ui.HOVER : Ui.BTN);
            g.item(e.icon(), x + 1, ry);
            Ui.label(g, e.name(), x + 20, ry + 5, LW - 36, on ? 0xFFFFB0B0 : Ui.TEXT);
            stars.draw(g, e.id(), x + LW - Stars.W, ry, ROWH - 1, mx, my);
        }
        g.text(view.size() + " entities, click to add or remove", x + 2, y + h - 9, Ui.DIM);

        int rx = rx(), rw = rw(), hw = (rw - 4) / 2;
        Ui.rr(g, rx, y, rw, 20, Ui.in(mx, my, rx, y, rw, 20) ? Ui.HOVER : Ui.BTN);
        g.text("Kill Field", rx + 8, y + 6, Ui.TEXT);
        Ui.pill(g, rx + rw - 36, y + 4, Menu.KILL_ON.on);

        int cy = y + 24;
        g.text("Radius", rx, cy + 5, Ui.TEXT);
        Ui.button(g, "-", rx + 40, cy, 14, 16, Ui.in(mx, my, rx + 40, cy, 14, 16), false);
        Ui.center(g, String.valueOf(Menu.KILL_RADIUS.intValue()), rx + 54, cy + 4, 24, Ui.TEXT);
        Ui.button(g, "+", rx + 78, cy, 14, 16, Ui.in(mx, my, rx + 78, cy, 14, 16), false);
        for (int i = 0; i < 3; i++) {
            int bx = rx + 100 + i * 28;
            if (bx + 26 > rx + rw) break;
            Ui.selButton(g, String.valueOf(PRESETS[i]), bx, cy, 26, 16, Ui.in(mx, my, bx, cy, 26, 16), Menu.KILL_RADIUS.intValue() == PRESETS[i]);
        }

        chip(g, "Spare Named", Menu.KILL_NAMED.on, rx, y + 44, hw, mx, my);
        chip(g, "Spare Pets", Menu.KILL_PETS.on, rx + hw + 4, y + 44, hw, mx, my);
        Ui.button(g, "Kill Now", rx, y + 62, rw, 18, Ui.in(mx, my, rx, y + 62, rw, 18), true);

        g.text("Selected (" + sel.size() + ")", rx, y + 90, Ui.DIM);
        Ui.button(g, "All Hostile", rx + rw - 110, y + 85, 64, 16, Ui.in(mx, my, rx + rw - 110, y + 85, 64, 16), false);
        Ui.button(g, "Clear", rx + rw - 42, y + 85, 42, 16, Ui.in(mx, my, rx + rw - 42, y + 85, 42, 16), false);

        List<String> order = ordered();
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

    private static void chip(Gfx g, String label, boolean on, int cx, int cy, int cw, int mx, int my) {
        Ui.rr(g, cx, cy, cw, 16, Ui.in(mx, my, cx, cy, cw, 16) ? Ui.HOVER : Ui.BTN);
        Ui.label(g, label, cx + 4, cy + 4, cw - 38, Ui.TEXT);
        Ui.pill(g, cx + cw - 32, cy + 2, on);
    }

    private static void radius(float r) { Menu.KILL_RADIUS.value = Math.max(4, Math.min(64, r)); Menu.changed(); }

    @Override
    public boolean click(int mx, int my, int btn) {
        if (bar.press(mx, my)) { listScroll = bar.at(my); return true; }
        if (selBar.press(mx, my)) { selScroll = selBar.at(my); return true; }
        for (int i = 0; i < FILTERS.length; i++) if (Ui.in(mx, my, fx(i), y + 20, FW[i], 14)) { filter = i; return true; }
        Set<String> sel = ids();
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
        int rx = rx(), rw = rw(), hw = (rw - 4) / 2, cy = y + 24;
        if (Ui.in(mx, my, rx, y, rw, 20)) { Menu.KILL_ON.on = !Menu.KILL_ON.on; Menu.changed(); return true; }
        if (Ui.in(mx, my, rx + 40, cy, 14, 16)) { radius(Menu.KILL_RADIUS.value - (btn == 1 ? 8 : 4)); return true; }
        if (Ui.in(mx, my, rx + 78, cy, 14, 16)) { radius(Menu.KILL_RADIUS.value + (btn == 1 ? 8 : 4)); return true; }
        for (int i = 0; i < 3; i++) if (rx + 100 + i * 28 + 26 <= rx + rw && Ui.in(mx, my, rx + 100 + i * 28, cy, 26, 16)) { radius(PRESETS[i]); return true; }
        if (Ui.in(mx, my, rx, y + 44, hw, 16)) { Menu.KILL_NAMED.on = !Menu.KILL_NAMED.on; Menu.changed(); return true; }
        if (Ui.in(mx, my, rx + hw + 4, y + 44, hw, 16)) { Menu.KILL_PETS.on = !Menu.KILL_PETS.on; Menu.changed(); return true; }
        if (Ui.in(mx, my, rx, y + 62, rw, 18)) { Features.killPass(true); return true; }
        if (Ui.in(mx, my, rx + rw - 110, y + 85, 64, 16)) {
            for (MobsPage.Entry e : MobsPage.entries()) if (e.cat() == MobCategory.MONSTER) sel.add(e.id());
            store(sel);
            return true;
        }
        if (Ui.in(mx, my, rx + rw - 42, y + 85, 42, 16)) { sel.clear(); store(sel); return true; }
        List<String> order = ordered();
        int sr = selRows();
        for (int i = 0; i < sr && selScroll + i < order.size(); i++)
            if (Ui.in(mx, my, rx + rw - 15, y + SEL_Y + i * 16, 15, 15)) { sel.remove(order.get(selScroll + i)); store(sel); return true; }
        return false;
    }

    @Override
    public boolean drag(int mx, int my) {
        if (bar.dragging()) { listScroll = bar.at(my); return true; }
        if (selBar.dragging()) { selScroll = selBar.at(my); return true; }
        if (stars.active()) { refilter(); stars.drag(my, view.stream().map(MobsPage.Entry::id).toList(), y + LIST, ROWH, listScroll); return true; }
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
