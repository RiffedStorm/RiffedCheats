package dev.riffedcheats.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Modern layout (Settings > Appearance > Layout): icon sidebar, cards, animated switches and sliders, global search.
 * Same design space and the same page area as the Classic layout, so every custom page (spawner, inventory...) fits unchanged.
 */
public final class ModernView implements MenuUi {
    private static final int DW = MenuView.DW, DH = MenuView.DH, R = 12;
    private static final int SBW = 116, BX = 126, BY = 58, BW = 382, BH = 285;
    private static final int RH = 18, GAP = 5, PAD = 10, CARD_GAP = 8;
    private static final int SEARCH_X = 340, SEARCH_W = 140, CLOSE_X = DW - 26, TOP = 10;

    private static int cat, tab;
    private final long opened = System.nanoTime();
    private final TextField search = new TextField();
    private boolean searchFocus;
    private float scroll, scrollTarget;
    private Setting dragging;
    private int dragX, dragW;
    private boolean barDrag;

    /** lw = room for the row label of a button row (kept on a following unlabeled row, so both rows line up). */
    private record Item(Setting s, int x, int y, int w, int h, Setting parent, int lw) {
        Item(Setting s, int x, int y, int w, int h, Setting parent) { this(s, x, y, w, h, parent, 0); }
    }
    private record Jump(int cat, int tab, String label) {}
    private record Card(String title, List<Item> items, List<Jump> jumps, int h) {}
    private record Hit(Item it, int x, int y) {}
    private record JumpHit(Jump j, int x, int y, int w, int h) {}
    private final List<Hit> hits = new ArrayList<>();
    private final List<JumpHit> jumpHits = new ArrayList<>();

    private static Menu.Tab cur() {
        Menu.Category c = Menu.CATEGORIES.get(cat);
        tab = Math.max(0, Math.min(tab, c.tabs().size() - 1));
        return c.tabs().get(tab);
    }

    private boolean searching() { return !search.text.isBlank(); }

    @Override public boolean capturesText() { return searchFocus || (Access.allowed && !searching() && cur().page() != null && cur().page().wantsText()); }

    private void go(int c, int t) {
        if (c == cat && t == tab && !searching()) { if (cur().page() != null) cur().page().reselect(); return; }
        cat = c; tab = t; scroll = scrollTarget = 0;
        search.text = ""; searchFocus = false;
        Anim.set("page", 0);
    }

    // ================================================================== layout
    /** Places a list of settings in a card of width w: switches, buttons and rules two per row, the rest full width. */
    private static Card card(String title, List<Setting> list, int w) {
        List<Item> out = new ArrayList<>();
        int y = title.isEmpty() ? PAD : 26, half = (w - 2 * PAD - 8) / 2;
        boolean left = false, lastLabel = false;
        Setting parent = null;      // the switch whose sub-options are being placed
        for (int i = 0; i < list.size(); i++) {
            Setting s = list.get(i);
            boolean kids = s.kind == Setting.Kind.TOGGLE && i + 1 < list.size() && list.get(i + 1).parent == s;
            boolean child = s.parent != null && s.parent == parent;
            boolean halfW = (s.kind == Setting.Kind.TOGGLE && !kids) || s.kind == Setting.Kind.BUTTON || s.kind == Setting.Kind.RULE;
            // a name that does not fit half the card gets the whole row instead of being cut
            int room = s.kind == Setting.Kind.TOGGLE ? 36 : s.kind == Setting.Kind.RULE ? 48 : 16;
            if (halfW && Gfx.textWidth(s.name) + room > half) halfW = false;
            if (halfW) {
                if (!left) { out.add(new Item(s, PAD, y, half, RH, null)); left = true; }
                else { out.add(new Item(s, PAD + half + 8, y, half, RH, null)); left = false; y += RH + GAP; }
                lastLabel = false;
            } else {
                if (left) { left = false; y += RH + GAP; }
                if (s.kind == Setting.Kind.LABEL) {
                    if (!lastLabel && !out.isEmpty()) y += 3;
                    out.add(new Item(s, PAD + 2, y, w - 2 * PAD - 2, 9, null));
                    y += 11;
                    lastLabel = true;
                } else {
                    int ind = child ? 14 : 0, lw = 0;
                    if (s.kind == Setting.Kind.BUTTONS) {
                        Setting prev = i > 0 ? list.get(i - 1) : null;
                        boolean follows = s.name.isEmpty() && prev != null && prev.kind == Setting.Kind.BUTTONS && !prev.name.isEmpty();
                        lw = !s.name.isEmpty() || follows ? 92 : 0;
                    }
                    out.add(new Item(s, PAD + ind, y, w - 2 * PAD - ind, RH, child ? parent : null, lw));
                    y += RH + GAP;
                    lastLabel = false;
                }
            }
            if (kids) parent = s;
            else if (!child) parent = null;
        }
        if (left) y += RH + GAP;
        return new Card(title, out, List.of(), y + (lastLabel ? PAD - 2 : PAD - GAP));
    }

