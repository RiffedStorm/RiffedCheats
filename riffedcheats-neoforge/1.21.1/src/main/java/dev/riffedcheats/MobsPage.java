package dev.riffedcheats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Mob summoner, laid out like the item spawner: search + filter on the left, preview and options on the right. */
public final class MobsPage implements Page {
    private record Entry(String id, String name, String lower, ItemStack icon, MobCategory cat) {}

    private static final int LW = 172, ROWH = 18, LIST = 38;
    private static final String[] FILTERS = { "All", "Hostile", "Passive", "Other" };
    private static final int[] FW = { 28, 46, 46, 40 };

    private List<Entry> all;
    private List<Entry> view = List.of();
    private final TextField search = new TextField(), name = new TextField();
    private String lastQuery = null;
    private int filter, lastFilter = -1, listScroll, count = 1;
    private Entry selected, hovered;
    private boolean noAi, baby, glow, invul, persist, silent, front = true;
    private int x, y, w, h;

    public MobsPage() { name.max = 24; name.focused = false; }

    @Override public boolean wantsText() { return true; }

    private void init() {
        if (all != null) return;
        Map<String, Item> items = new HashMap<>();
        for (Item it : BuiltInRegistries.ITEM) items.put(BuiltInRegistries.ITEM.getKey(it).toString(), it);
        List<Entry> l = new ArrayList<>();
        for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(t).toString();
            if (id.equals("minecraft:player") || id.equals("minecraft:fishing_bobber")) continue; // cannot be summoned
            String n = t.getDescription().getString();
            Item icon = items.get(id + "_spawn_egg");
            if (icon == null) icon = items.get(id);          // tnt, boats, minecarts, armor stand...
            if (icon == null || icon == Items.AIR) icon = Items.SPAWNER;
            l.add(new Entry(id, n, (n + " " + id).toLowerCase(Locale.ROOT), new ItemStack(icon), t.getCategory()));
        }
        l.sort(Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT)));
        all = l;
    }

    private boolean pass(Entry e) {
        return switch (filter) {
            case 1 -> e.cat() == MobCategory.MONSTER;
            case 2 -> e.cat() != MobCategory.MONSTER && e.cat() != MobCategory.MISC;
            case 3 -> e.cat() == MobCategory.MISC;
            default -> true;
        };
    }

    private void refilter() {
        if (search.text.equals(lastQuery) && filter == lastFilter) return;
        lastQuery = search.text;
        lastFilter = filter;
        String[] q = search.text.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            if (!pass(e)) continue;
            boolean ok = true;
            for (String t : q) if (!t.isEmpty() && !e.lower().contains(t)) { ok = false; break; }
            if (ok) out.add(e);
        }
        view = out;
        listScroll = 0;
    }

    private int rx() { return x + LW + 8; }
    private int rw() { return w - LW - 8; }
    private int hw() { return (rw() - 4) / 2; }
    private static final int COUNT_Y = 86, T1 = 106, T2 = 123, T3 = 140, NAME_Y = 160, FRONT_Y = 180;
    private int listRows() { return Math.max(1, (h - LIST - 11) / ROWH); }
    private int fx(int i) { int fx = x; for (int k = 0; k < i; k++) fx += FW[k] + 4; return fx; }

    @Override
    public void render(Gfx g, int mx, int my, int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        init();
        refilter();

        // ---- left: search, filter, list ----
        search.draw(g, x, y, LW, 16, "Search mobs...");
        for (int i = 0; i < FILTERS.length; i++) {
            int bx = fx(i);
            Ui.rr(g, bx, y + 20, FW[i], 14, i == filter ? Ui.SEL : Ui.in(mx, my, bx, y + 20, FW[i], 14) ? Ui.HOVER : Ui.BTN);
            Ui.center(g, FILTERS[i], bx, y + 23, FW[i], Ui.TEXT);
        }
        int rows = listRows();
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, view.size() - rows)));
        hovered = null;
        for (int i = 0; i < rows && listScroll + i < view.size(); i++) {
            Entry e = view.get(listScroll + i);
            int ry = y + LIST + i * ROWH;
            boolean hov = Ui.in(mx, my, x, ry, LW, ROWH - 1);
            if (hov) hovered = e;
            Ui.rr(g, x, ry, LW, ROWH - 1, e == selected ? Ui.SEL : hov ? Ui.HOVER : Ui.BTN);
            g.item(e.icon(), x + 1, ry);
            g.text(Ui.fit(g, e.name(), LW - 24), x + 20, ry + 5, Ui.TEXT);
        }
        g.text(view.size() + " entities", x + 2, y + h - 9, Ui.DIM);

        // ---- right: preview ----
        int rx = rx(), rw = rw(), hw = hw();
        Ui.rr(g, rx, y, rw, 82, Ui.PANEL);
        Entry shown = hovered != null ? hovered : selected;
        if (shown == null) {
            Ui.center(g, "Hover or click a mob", rx, y + 36, rw, Ui.DIM);
        } else {
            g.itemScaled(shown.icon(), rx + (rw - 56) / 2f, y + 3, 3.5f);
            Ui.center(g, Ui.fit(g, shown.name(), rw - 8), rx, y + 62, rw, Ui.TEXT);
            Ui.center(g, Ui.fit(g, shown.id(), rw - 8), rx, y + 72, rw, Ui.DIM);
        }

        // ---- count ----
        int cy = y + COUNT_Y;
        g.text("Count", rx, cy + 5, Ui.TEXT);
        Ui.button(g, "-", rx + 40, cy, 14, 16, Ui.in(mx, my, rx + 40, cy, 14, 16), false);
        Ui.center(g, String.valueOf(count), rx + 54, cy + 4, 24, Ui.TEXT);
        Ui.button(g, "+", rx + 78, cy, 14, 16, Ui.in(mx, my, rx + 78, cy, 14, 16), false);
        int[] presets = { 1, 5, 10 };
        for (int i = 0; i < 3; i++) {
            int bx = rx + 100 + i * 34;
            Ui.button(g, String.valueOf(presets[i]), bx, cy, 30, 16, Ui.in(mx, my, bx, cy, 30, 16), false);
        }

        // ---- options (two columns) ----
        chip(g, "No AI", noAi, rx, y + T1, hw, mx, my);
        chip(g, "Baby", baby, rx + hw + 4, y + T1, hw, mx, my);
        chip(g, "Glowing", glow, rx, y + T2, hw, mx, my);
        chip(g, "Invincible", invul, rx + hw + 4, y + T2, hw, mx, my);
        chip(g, "No Despawn", persist, rx, y + T3, hw, mx, my);
        chip(g, "Silent", silent, rx + hw + 4, y + T3, hw, mx, my);
        name.draw(g, rx, y + NAME_Y, rw, 16, "Custom name (optional)");
        chip(g, front ? "Spawn: 3 blocks in front of me" : "Spawn: at my feet", front, rx, y + FRONT_Y, rw, mx, my);

        // ---- actions ----
        int gy = y + h - 40;
        String sum = selected == null ? "Select a mob" : "Summon x" + count;
        Ui.button(g, sum, rx, gy, rw, 20, Ui.in(mx, my, rx, gy, rw, 20), false);
        Ui.button(g, "Kill This Type (64m)", rx, y + h - 18, hw, 18, Ui.in(mx, my, rx, y + h - 18, hw, 18), true);
        Ui.button(g, "Kill All Mobs (64m)", rx + hw + 4, y + h - 18, hw, 18, Ui.in(mx, my, rx + hw + 4, y + h - 18, hw, 18), true);
    }

    private static void chip(Gfx g, String label, boolean on, int cx, int cy, int cw, int mx, int my) {
        Ui.rr(g, cx, cy, cw, 16, Ui.in(mx, my, cx, cy, cw, 16) ? Ui.HOVER : Ui.BTN);
        g.text(Ui.fit(g, label, cw - 38), cx + 4, cy + 4, Ui.TEXT);
        Ui.pill(g, cx + cw - 32, cy + 2, on);
    }

    @Override
    public boolean click(int mx, int my, int btn) {
        if (Ui.in(mx, my, x, y, LW, 16)) { search.focused = true; name.focused = false; return true; }
        int rx = rx(), rw = rw(), hw = hw();
        if (Ui.in(mx, my, rx, y + NAME_Y, rw, 16)) { name.focused = true; search.focused = false; return true; }

        for (int i = 0; i < FILTERS.length; i++) if (Ui.in(mx, my, fx(i), y + 20, FW[i], 14)) { filter = i; return true; }
        int rows = listRows();
        for (int i = 0; i < rows && listScroll + i < view.size(); i++)
            if (Ui.in(mx, my, x, y + LIST + i * ROWH, LW, ROWH - 1)) { selected = view.get(listScroll + i); return true; }

        int cy = y + COUNT_Y;
        if (Ui.in(mx, my, rx + 40, cy, 14, 16)) { count = Math.max(1, count - (btn == 1 ? 5 : 1)); return true; }
        if (Ui.in(mx, my, rx + 78, cy, 14, 16)) { count = Math.min(64, count + (btn == 1 ? 5 : 1)); return true; }
        int[] presets = { 1, 5, 10 };
        for (int i = 0; i < 3; i++) if (Ui.in(mx, my, rx + 100 + i * 34, cy, 30, 16)) { count = presets[i]; return true; }

        if (Ui.in(mx, my, rx, y + T1, hw, 16)) { noAi = !noAi; return true; }
        if (Ui.in(mx, my, rx + hw + 4, y + T1, hw, 16)) { baby = !baby; return true; }
        if (Ui.in(mx, my, rx, y + T2, hw, 16)) { glow = !glow; return true; }
        if (Ui.in(mx, my, rx + hw + 4, y + T2, hw, 16)) { invul = !invul; return true; }
        if (Ui.in(mx, my, rx, y + T3, hw, 16)) { persist = !persist; return true; }
        if (Ui.in(mx, my, rx + hw + 4, y + T3, hw, 16)) { silent = !silent; return true; }
        if (Ui.in(mx, my, rx, y + FRONT_Y, rw, 16)) { front = !front; return true; }

        if (Ui.in(mx, my, rx, y + h - 40, rw, 20)) { summon(); return true; }
        if (selected != null && Ui.in(mx, my, rx, y + h - 18, hw, 18)) { Cmd.run("kill @e[type=" + selected.id() + ",distance=..64]"); return true; }
        if (Ui.in(mx, my, rx + hw + 4, y + h - 18, hw, 18)) {
            Cmd.run("kill @e[type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,distance=..64]");
            return true;
        }
        return false;
    }

    private String nbt() {
        List<String> t = new ArrayList<>();
        if (noAi) t.add("NoAI:1b");
        if (baby) { t.add("IsBaby:1b"); t.add("Age:-24000"); }
        if (glow) t.add("Glowing:1b");
        if (invul) t.add("Invulnerable:1b");
        if (persist) t.add("PersistenceRequired:1b");
        if (silent) t.add("Silent:1b");
        String n = name.text.replaceAll("[\"'\\\\]", "").trim();
        if (!n.isEmpty()) { t.add(Compat.customName(n)); t.add("CustomNameVisible:1b"); }
        return t.isEmpty() ? "" : " {" + String.join(",", t) + "}";
    }

    private static String off(double v) { return v == 0 ? "~" : "~" + String.format(Locale.ROOT, "%.4f", v); }

    private void summon() {
        if (selected == null) return;
        String pre = front ? "execute at @s rotated ~ 0 positioned ^ ^ ^3 run " : "execute at @s run ";
        String nbt = nbt();
        for (int i = 0; i < count; i++) {
            // several mobs get spread on a small grid (this also keeps every queued command unique)
            double ox = count == 1 ? 0 : ((i % 5) - 2) * 0.7 + i * 0.0001, oz = count == 1 ? 0 : ((i / 5) % 5 - 2) * 0.7;
            Cmd.run(pre + "summon " + selected.id() + " " + off(ox) + " ~ " + off(oz) + nbt);
        }
    }

    @Override
    public boolean scroll(int mx, int my, double dy) {
        if (mx < x + LW + 4) listScroll -= (int) Math.signum(dy) * 3;
        return true;
    }

    @Override public boolean typed(char c) { return search.focused ? search.typed(c) : name.typed(c); }
    @Override public boolean key(int key, int scan, int mods) { return search.focused ? search.key(key, mods) : name.key(key, mods); }
}
