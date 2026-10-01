package dev.riffedcheats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Item spawner: search, icon previews on hover (Menyoo style), enchant editor, count, infinite durability. */
public final class ItemsPage implements Page {
    private record Entry(ItemStack stack, String name, String id, String lower) {}

    private static final int LW = 172, ROWH = 18;

    private List<Entry> all;
    private List<Entry> view = List.of();
    private final TextField search = new TextField();
    private String lastQuery = null;
    private int listScroll, enchScroll;
    private Entry selected, hovered;
    private final Map<String, Integer> ench = new LinkedHashMap<>();
    private List<String> enchIds = List.of();
    private int count = 1;
    private boolean showAll;
    private int x, y, w, h;

    @Override public boolean wantsText() { return true; }

    private void init() {
        if (all != null) return;
        List<Entry> l = new ArrayList<>();
        for (Item it : BuiltInRegistries.ITEM) {
            if (it == Items.AIR) continue;
            ItemStack st = new ItemStack(it);
            String name = st.getHoverName().getString();
            String id = BuiltInRegistries.ITEM.getKey(it).toString();
            l.add(new Entry(st, name, id, (name + " " + id).toLowerCase(Locale.ROOT)));
        }
        l.sort(Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT)));
        all = l;
    }

    private void filter() {
        if (search.text.equals(lastQuery)) return;
        lastQuery = search.text;
        String[] q = search.text.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            boolean ok = true;
            for (String t : q) if (!t.isEmpty() && !e.lower().contains(t)) { ok = false; break; }
            if (ok) out.add(e);
        }
        view = out;
        listScroll = 0;
    }

    private void select(Entry e) {
        selected = e;
        ench.clear();
        enchScroll = 0;
        rebuildEnch();
    }

    private void rebuildEnch() {
        List<String> ids = new ArrayList<>();
        if (selected != null) {
            for (String id : Compat.enchantIds())
                if (showAll || Compat.enchantFits(id, selected.stack())) ids.add(id);
        }
        enchIds = ids;
    }

    private static String pretty(String id) {
        String p = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String s : p.split(" ")) if (!s.isEmpty()) sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).append(' ');
        return sb.toString().trim();
    }

    // geometry of the right column
    private int rx() { return x + LW + 8; }
    private int rw() { return w - LW - 8; }
    private static final int COUNT_Y = 86, TOG1 = 106, TOG2 = 123, HDR = 141, LIST = 158;
    private int enchRows() { return Math.max(1, (h - 44 - LIST) / 16); }
    private int listRows() { return Math.max(1, (h - 22 - 11) / ROWH); }

    @Override
    public void render(Gfx g, int mx, int my, int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        init();
        filter();
        search.focused = true;

        // ---- left: search + list ----
        search.draw(g, x, y, LW, 16, "Search items...");
        int rows = listRows();
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, view.size() - rows)));
        hovered = null;
        for (int i = 0; i < rows && listScroll + i < view.size(); i++) {
            Entry e = view.get(listScroll + i);
            int ry = y + 20 + i * ROWH;
            boolean hov = Ui.in(mx, my, x, ry, LW, ROWH - 1);
            if (hov) hovered = e;
            Ui.rr(g, x, ry, LW, ROWH - 1, e == selected ? Ui.SEL : hov ? Ui.HOVER : Ui.BTN);
            g.item(e.stack(), x + 1, ry);
            g.text(Ui.fit(g, e.name(), LW - 24), x + 20, ry + 5, Ui.TEXT);
        }
        g.text(view.size() + " items", x + 2, y + h - 9, Ui.DIM);

        // ---- right: preview ----
        int rx = rx(), rw = rw();
        Ui.rr(g, rx, y, rw, 82, Ui.PANEL);
        Entry shown = hovered != null ? hovered : selected;
        if (shown == null) {
            Ui.center(g, "Hover or click an item", rx, y + 36, rw, Ui.DIM);
        } else {
            g.itemScaled(shown.stack(), rx + (rw - 56) / 2f, y + 3, 3.5f);
            Ui.center(g, Ui.fit(g, shown.name(), rw - 8), rx, y + 62, rw, Ui.TEXT);
            Ui.center(g, Ui.fit(g, shown.id(), rw - 8), rx, y + 72, rw, Ui.DIM);
        }

        // ---- count ----
        int cy = y + COUNT_Y;
        g.text("Count", rx, cy + 5, Ui.TEXT);
        Ui.button(g, "-", rx + 40, cy, 14, 16, Ui.in(mx, my, rx + 40, cy, 14, 16), false);
        Ui.center(g, String.valueOf(count), rx + 54, cy + 4, 24, Ui.TEXT);
        Ui.button(g, "+", rx + 78, cy, 14, 16, Ui.in(mx, my, rx + 78, cy, 14, 16), false);
        int[] presets = { 1, 16, 64 };
        for (int i = 0; i < 3; i++) {
            int bx = rx + 100 + i * 34;
            Ui.button(g, String.valueOf(presets[i]), bx, cy, 30, 16, Ui.in(mx, my, bx, cy, 30, 16), false);
        }

        // ---- toggles (full width so the labels fit) ----
        chip(g, "Infinite Durability", Menu.UNBREAKABLE.on, rx, y + TOG1, rw, Ui.in(mx, my, rx, y + TOG1, rw, 16));
        chip(g, "Over-Max Enchant Levels", Menu.OVERLEVEL.on, rx, y + TOG2, rw, Ui.in(mx, my, rx, y + TOG2, rw, 16));

        // ---- enchant list ----
        int hy = y + HDR;
        g.text("Enchantments", rx, hy + 4, Ui.DIM);
        chip(g, "Show All", showAll, rx + rw - 90, hy - 1, 90, Ui.in(mx, my, rx + rw - 90, hy - 1, 90, 16));
        int er = enchRows();
        enchScroll = Math.max(0, Math.min(enchScroll, Math.max(0, enchIds.size() - er)));
        if (selected == null) g.text("Select an item first.", rx, y + LIST + 2, Ui.DIM);
        else if (enchIds.isEmpty()) g.text("No compatible enchantments.", rx, y + LIST + 2, Ui.DIM);
        for (int i = 0; i < er && enchScroll + i < enchIds.size(); i++) {
            String id = enchIds.get(enchScroll + i);
            int ry = y + LIST + i * 16;
            int lv = ench.getOrDefault(id, 0);
            Ui.rr(g, rx, ry, rw, 15, lv > 0 ? Ui.SEL : Ui.BTN);
            g.text(Ui.fit(g, pretty(id), rw - 88), rx + 4, ry + 4, Ui.TEXT);
            Ui.button(g, "-", rx + rw - 80, ry, 14, 15, Ui.in(mx, my, rx + rw - 80, ry, 14, 15), false);
            Ui.center(g, String.valueOf(lv), rx + rw - 66, ry + 4, 22, Ui.TEXT);
            Ui.button(g, "+", rx + rw - 44, ry, 14, 15, Ui.in(mx, my, rx + rw - 44, ry, 14, 15), false);
            Ui.button(g, "Max", rx + rw - 28, ry, 28, 15, Ui.in(mx, my, rx + rw - 28, ry, 28, 15), false);
        }

        // ---- action buttons ----
        int gy = y + h - 40;
        String give = selected == null ? "Select an item" : "Give x" + count + " to me";
        Ui.button(g, give, rx, gy, rw, 20, Ui.in(mx, my, rx, gy, rw, 20), false);
        int hw = (rw - 4) / 2;
        Ui.button(g, "Max All", rx, y + h - 18, hw, 18, Ui.in(mx, my, rx, y + h - 18, hw, 18), false);
        Ui.button(g, "Clear Enchants", rx + hw + 4, y + h - 18, hw, 18, Ui.in(mx, my, rx + hw + 4, y + h - 18, hw, 18), false);
    }

    private void chip(Gfx g, String label, boolean on, int cx, int cy, int cw, boolean hov) {
        Ui.rr(g, cx, cy, cw, 16, hov ? Ui.HOVER : Ui.BTN);
        g.text(Ui.fit(g, label, cw - 40), cx + 4, cy + 4, Ui.TEXT);
        Ui.pill(g, cx + cw - 32, cy + 2, on);
    }

    @Override
    public boolean click(int mx, int my, int btn) {
        int rows = listRows();
        for (int i = 0; i < rows && listScroll + i < view.size(); i++)
            if (Ui.in(mx, my, x, y + 20 + i * ROWH, LW, ROWH - 1)) { select(view.get(listScroll + i)); return true; }

        int rx = rx(), rw = rw(), cy = y + COUNT_Y;
        if (Ui.in(mx, my, rx + 40, cy, 14, 16)) { count = Math.max(1, count - (btn == 1 ? 8 : 1)); return true; }
        if (Ui.in(mx, my, rx + 78, cy, 14, 16)) { count = Math.min(64, count + (btn == 1 ? 8 : 1)); return true; }
        int[] presets = { 1, 16, 64 };
        for (int i = 0; i < 3; i++) if (Ui.in(mx, my, rx + 100 + i * 34, cy, 30, 16)) { count = presets[i]; return true; }

        if (Ui.in(mx, my, rx, y + TOG1, rw, 16)) { Menu.UNBREAKABLE.on = !Menu.UNBREAKABLE.on; Menu.changed(); return true; }
        if (Ui.in(mx, my, rx, y + TOG2, rw, 16)) { Menu.OVERLEVEL.on = !Menu.OVERLEVEL.on; Menu.changed(); return true; }
        if (Ui.in(mx, my, rx + rw - 90, y + HDR - 1, 90, 16)) { showAll = !showAll; rebuildEnch(); return true; }

        int er = enchRows();
        for (int i = 0; i < er && enchScroll + i < enchIds.size(); i++) {
            String id = enchIds.get(enchScroll + i);
            int ry = y + LIST + i * 16;
            int lv = ench.getOrDefault(id, 0);
            int cap = Menu.OVERLEVEL.on ? 255 : Compat.enchantMax(id);
            if (Ui.in(mx, my, rx + rw - 80, ry, 14, 15)) { set(id, Math.max(0, lv - (btn == 1 && Menu.OVERLEVEL.on ? 10 : 1))); return true; }
            if (Ui.in(mx, my, rx + rw - 44, ry, 14, 15)) { set(id, Math.min(cap, lv + (btn == 1 && Menu.OVERLEVEL.on ? 10 : 1))); return true; }
            if (Ui.in(mx, my, rx + rw - 28, ry, 28, 15)) { set(id, Compat.enchantMax(id)); return true; }
        }

        if (Ui.in(mx, my, rx, y + h - 40, rw, 20)) { give(); return true; }
        int hw = (rw - 4) / 2;
        if (Ui.in(mx, my, rx, y + h - 18, hw, 18)) { for (String id : enchIds) ench.put(id, Compat.enchantMax(id)); return true; }
        if (Ui.in(mx, my, rx + hw + 4, y + h - 18, hw, 18)) { ench.clear(); return true; }
        return false;
    }

    private void set(String id, int lv) { if (lv <= 0) ench.remove(id); else ench.put(id, lv); }

    private void give() {
        if (selected == null) return;
        StringBuilder csv = new StringBuilder();
        for (Map.Entry<String, Integer> e : ench.entrySet()) {
            if (e.getValue() <= 0) continue;
            String id = e.getKey();
            String key = id.startsWith("minecraft:") ? id.substring(10) : "\"" + id + "\"";
            if (csv.length() > 0) csv.append(',');
            csv.append(key).append(':').append(e.getValue());
        }
        List<String> comps = new ArrayList<>();
        if (csv.length() > 0) comps.add(selected.stack().is(Items.ENCHANTED_BOOK) ? Compat.enchBook(csv.toString()) : Compat.ench(csv.toString()));
        if (Menu.UNBREAKABLE.on && selected.stack().isDamageableItem()) comps.add("unbreakable={}");
        String cmd = "give @s " + selected.id() + (comps.isEmpty() ? "" : "[" + String.join(",", comps) + "]") + " " + count;
        Cmd.run(cmd);
    }

    @Override
    public boolean scroll(int mx, int my, double dy) {
        int d = (int) -Math.signum(dy);
        if (mx < x + LW + 4) listScroll += d * 3;
        else enchScroll += d;
        return true;
    }

    @Override public boolean typed(char c) { return search.typed(c); }
    @Override public boolean key(int key, int scan, int mods) { return search.key(key, mods); }
}