    private static boolean matches(String hay, String[] words) {
        for (String w : words) if (!w.isEmpty() && !hay.contains(w)) return false;
        return true;
    }

    private List<Card> cards() {
        List<Card> out = new ArrayList<>();
        int w = BW - 8;
        if (!searching()) {
            Menu.Category c = Menu.CATEGORIES.get(cat);
            out.add(card(c.tabs().size() > 1 ? cur().name().toUpperCase(Locale.ROOT) : "", cur().settings(), w));
            return out;
        }
        String[] q = search.text.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<Jump> jumps = new ArrayList<>();
        int total = 0;
        for (int ci = 0; ci < Menu.CATEGORIES.size(); ci++) {
            Menu.Category c = Menu.CATEGORIES.get(ci);
            for (int ti = 0; ti < c.tabs().size(); ti++) {
                Menu.Tab t = c.tabs().get(ti);
                String where = (c.name() + " " + t.name()).toLowerCase(Locale.ROOT);
                if (matches(where, q)) jumps.add(new Jump(ci, ti, c.name() + "  ›  " + t.name()));
                List<Setting> found = new ArrayList<>();
                for (Setting s : t.settings()) {
                    if (s.kind == Setting.Kind.LABEL || total >= 120) continue;
                    String hay = (s.name + " " + (s.opts != null ? String.join(" ", s.opts) : "") + " " + where).toLowerCase(Locale.ROOT);
                    if (matches(hay, q)) { found.add(s); total++; }
                }
                if (!found.isEmpty()) out.add(card((c.name() + "  ›  " + t.name()).toUpperCase(Locale.ROOT), found, w));
            }
        }
        if (!jumps.isEmpty()) {
            int rows = (jumps.size() + 1) / 2;
            out.add(0, new Card("GO TO", List.of(), jumps, 26 + rows * (RH + GAP) + PAD - GAP));
        }
        return out;
    }

    // ================================================================== render
    @Override
    public void render(Gfx g, int mx, int my) {
        Updater.autoCheck();
        Anim.frame();
        float open = Anim.on() ? Anim.ease((System.nanoTime() - opened) / 2.2e8f) : 1f;
        Gfx.alpha = open;
        g.push();
        g.translate(0, (1 - open) * 10);

        if (Menu.UI_GLOW.on) Draw.glow(g, 0, 0, DW, DH, R, Theme.accent);
        Draw.round(g, 0, 0, DW, DH, R, Theme.bg);
        // sidebar: rounded on the left, square where it meets the content
        Draw.round(g, 0, 0, SBW + R, DH, R, Theme.side);
        g.fill(SBW, 0, SBW + R, DH, Theme.bg);
        g.fill(SBW, 10, SBW + 1, DH - 10, Theme.border);
        Draw.ring(g, 0, 0, DW, DH, R, Theme.border);

        sidebar(g, mx, my);
        header(g, mx, my);

        if (!Access.allowed) denied(g);
        else {
            float page = Anim.to("page", 1, 10);
            Gfx.alpha = open * page;
            g.push();
            g.translate(0, (1 - page) * 6);
            if (!searching() && cur().page() != null) cur().page().render(g, mx, my, BX, BY, BW, BH);
            else body(g, mx, my);
            g.pop();
        }
        g.pop();
        Gfx.alpha = 1f;
    }

    private void sidebar(Gfx g, int mx, int my) {
        g.push();
        g.translate(12, 12);
        g.scale(1.3f);
        g.bold("RiffedCheats", 0, 0, Theme.accent);
        g.pop();
        small(g, "Minecraft · " + Compat.buildName(), 12, 27, Theme.dim);

        int y0 = 44, step = 19;
        int n = Menu.CATEGORIES.size();
        float sel = Anim.to("side", searching() ? -5 : cat, 16);
        if (sel > -1) {
            int sy = Math.round(y0 + sel * step);
            Draw.round(g, 6, sy, SBW - 12, 17, 5, Theme.accentFaint);
            Draw.round(g, 6, sy + 3, 2, 11, 1, Theme.accent);
        }
        for (int i = 0; i < n; i++) {
            int ry = y0 + i * step;
            boolean on = i == cat && !searching();
            boolean hov = Access.allowed && Ui.in(mx, my, 6, ry, SBW - 12, 17);
            float h = Anim.to("sideh" + i, hov && !on ? 1 : 0, 18);
            if (h > 0) Draw.round(g, 6, ry, SBW - 12, 17, 5, Theme.alpha(Theme.raised, (int) (h * 120)));
            String name = Menu.CATEGORIES.get(i).name();
            int c = on ? Theme.accent : hov ? Theme.text : Theme.dim;
            g.push();
            g.translate(14, ry + 4);
            g.scale(0.9f);
            Draw.icon(g, Icons.of(name), 0, 0, c);
            g.pop();
            g.text(name, 30, ry + 5, on ? Theme.text : hov ? Theme.text : Theme.dim);
        }

        String b = badge();
        if (b != null) {
            boolean hov = Ui.in(mx, my, 8, DH - 52, SBW - 16, 14);
            Draw.round(g, 8, DH - 52, SBW - 16, 14, 4, hov ? Theme.accentSoft : Theme.accentFaint);
            small(g, b, 13, DH - 48, Updater.justUpdated() ? Ui.GREEN : Theme.accent);
        }
        small(g, "v" + Updater.current(), 12, DH - 30, Theme.dim);
        String acc = Access.label();
        boolean warn = Access.mode == Access.Mode.OP && ServerPage.feedbackOn();
        small(g, acc, 12, DH - 19, Access.server() ? Ui.GREEN : Access.allowed ? (warn ? Ui.RED : 0xFFE0C04C) : Ui.RED);
        if (warn) small(g, "Other OPs see commands", 12, DH - 10, Ui.RED);
    }

    private static String badge() {
        if (Updater.justUpdated()) return "Updated to " + Updater.UPDATED_TO;
        if (Updater.state == Updater.State.AVAILABLE) return "Update: " + Updater.latest;
        return null;
    }

    private static void small(Gfx g, String s, int x, int y, int c) {
        g.push();
        g.translate(x, y);
        g.scale(0.75f);
        int room = SBW - x - 4, tw = g.width(s);
        if (tw * 0.75f > room) { float k = Math.max(0.75f, room / (tw * 0.75f)); g.scale(k); s = Ui.fit(g, s, (int) (room / 0.75f / k)); }
        g.text(s, 0, 0, c);
        g.pop();
    }

    private void header(Gfx g, int mx, int my) {
        String title = searching() ? "SEARCH" : Menu.CATEGORIES.get(cat).name().toUpperCase(Locale.ROOT);
        g.push();
        g.translate(BX, TOP + 2);
        g.scale(1.45f);
        g.bold(title, 0, 0, Theme.text);
        g.pop();

        // search box
        boolean sh = Ui.in(mx, my, SEARCH_X, TOP, SEARCH_W, 16);
        Draw.round(g, SEARCH_X, TOP, SEARCH_W, 16, 8, searchFocus ? Theme.raised2 : sh ? Theme.raised2 : Theme.raised);
        if (searchFocus) Draw.ring(g, SEARCH_X, TOP, SEARCH_W, 16, 8, Theme.accentSoft);
        Draw.icon(g, Icons.SEARCH, SEARCH_X + 7, TOP + 4, Theme.dim);
        String s = search.text;
        while (s.length() > 1 && g.width(s) > SEARCH_W - 34) s = s.substring(1);
        if (s.isEmpty() && !searchFocus) g.text("Search features", SEARCH_X + 19, TOP + 4, Theme.dim);
        else g.text(s, SEARCH_X + 19, TOP + 4, Theme.text);
        if (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = SEARCH_X + 19 + g.width(s);
            g.fill(cx, TOP + 3, cx + 1, TOP + 13, Theme.accent);
        }
        if (searching()) {
            boolean xh = Ui.in(mx, my, SEARCH_X + SEARCH_W - 16, TOP, 16, 16);
            Draw.icon(g, Icons.CLOSE, SEARCH_X + SEARCH_W - 12, TOP + 5, xh ? Theme.text : Theme.dim);
        }

        boolean ch = Ui.in(mx, my, CLOSE_X, TOP, 16, 16);
        float c = Anim.to("close", ch ? 1 : 0, 18);
        if (c > 0) Draw.round(g, CLOSE_X, TOP, 16, 16, 8, Theme.alpha(0xE05C5C, (int) (c * 200)));
        Draw.icon(g, Icons.CLOSE, CLOSE_X + 5, TOP + 5, ch ? 0xFFFFFFFF : Theme.dim);

        if (searching() || !Access.allowed) return;
        Menu.Category cc = Menu.CATEGORIES.get(cat);
        if (cc.tabs().size() < 2) return;
        int x = BX, y = 34, total = 0;
        for (Menu.Tab t : cc.tabs()) total += g.width(t.name()) + 18;
        Draw.round(g, x, y, total + 4, 18, 9, Theme.raised);
        int tx = x + 2, selX = 0, selW = 0;
        for (int j = 0; j < cc.tabs().size(); j++) {
            int w = g.width(cc.tabs().get(j).name()) + 18;
            if (j == tab) { selX = tx; selW = w; }
            tx += w;
        }
        float ax = Anim.to("tabx", selX, 16), aw = Anim.to("tabw", selW, 16);
        Draw.round(g, Math.round(ax), y + 2, Math.round(aw), 14, 7, Theme.mix(Theme.raised, Theme.accent, 0.45f));
        tx = x + 2;
        for (int j = 0; j < cc.tabs().size(); j++) {
            String n = cc.tabs().get(j).name();
            int w = g.width(n) + 18;
            boolean hov = Ui.in(mx, my, tx, y, w, 18);
            g.text(n, tx + 9, y + 5, j == tab ? Theme.text : hov ? Theme.text : Theme.dim);
            tx += w;
        }
    }

    private void denied(Gfx g) {
        Draw.round(g, BX, BY, BW, 120, 8, Theme.card);
        int cy = BY + 20;
        if (Access.mode == Access.Mode.WAIT) { Ui.center(g, "Connecting to RiffedCheats on this server...", BX, cy, BW, Theme.text); return; }
        Ui.center(g, "Operator permissions required", BX, cy, BW, Ui.RED);
        cy += 20;
        String[] lines = {
            "You are not an operator on this server, so every function is disabled.",
            "Your settings are kept in memory and come back automatically when you are OP again, or in your own singleplayer / LAN world.",
            "Ask the server owner to run:  /op <your name>" };
        for (String l : lines)
            for (String w : Ui.wrap(g, l, BW - 30)) { Ui.center(g, w, BX, cy, BW, Theme.text); cy += 12; }
    }

    private void body(Gfx g, int mx, int my) {
        List<Card> cards = cards();
        int total = 0;
        for (Card c : cards) total += c.h() + CARD_GAP;
        total = Math.max(0, total - CARD_GAP);
        int max = Math.max(0, total - BH);
        scrollTarget = Math.max(0, Math.min(scrollTarget, max));
        scroll = Anim.to("scroll", scrollTarget, 18);
        scroll = Math.max(0, Math.min(scroll, max));
        boolean inBody = Ui.in(mx, my, BX - 4, BY, BW + 8, BH);
        int hmx = inBody ? mx : -10000, hmy = inBody ? my : -10000;

        hits.clear();
        jumpHits.clear();
        g.clip(BX - 4, BY, BX + BW + 4, BY + BH);
        int y = BY - Math.round(scroll);
        for (Card c : cards) {
            if (y + c.h() >= BY && y <= BY + BH) drawCard(g, c, BX, y, BW - 8, hmx, hmy);
            y += c.h() + CARD_GAP;
        }
        if (cards.isEmpty()) Ui.center(g, "Nothing found for \"" + search.text.trim() + "\"", BX, BY + 20, BW - 8, Theme.dim);
        g.unclip();
        if (max > 0) {
            int th = Math.max(20, BH * BH / Math.max(1, total)), ty = BY + Math.round((BH - th) * scroll / max);
            Draw.round(g, BX + BW - 3, BY, 3, BH, 1, Theme.alpha(Theme.raised, 0x80));
            Draw.round(g, BX + BW - 3, ty, 3, th, 1, barDrag ? Theme.accent : Theme.raised2);
        }
    }

    private void drawCard(Gfx g, Card c, int x, int y, int w, int mx, int my) {
        Draw.round(g, x, y, w, c.h(), 8, Theme.card);
        Draw.ring(g, x, y, w, c.h(), 8, Theme.border);
        if (!c.title().isEmpty()) {
            g.push();
            g.translate(x + PAD, y + 9);
            g.scale(0.8f);
            g.bold(c.title(), 0, 0, Theme.accent);
            g.pop();
        }
        int half = (w - 2 * PAD - 8) / 2;
        for (int i = 0; i < c.jumps().size(); i++) {
            Jump j = c.jumps().get(i);
            int jx = x + PAD + (i % 2) * (half + 8), jy = y + 26 + (i / 2) * (RH + GAP);
            boolean hov = Ui.in(mx, my, jx, jy, half, RH);
            Draw.round(g, jx, jy, half, RH, 5, hov ? Theme.raised2 : Theme.raised);
            if (hov) Draw.ring(g, jx, jy, half, RH, 5, Theme.accentSoft);
            g.text(Ui.fit(g, j.label(), half - 12), jx + 8, jy + 5, Theme.text);
            jumpHits.add(new JumpHit(j, jx, jy, half, RH));
        }
        for (Item it : c.items()) {
            int ax = x + it.x(), ay = y + it.y();
            if (ay + it.h() < BY - 2 || ay > BY + BH + 2) continue;
            hits.add(new Hit(it, ax, ay));
            float a0 = Gfx.alpha;
            if (it.parent() != null) {
                g.fill(ax - 8, ay - 3, ax - 7, ay + it.h() + 2, Theme.accentFaint);
                if (!it.parent().on) Gfx.alpha = a0 * 0.45f;
            }
            if (!it.s().usable()) Gfx.alpha = a0 * 0.4f;
            widget(g, it.s(), ax, ay, it.w(), it.h(), it.lw(), mx, my);
            Gfx.alpha = a0;
            if (!it.s().usable()) chip(g, "Server only", ax + it.w() - 4, ay + 3, Theme.raised2, Theme.dim);
        }
    }

    /** A small label chip; right-aligned at rx. */
    private static void chip(Gfx g, String s, int rx, int y, int bg, int fg) {
        int w = Math.round(Gfx.textWidth(s) * 0.75f) + 10;
        Draw.round(g, rx - w, y, w, 12, 6, bg);
        g.push();
        g.translate(rx - w + 5, y + 3);
        g.scale(0.75f);
        g.text(s, 0, 0, fg);
        g.pop();
    }

    private static String num(float v) {
        return String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void widget(Gfx g, Setting s, int x, int y, int w, int h, int rowLw, int mx, int my) {
        boolean hov = Ui.in(mx, my, x, y, w, h);
        String key = s.id() + "@" + System.identityHashCode(s);
        switch (s.kind) {
            case LABEL -> g.text(Ui.fit(g, s.name, w), x, y, Theme.dim);
            case TOGGLE -> {
                float hv = Anim.to("h" + key, hov ? 1 : 0, 20);
                if (hv > 0) Draw.round(g, x - 3, y, w + 6, h, 5, Theme.alpha(Theme.raised, (int) (hv * 150)));
                float k = Anim.to("t" + key, s.on ? 1 : 0, 16);
                int sx = x + 2, sy = y + 3;
                Draw.round(g, sx, sy, 22, 12, 6, Theme.mix(Theme.raised2, Theme.accent, k));
                Draw.circle(g, sx + 2 + Math.round(k * 10), sy + 2, 8, s.on ? 0xFFFFFFFF : Theme.mix(Theme.dim, Theme.text, k));
                Ui.label(g, s.name, sx + 29, y + 5, w - 33, s.on ? Theme.text : Theme.mix(Theme.dim, Theme.text, 0.6f));
            }
            case RULE -> {
                float hv = Anim.to("h" + key, hov ? 1 : 0, 20);
                if (hv > 0) Draw.round(g, x - 3, y, w + 6, h, 5, Theme.alpha(Theme.raised, (int) (hv * 150)));
                Ui.label(g, s.name, x + 2, y + 5, w - 44, Theme.text);
                Boolean v = Rules.get(s.opts[0]);
                int c = v == null ? Theme.raised2 : v ? 0xFF2E8F5A : 0xFF8F3040;
                Draw.round(g, x + w - 36, y + 3, 34, 12, 6, c);
                String t = v == null ? "..." : v ? "ON" : "OFF";
                g.push();
                g.translate(x + w - 19 - Gfx.textWidth(t) * 0.75f / 2f, y + 6);
                g.scale(0.75f);
                g.text(t, 0, 0, 0xFFFFFFFF);
                g.pop();
            }
            case BUTTON -> button(g, s.name, x, y, w, h, hov, s.danger, key);
            case BUTTONS -> {
                int lw = rowLw;
                if (!s.name.isEmpty()) Ui.label(g, s.name, x + 2, y + 5, lw - 6, Theme.dim);
                int n = s.opts.length, bw = (w - lw - (n - 1) * 4) / n;
                for (int i = 0; i < n; i++) {
                    int bx = x + lw + i * (bw + 4);
                    button(g, s.opts[i], bx, y, bw, h, Ui.in(mx, my, bx, y, bw, h), false, key + i);
                }
            }
            case CHOICE -> {
                int lw = 92;
                Ui.label(g, s.name, x + 2, y + 5, lw - 6, Theme.text);
                int cx = x + lw, cw = w - lw, n = s.opts.length, ow = cw / n;
                Draw.round(g, cx, y, cw, h, 6, Theme.raised);
                float ix = Anim.to("c" + key, s.intValue(), 16);
                Draw.round(g, cx + Math.round(ix * ow) + 2, y + 2, ow - 4, h - 4, 5, Theme.mix(Theme.raised, Theme.accent, 0.5f));
                for (int i = 0; i < n; i++) {
                    boolean oh = Ui.in(mx, my, cx + i * ow, y, ow, h);
                    centered(g, s.opts[i], cx + i * ow, y + 5, ow - 4, i == s.intValue() || oh ? Theme.text : Theme.dim);
                }
            }
            case SLIDER -> {
                int tw = w - 16;
                float frac = (s.value - s.min) / (s.max - s.min);
                float f = Anim.to("s" + key, frac, dragging == s ? 40 : 14);
                Draw.round(g, x, y, tw, h, 9, Ui.in(mx, my, x, y, tw, h) || dragging == s ? Theme.raised2 : Theme.raised);
                int fw = Math.max(h, Math.round(f * tw));
                Draw.round(g, x, y, fw, h, 9, Theme.accentSoft);
                Draw.round(g, x + fw - 8, y + 4, 3, h - 8, 1, 0xFFFFFFFF);
                String t = Ui.fit(g, s.name, tw - 80) + "   " + num(s.value);
                g.text(t, x + (tw - g.width(t)) / 2, y + 5, Theme.text);
                if (s.value != s.def) {
                    boolean rh = Ui.in(mx, my, x + tw, y, 16, h);
                    g.text("\u21BA", x + tw + 5, y + 5, rh ? Theme.accent : Theme.dim);
                }
            }
            default -> {}
        }
    }

    /** Centered text that shrinks (down to 70%) to fit w. */
    private static void centered(Gfx g, String s, int x, int y, int w, int c) {
        int tw = g.width(s);
        if (tw <= w) { g.text(s, x + (w - tw) / 2 + 2, y, c); return; }
        float sc = Math.max(0.7f, (float) w / tw);
        String t = Ui.fit(g, s, (int) (w / sc));
        g.push();
        g.translate(x + 2 + (w - g.width(t) * sc) / 2f, y + (1 - sc) * 4);
        g.scale(sc);
        g.text(t, 0, 0, c);
        g.pop();
    }

    private static void button(Gfx g, String label, int x, int y, int w, int h, boolean hov, boolean danger, String key) {
        float hv = Anim.to("b" + key, hov ? 1 : 0, 20);
        int base = danger ? 0xFF4E2329 : Theme.raised, top = danger ? 0xFF7A2E3A : Theme.raised2;
        Draw.round(g, x, y, w, h, 6, Theme.mix(base, top, hv));
        if (hv > 0.01f) Draw.ring(g, x, y, w, h, 6, Theme.alpha(danger ? 0xF0605C : Theme.accent, (int) (hv * 0x70)));
        centered(g, label, x + 3, y + 5, w - 10, Theme.text);
    }

    // ================================================================== input
    @Override
    public boolean click(int mx, int my, int btn) {
        if (Ui.in(mx, my, CLOSE_X, TOP, 16, 16)) return true;
        if (searching() && Ui.in(mx, my, SEARCH_X + SEARCH_W - 16, TOP, 16, 16)) { search.text = ""; scroll = scrollTarget = 0; return false; }
        searchFocus = Ui.in(mx, my, SEARCH_X, TOP, SEARCH_W, 16);
        if (searchFocus || !Access.allowed) return false;

        if (badge() != null && Ui.in(mx, my, 8, DH - 52, SBW - 16, 14)) {
            for (int i = 0; i < Menu.CATEGORIES.size(); i++) {
                List<Menu.Tab> tabs = Menu.CATEGORIES.get(i).tabs();
                for (int j = 0; j < tabs.size(); j++) if (tabs.get(j).page() instanceof UpdatesPage) go(i, j);
            }
            return false;
        }
        for (int i = 0; i < Menu.CATEGORIES.size(); i++)
            if (Ui.in(mx, my, 6, 44 + i * 19, SBW - 12, 17)) { go(i, 0); return false; }

        if (!searching()) {
            Menu.Category cc = Menu.CATEGORIES.get(cat);
            if (cc.tabs().size() > 1) {
                int tx = BX + 2;
                for (int j = 0; j < cc.tabs().size(); j++) {
                    int w = Gfx.textWidth(cc.tabs().get(j).name()) + 18;
                    if (Ui.in(mx, my, tx, 34, w, 18)) { go(cat, j); return false; }
                    tx += w;
                }
            }
            if (cur().page() != null) { cur().page().click(mx, my, btn); return false; }
        }

        if (!Ui.in(mx, my, BX - 4, BY, BW + 8, BH)) return false;
        if (mx >= BX + BW - 5) { barDrag = true; dragBar(my); return false; }
        for (JumpHit j : jumpHits)
            if (Ui.in(mx, my, j.x(), j.y(), j.w(), j.h())) { go(j.j().cat(), j.j().tab()); return false; }
        for (Hit h : hits) {
            Item it = h.it();
            Setting s = it.s();
            int x = h.x(), y = h.y(), w = it.w();
            if (!Ui.in(mx, my, x, y, w, it.h()) || !s.usable()) continue;
            switch (s.kind) {
                case TOGGLE -> { s.on = !s.on; Menu.changed(); }
                case BUTTON -> s.action.run();
                case RULE -> Rules.flip(s.opts[0]);
                case BUTTONS -> {
                    int lw = it.lw(), n = s.opts.length, bw = (w - lw - (n - 1) * 4) / n;
                    for (int i = 0; i < n; i++) if (Ui.in(mx, my, x + lw + i * (bw + 4), y, bw, it.h())) { s.acts[i].run(); break; }
                }
                case CHOICE -> {
                    int cx = x + 92, ow = (w - 92) / s.opts.length;
                    if (mx >= cx) { s.value = Math.max(0, Math.min(s.opts.length - 1, (mx - cx) / ow)); Menu.changed(); }
                }
                case SLIDER -> {
                    if (mx >= x + w - 16) { if (s.value != s.def) { s.value = s.def; Menu.changed(); } }
                    else { dragging = s; dragX = x; dragW = w - 16; setSlider(mx); }
                }
                default -> {}
            }
            return false;
        }
        return false;
    }

    private void setSlider(int mx) {
        Setting s = dragging;
        double f = Math.max(0, Math.min(1, (mx - dragX) / (double) dragW));
        double v = s.min + f * (s.max - s.min);
        v = Math.round(v / s.step) * s.step;
        s.value = (float) Math.max(s.min, Math.min(s.max, v));
    }

    private void dragBar(int my) {
        int total = 0;
        for (Card c : cards()) total += c.h() + CARD_GAP;
        int max = Math.max(0, total - CARD_GAP - BH);
        scrollTarget = Math.max(0, Math.min(max, (my - BY) / (float) BH * (max + BH) - BH / 2f));
        scroll = scrollTarget;
        Anim.set("scroll", scroll);
    }

    @Override
    public void drag(int mx, int my) {
        if (dragging != null) setSlider(mx);
        else if (barDrag) dragBar(my);
        else if (Access.allowed && !searching() && cur().page() != null) cur().page().drag(mx, my);
    }

    @Override
    public void release() {
        if (dragging != null) { dragging = null; Menu.changed(); }
        barDrag = false;
        if (Access.allowed && !searching() && cur().page() != null) cur().page().release();
    }

    @Override
    public void scroll(int mx, int my, double dy) {
        if (!Access.allowed) return;
        if (!searching() && cur().page() != null) { cur().page().scroll(mx, my, dy); return; }
        scrollTarget -= (float) (Math.signum(dy) * 34);
    }

    @Override
    public boolean key(int key, int scan, int mods) {
        if (key == 256 && (searchFocus || searching())) { search.text = ""; searchFocus = false; scroll = scrollTarget = 0; return true; }
        if (searchFocus) {
            if (key == 257 || key == 335) { searchFocus = false; return true; }
            if (search.key(key, mods)) { scrollTarget = 0; return true; }
            return false;
        }
        if (key == 70 && (mods & 2) != 0) { searchFocus = true; return true; }      // Ctrl+F
        if (!Access.allowed || searching()) return false;
        Menu.Tab t = cur();
        return t.page() != null && t.page().key(key, scan, mods);
    }

    @Override
    public boolean typed(char c) {
        if (searchFocus) { search.focused = true; boolean r = search.typed(c); scrollTarget = 0; return r; }
        if (!Access.allowed || searching()) return false;
        Menu.Tab t = cur();
        return t.page() != null && t.page().typed(c);
    }
}
