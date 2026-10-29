package dev.riffedcheats.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Villager / wandering trader summoner with custom trades. Left: live 3D preview + item picker / enchantment editor for
 * the selected box. Right: job, biome, level, options,
 * the trade list and an editor for the selected trade (Cost + Cost 2 = Result). Without custom trades the
 * villager gets the normal ones for its job. Long data is sent as several commands, so it works on servers too (OP).
 */
public final class VillagerPage implements Page {
    /** One trade. b may be empty; custom* = item with extra data (enchantments, name... from the hand or the enchant editor). */
    private static final class Trade {
        ItemStack a = new ItemStack(Items.EMERALD), b = ItemStack.EMPTY, s = ItemStack.EMPTY;
        boolean customA, customB, customS;
        int uses = USES.length - 1;
    }

    private static final int LW = 150, ROWH = 16, TR_H = 18, TR_ROWS = 4, PV = 96, TABS = PV + 4, SEARCH = PV + 22, LIST = PV + 42;
    private static final int R_JOB = 18, R_LVL = 38, R_OPT1 = 58, R_OPT2 = 76, R_NAME = 96, R_TR = 116, R_LIST = 132, R_ED = 212, R_ED2 = 234;
    private static final int[] USES = { 1, 4, 12, 16, 32, 64, Integer.MAX_VALUE };
    private static final String[] LEVELS = { "Novice", "Apprentice", "Journeyman", "Expert", "Master" };
    private static final String[][] WORKSTATION = {
        { "armorer", "blast_furnace" }, { "butcher", "smoker" }, { "cartographer", "cartography_table" },
        { "cleric", "brewing_stand" }, { "farmer", "composter" }, { "fisherman", "barrel" },
        { "fletcher", "fletching_table" }, { "leatherworker", "cauldron" }, { "librarian", "lectern" },
        { "mason", "stonecutter" }, { "shepherd", "loom" }, { "toolsmith", "smithing_table" }, { "weaponsmith", "grindstone" } };
    private static final String TAG = "rc_villager", SELF = "@e[tag=" + TAG + ",limit=1]";
    private static final int MAX_CMD = 255;
    /** Villagers whose normal trades are kept at unlimited uses (see Features.keepTradesUnlimited). */
    static final String UNLIMITED_TAG = "rc_unlimited";
    /** Sets every trade of the tagged villagers near you to unlimited uses; does nothing (no message) before they have trades. */
    static final String UNLIMITED_CMD = "execute as @e[tag=" + UNLIMITED_TAG + ",distance=..96] if data entity @s Offers.Recipes[0]"
            + " run data modify entity @s Offers.Recipes[].maxUses set value 2147483647";
    private static final int UNL_W = 100;   // vanilla's command length limit on servers

    private final TextField search = new TextField(), name = new TextField();
    private String lastQuery;
    private List<ItemsPage.Entry> view = List.of();
    private List<String> jobs, biomes;
    private int listScroll, enchScroll, trScroll, job, biome, level = 5;
    private boolean trader, noAi, invul, silent, fixed = true;
    private boolean enchMode, showAll;       // left column shows the enchantment editor instead of the item list
    private final List<Trade> trades = new ArrayList<>();
    private Trade cur;
    private int box = 2;                     // box being edited: 0 = Cost, 1 = Cost 2, 2 = Result
    private String note;
    private long noteUntil;
    private int x, y, w, h;
    private final Scrollbar bar = new Scrollbar(), trBar = new Scrollbar();
    private net.minecraft.world.entity.LivingEntity preview;   // client-only model shown in the preview box
    private String previewKey;
    private Object previewLevel;

    public VillagerPage() { name.max = 24; name.focused = false; }

    @Override public boolean wantsText() { return true; }

    private void init() {
        if (jobs != null) return;
        jobs = new ArrayList<>();
        for (var k : BuiltInRegistries.VILLAGER_PROFESSION.keySet()) {
            String id = k.toString();
            if (!id.equals("minecraft:none") && !id.equals("minecraft:nitwit")) jobs.add(id);   // those two cannot trade
        }
        jobs.sort(null);
        biomes = new ArrayList<>();
        for (var k : BuiltInRegistries.VILLAGER_TYPE.keySet()) biomes.add(k.toString());
        biomes.sort(null);
        job = Math.max(0, jobs.indexOf("minecraft:librarian"));
        biome = Math.max(0, biomes.indexOf("minecraft:plains"));
    }

    private void refilter() {
        if (search.text.equals(lastQuery)) return;
        lastQuery = search.text;
        String[] q = search.text.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<ItemsPage.Entry> out = new ArrayList<>();
        for (ItemsPage.Entry e : ItemsPage.items()) {
            boolean ok = true;
            for (String t : q) if (!t.isEmpty() && !e.lower().contains(t)) { ok = false; break; }
            if (ok) out.add(e);
        }
        view = out;
        listScroll = 0;
    }

    private static String pretty(String id) {
        String p = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String s : p.split(" ")) if (!s.isEmpty()) sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).append(' ');
        return sb.toString().trim();
    }

    private static ItemStack jobIcon(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        for (String[] w : WORKSTATION) if (w[0].equals(path)) {
            for (ItemsPage.Entry e : ItemsPage.items()) if (e.id().equals("minecraft:" + w[1])) return e.stack();
        }
        return new ItemStack(Items.EMERALD);
    }

    private void note(String s) { note = s; noteUntil = System.currentTimeMillis() + 4000; }

    private int rx() { return x + LW + 8; }
    private int rw() { return w - LW - 8; }
    private int hw() { return (rw() - 4) / 2; }
    private int listRows() { return Math.max(1, (h - LIST - 12) / ROWH); }
    private int boxX(int i) { return rx() + i * 30; }
    private int jobW() { return rw() - 94; }             // job selector; the biome one gets the rest

    private ItemStack get(Trade t, int i) { return i == 0 ? t.a : i == 1 ? t.b : t.s; }
    private void set(Trade t, int i, ItemStack st, boolean custom) {
        switch (i) {
            case 0 -> { t.a = st; t.customA = custom; }
            case 1 -> { t.b = st; t.customB = custom; }
            default -> { t.s = st; t.customS = custom; }
        }
    }

    private Trade addTrade() {
        Trade t = new Trade();
        trades.add(t);
        cur = t;
        box = 2;
        trScroll = Math.max(0, trades.size() - TR_ROWS);
        return t;
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(Gfx g, int mx, int my, int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        init();
        refilter();

        // ---- left: preview + item picker / enchantments ----
        drawPreview(g, mx, my);
        int tw = (LW - 4) / 2;
        Ui.selButton(g, "Items", x, y + TABS, tw, 14, Ui.in(mx, my, x, y + TABS, tw, 14), !enchMode);
        Ui.selButton(g, "Enchants", x + tw + 4, y + TABS, tw, 14, Ui.in(mx, my, x + tw + 4, y + TABS, tw, 14), enchMode);
        if (enchMode) drawEnchants(g, mx, my);
        else drawItems(g, mx, my);

        // ---- right: villager ----
        int rx = rx(), rw = rw(), hw = hw();
        Ui.selButton(g, "Villager", rx, y, hw, 14, Ui.in(mx, my, rx, y, hw, 14), !trader);
        Ui.selButton(g, "Wandering Trader", rx + hw + 4, y, hw, 14, Ui.in(mx, my, rx + hw + 4, y, hw, 14), trader);
        if (trader) {
            g.text(Ui.fit(g, "Wandering traders have no job or level.", rw), rx, y + R_JOB + 4, Ui.DIM);
            g.text(Ui.fit(g, "This one never despawns.", rw), rx, y + R_LVL + 4, Ui.DIM);
        } else {
            String j = jobs.isEmpty() ? "minecraft:none" : jobs.get(job);
            Ui.cycler(g, mx, my, rx, y + R_JOB, jobW(), pretty(j), jobIcon(j));
            Ui.cycler(g, mx, my, rx + jobW() + 4, y + R_JOB, rw - jobW() - 4, biomes.isEmpty() ? "?" : pretty(biomes.get(biome)), null);
            g.text("Level", rx, y + R_LVL + 4, Ui.TEXT);
            for (int i = 0; i < 5; i++) {
                int bx = rx + 34 + i * 22;
                Ui.selButton(g, String.valueOf(i + 1), bx, y + R_LVL, 20, 16, Ui.in(mx, my, bx, y + R_LVL, 20, 16), level == i + 1);
            }
            g.text(LEVELS[level - 1], rx + 34 + 5 * 22 + 4, y + R_LVL + 4, Ui.DIM);
        }
        chip(g, "No AI", noAi, rx, y + R_OPT1, hw, mx, my);
        chip(g, "Invincible", invul, rx + hw + 4, y + R_OPT1, hw, mx, my);
        chip(g, "Silent", silent, rx, y + R_OPT2, hw, mx, my);
        chip(g, "Fixed Prices", fixed, rx + hw + 4, y + R_OPT2, hw, mx, my);
        name.draw(g, rx, y + R_NAME, rw - UNL_W - 4, 14, "Custom name (optional)");
        chip(g, "Unlimited Uses", Menu.VILL_UNLIMITED.on, rx + rw - UNL_W, y + R_NAME - 1, UNL_W, mx, my);

        // ---- trades ----
        g.text("Trades (" + trades.size() + ")", rx, y + R_TR + 3, Ui.TEXT);
        Ui.button(g, "+ Add", rx + rw - 86, y + R_TR, 44, 13, Ui.in(mx, my, rx + rw - 86, y + R_TR, 44, 13), false);
        Ui.button(g, "Clear", rx + rw - 38, y + R_TR, 38, 13, Ui.in(mx, my, rx + rw - 38, y + R_TR, 38, 13), true);
        trScroll = Math.max(0, Math.min(trScroll, Math.max(0, trades.size() - TR_ROWS)));
        trBar.draw(g, x + w + 1, y + R_LIST, TR_ROWS * (TR_H + 1) - 1, trades.size(), TR_ROWS, trScroll, mx, my);
        if (trades.isEmpty()) {
            int ty = y + R_LIST + 4;
            for (String l : Ui.wrap(g, "No custom trades: the villager gets the normal ones for its job. Add a trade, or click an item on the left.", rw - 4)) {
                g.text(l, rx + 2, ty, Ui.DIM);
                ty += 11;
            }
        }
        for (int i = 0; i < TR_ROWS && trScroll + i < trades.size(); i++) {
            Trade t = trades.get(trScroll + i);
            int ry = y + R_LIST + i * (TR_H + 1);
            boolean hov = Ui.in(mx, my, rx, ry, rw - 18, TR_H);
            Ui.rr(g, rx, ry, rw, TR_H, t == cur ? Ui.SEL : hov ? Ui.HOVER : Ui.BTN);
            stack(g, t.a, rx + 2, ry + 1);
            if (!t.b.isEmpty()) { g.text("+", rx + 21, ry + 5, Ui.DIM); stack(g, t.b, rx + 28, ry + 1); }
            g.text("=", rx + 49, ry + 5, Ui.DIM);
            if (t.s.isEmpty()) g.text("?", rx + 62, ry + 5, Ui.RED);
            else stack(g, t.s, rx + 58, ry + 1);
            String info = t.s.isEmpty() ? "pick a result" : t.s.getHoverName().getString();
            Ui.label(g, info, rx + 80, ry + 5, rw - 132, Ui.TEXT);
            String u = usesText(t.uses);
            g.text(u, rx + rw - 22 - g.width(u), ry + 5, Ui.DIM);
            Ui.button(g, "x", rx + rw - 16, ry + 2, 14, 14, Ui.in(mx, my, rx + rw - 16, ry + 2, 14, 14), true);
        }

        // ---- editor for the selected trade ----
        if (cur == null) {
            g.text("Select a trade to edit it.", rx, y + R_ED + 5, Ui.DIM);
        } else {
            for (int i = 0; i < 3; i++) {
                int bx = boxX(i);
                Ui.rr(g, bx - 1, y + R_ED - 1, 20, 20, i == box ? Ui.ACCENT : Ui.DARK);
                Ui.rr(g, bx, y + R_ED, 18, 18, Ui.in(mx, my, bx, y + R_ED, 18, 18) ? Ui.HOVER : Ui.DARK);
                stack(g, get(cur, i), bx + 1, y + R_ED + 1);
            }
            g.text("+", boxX(0) + 22, y + R_ED + 5, Ui.DIM);
            g.text("=", boxX(1) + 22, y + R_ED + 5, Ui.DIM);
            int cx = rx + 92;
            ItemStack st = get(cur, box);
            Ui.button(g, "-", cx, y + R_ED + 2, 14, 14, Ui.in(mx, my, cx, y + R_ED + 2, 14, 14), false);
            Ui.center(g, st.isEmpty() ? "-" : String.valueOf(st.getCount()), cx + 14, y + R_ED + 5, 22, Ui.TEXT);
            Ui.button(g, "+", cx + 36, y + R_ED + 2, 14, 14, Ui.in(mx, my, cx + 36, y + R_ED + 2, 14, 14), false);
            Ui.button(g, "Hand", rx + rw - 80, y + R_ED + 2, 40, 14, Ui.in(mx, my, rx + rw - 80, y + R_ED + 2, 40, 14), false);
            Ui.button(g, "Empty", rx + rw - 38, y + R_ED + 2, 38, 14, Ui.in(mx, my, rx + rw - 38, y + R_ED + 2, 38, 14), false);

            Ui.button(g, "Uses: " + (USES[cur.uses] == Integer.MAX_VALUE ? "No limit" : USES[cur.uses]), rx, y + R_ED2, 86, 14,
                    Ui.in(mx, my, rx, y + R_ED2, 86, 14), false);
            String[] boxes = { "Editing: Cost", "Editing: Cost 2 (optional)", "Editing: Result" };
            g.text(Ui.fit(g, boxes[box], rw - 92), rx + 92, y + R_ED2 + 3, Ui.DIM);
        }
        boolean noted = note != null && System.currentTimeMillis() < noteUntil;
        String hint = noted ? note : cur == null ? "" : "Hand = the item you hold, with all its data";
        g.text(Ui.fit(g, hint, rw), rx, y + R_ED2 + 19, noted ? Ui.TEXT : Ui.DIM);

        // ---- summon ----
        String sum = trader ? "Summon Wandering Trader" : "Summon " + (jobs.isEmpty() ? "Villager" : pretty(jobs.get(job)));
        Ui.button(g, sum, rx, y + h - 18, rw, 18, Ui.in(mx, my, rx, y + h - 18, rw, 18), false);
    }

    private void drawItems(Gfx g, int mx, int my) {
        search.draw(g, x, y + SEARCH, LW, 16, "Search items...");
        int rows = listRows();
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, view.size() - rows)));
        bar.draw(g, x + LW + 2, y + LIST, rows * ROWH - 1, view.size(), rows, listScroll, mx, my);
        for (int i = 0; i < rows && listScroll + i < view.size(); i++) {
            ItemsPage.Entry e = view.get(listScroll + i);
            int ry = y + LIST + i * ROWH;
            Ui.rr(g, x, ry, LW, ROWH - 1, Ui.in(mx, my, x, ry, LW, ROWH - 1) ? Ui.HOVER : Ui.BTN);
            g.item(e.stack(), x + 1, ry - 1);
            Ui.label(g, e.name(), x + 20, ry + 4, LW - 24, Ui.TEXT);
        }
        g.text(Ui.fit(g, "Click an item: puts it in the selected box", LW), x, y + h - 9, Ui.DIM);
    }

    /** Item in the selected box of the selected trade (empty if none). */
    private ItemStack target() { return cur == null ? ItemStack.EMPTY : get(cur, box); }

    /** Enchantments that make sense for the item (all of them with Show All), plus the ones it already has. */
    private List<String> enchList(ItemStack st) {
        List<String> ids = new ArrayList<>();
        if (st.isEmpty()) return ids;
        java.util.Map<String, Integer> has = Compat.enchantsOf(st);
        for (String id : Compat.enchantIds()) if (showAll || has.containsKey(id) || Compat.enchantFits(id, st)) ids.add(id);
        return ids;
    }

    private void drawEnchants(Gfx g, int mx, int my) {
        chip(g, "Show All", showAll, x, y + SEARCH, LW, mx, my);
        ItemStack st = target();
        List<String> ids = enchList(st);
        java.util.Map<String, Integer> has = st.isEmpty() ? java.util.Map.of() : Compat.enchantsOf(st);
        int rows = listRows();
        enchScroll = Math.max(0, Math.min(enchScroll, Math.max(0, ids.size() - rows)));
        bar.draw(g, x + LW + 2, y + LIST, rows * ROWH - 1, ids.size(), rows, enchScroll, mx, my);
        if (st.isEmpty()) {
            int ty = y + LIST + 2;
            for (String l : Ui.wrap(g, cur == null ? "Add a trade and pick an item first." : "Put an item in the selected box first.", LW - 4)) {
                g.text(l, x + 2, ty, Ui.DIM);
                ty += 11;
            }
        } else if (ids.isEmpty()) g.text("No compatible enchantments.", x + 2, y + LIST + 2, Ui.DIM);
        for (int i = 0; i < rows && enchScroll + i < ids.size(); i++) {
            String id = ids.get(enchScroll + i);
            int ry = y + LIST + i * ROWH, lv = has.getOrDefault(id, 0);
            Ui.rr(g, x, ry, LW, ROWH - 1, lv > 0 ? Ui.SEL : Ui.BTN);
            Ui.label(g, pretty(id), x + 4, ry + 4, LW - 72, Ui.TEXT);
            Ui.button(g, "-", x + LW - 66, ry, 12, ROWH - 1, Ui.in(mx, my, x + LW - 66, ry, 12, ROWH - 1), false);
            Ui.center(g, String.valueOf(lv), x + LW - 54, ry + 4, 16, Ui.TEXT);
            Ui.button(g, "+", x + LW - 38, ry, 12, ROWH - 1, Ui.in(mx, my, x + LW - 38, ry, 12, ROWH - 1), false);
            Ui.button(g, "Max", x + LW - 24, ry, 24, ROWH - 1, Ui.in(mx, my, x + LW - 24, ry, 24, ROWH - 1), false);
        }
        String what = cur == null ? "Enchants the selected box" : new String[] { "Cost", "Cost 2", "Result" }[box] + (st.isEmpty() ? "" : ": " + st.getHoverName().getString());
        Ui.label(g, what, x, y + h - 9, LW, Ui.DIM);
    }

    /** Sets one enchantment level on the selected box's item (0 removes it). A plain book becomes an enchanted book. */
    private void enchant(String id, int lv) {
        ItemStack st = target();
        if (st.isEmpty()) return;
        if (st.is(Items.BOOK)) { st = new ItemStack(Items.ENCHANTED_BOOK, st.getCount()); set(cur, box, st, true); }
        Compat.setEnchant(st, id, lv);
        set(cur, box, st, true);
    }

    /** The villager as it will look (job, biome, level badge), turning its head toward the mouse. */
    private void drawPreview(Gfx g, int mx, int my) {
        Ui.rr(g, x, y, LW, PV, Ui.DARK);
        String j = jobs.isEmpty() ? "minecraft:none" : jobs.get(job), b = biomes.isEmpty() ? "minecraft:plains" : biomes.get(biome);
        String key = trader + "|" + j + "|" + b + "|" + level;
        Object lvl = net.minecraft.client.Minecraft.getInstance().level;
        if (!key.equals(previewKey) || lvl != previewLevel) {
            previewKey = key;
            previewLevel = lvl;
            try { preview = Compat.villagerPreview(trader, j, b, level); } catch (Throwable t) { preview = null; }
        }
        if (preview != null) {
            Ui.entity(g, preview, x + LW - 74, y + 2, x + LW - 4, y + PV - 2, 42f, mx, my);
        }
        int ty = y + 6;
        String n = name.text.trim();
        if (!n.isEmpty()) { Ui.label(g, n, x + 5, ty, 72, Ui.TEXT); ty += 14; }
        if (trader) {
            g.text("Wandering", x + 5, ty, Ui.TEXT);
            g.text("Trader", x + 5, ty + 11, Ui.TEXT);
        } else {
            Ui.label(g, pretty(j), x + 5, ty, 72, Ui.TEXT);
            Ui.label(g, pretty(b), x + 5, ty + 11, 72, Ui.DIM);
            g.text(Ui.fit(g, LEVELS[level - 1], 72), x + 5, ty + 22, Ui.DIM);
        }
        g.text("Preview", x + 5, y + PV - 12, Ui.DIM);
    }

    private static String usesText(int i) { return USES[i] == Integer.MAX_VALUE ? "∞" : USES[i] + "x"; }

    private static void stack(Gfx g, ItemStack st, int sx, int sy) {
        if (st.isEmpty()) return;
        g.item(st, sx, sy);
        g.decorations(st, sx, sy);
    }

    private static void chip(Gfx g, String label, boolean on, int cx, int cy, int cw, int mx, int my) {
        Ui.rr(g, cx, cy, cw, 16, Ui.in(mx, my, cx, cy, cw, 16) ? Ui.HOVER : Ui.BTN);
        Ui.label(g, label, cx + 4, cy + 4, cw - 38, Ui.TEXT);
        Ui.pill(g, cx + cw - 32, cy + 2, on);
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean click(int mx, int my, int btn) {
        if (bar.press(mx, my)) { scrollTo(bar.at(my)); return true; }
        if (trBar.press(mx, my)) { trScroll = trBar.at(my); return true; }
        int tw = (LW - 4) / 2;
        if (Ui.in(mx, my, x, y + TABS, tw, 14)) { enchMode = false; return true; }
        if (Ui.in(mx, my, x + tw + 4, y + TABS, tw, 14)) { enchMode = true; return true; }
        int rx = rx(), rw = rw(), hw = hw();
        if (Ui.in(mx, my, rx, y + R_NAME, rw - UNL_W - 4, 14)) { name.focused = true; search.focused = false; return true; }
        if (Ui.in(mx, my, rx + rw - UNL_W, y + R_NAME - 1, UNL_W, 16)) {
            Menu.VILL_UNLIMITED.on = !Menu.VILL_UNLIMITED.on;
            Menu.changed();
            note(Menu.VILL_UNLIMITED.on ? "Normal trades of new villagers never run out." : "Normal trades restock the vanilla way.");
            return true;
        }

        int rows = listRows();
        if (enchMode) {
            if (Ui.in(mx, my, x, y + SEARCH, LW, 16)) { showAll = !showAll; enchScroll = 0; return true; }
            ItemStack st = target();
            List<String> ids = enchList(st);
            java.util.Map<String, Integer> has = st.isEmpty() ? java.util.Map.of() : Compat.enchantsOf(st);
            for (int i = 0; i < rows && enchScroll + i < ids.size(); i++) {
                String id = ids.get(enchScroll + i);
                int ry = y + LIST + i * ROWH, lv = has.getOrDefault(id, 0);
                int cap = Menu.OVERLEVEL.on ? 255 : Compat.enchantMax(id), d = btn == 1 && Menu.OVERLEVEL.on ? 10 : 1;
                if (Ui.in(mx, my, x + LW - 66, ry, 12, ROWH - 1)) { enchant(id, Math.max(0, lv - d)); return true; }
                if (Ui.in(mx, my, x + LW - 38, ry, 12, ROWH - 1)) { enchant(id, Math.min(Math.max(cap, lv), lv + d)); return true; }
                if (Ui.in(mx, my, x + LW - 24, ry, 24, ROWH - 1)) { enchant(id, Compat.enchantMax(id)); return true; }
            }
        } else {
            if (Ui.in(mx, my, x, y + SEARCH, LW, 16)) { search.focused = true; name.focused = false; return true; }
            for (int i = 0; i < rows && listScroll + i < view.size(); i++)
                if (Ui.in(mx, my, x, y + LIST + i * ROWH, LW, ROWH - 1)) { pick(view.get(listScroll + i)); return true; }
        }

        if (Ui.in(mx, my, rx, y, hw, 14)) { trader = false; return true; }
        if (Ui.in(mx, my, rx + hw + 4, y, hw, 14)) { trader = true; return true; }
        if (!trader) {
            int d = Ui.cycled(mx, my, btn, rx, y + R_JOB, jobW());
            if (d != 0 && !jobs.isEmpty()) { job = Math.floorMod(job + d, jobs.size()); return true; }
            d = Ui.cycled(mx, my, btn, rx + jobW() + 4, y + R_JOB, rw - jobW() - 4);
            if (d != 0 && !biomes.isEmpty()) { biome = Math.floorMod(biome + d, biomes.size()); return true; }
            for (int i = 0; i < 5; i++) if (Ui.in(mx, my, rx + 34 + i * 22, y + R_LVL, 20, 16)) { level = i + 1; return true; }
        }
        if (Ui.in(mx, my, rx, y + R_OPT1, hw, 16)) { noAi = !noAi; return true; }
        if (Ui.in(mx, my, rx + hw + 4, y + R_OPT1, hw, 16)) { invul = !invul; return true; }
        if (Ui.in(mx, my, rx, y + R_OPT2, hw, 16)) { silent = !silent; return true; }
        if (Ui.in(mx, my, rx + hw + 4, y + R_OPT2, hw, 16)) { fixed = !fixed; return true; }

        if (Ui.in(mx, my, rx + rw - 86, y + R_TR, 44, 13)) { addTrade(); return true; }
        if (Ui.in(mx, my, rx + rw - 38, y + R_TR, 38, 13)) { trades.clear(); cur = null; return true; }
        for (int i = 0; i < TR_ROWS && trScroll + i < trades.size(); i++) {
            int ry = y + R_LIST + i * (TR_H + 1);
            Trade t = trades.get(trScroll + i);
            if (Ui.in(mx, my, rx + rw - 16, ry + 2, 14, 14)) {
                trades.remove(t);
                if (cur == t) cur = trades.isEmpty() ? null : trades.get(Math.min(trScroll + i, trades.size() - 1));
                return true;
            }
            if (Ui.in(mx, my, rx, ry, rw - 18, TR_H)) { cur = t; return true; }
        }

        if (cur != null) {
            for (int i = 0; i < 3; i++) if (Ui.in(mx, my, boxX(i), y + R_ED, 18, 18)) { box = i; enchScroll = 0; return true; }
            int cx = rx + 92, step = btn == 1 ? 8 : 1;
            if (Ui.in(mx, my, cx, y + R_ED + 2, 14, 14)) { count(-step); return true; }
            if (Ui.in(mx, my, cx + 36, y + R_ED + 2, 14, 14)) { count(step); return true; }
            if (Ui.in(mx, my, rx + rw - 80, y + R_ED + 2, 40, 14)) { fromHand(); return true; }
            if (Ui.in(mx, my, rx + rw - 38, y + R_ED + 2, 38, 14)) { set(cur, box, ItemStack.EMPTY, false); return true; }
            if (Ui.in(mx, my, rx, y + R_ED2, 86, 14)) { cur.uses = Math.floorMod(cur.uses + (btn == 1 ? -1 : 1), USES.length); return true; }
        }
        if (Ui.in(mx, my, rx, y + h - 18, rw, 18)) { summon(); return true; }
        return false;
    }

    /** Item from the list goes into the selected box (a new trade is made when there is none). */
    private void pick(ItemsPage.Entry e) {
        if (cur == null) addTrade();
        ItemStack old = get(cur, box);
        int n = old.isEmpty() ? 1 : Math.min(old.getCount(), e.stack().getMaxStackSize());
        set(cur, box, e.stack().copyWithCount(n), false);
        if (box == 0 && cur.s.isEmpty()) box = 2;   // cost picked: go on with the result
    }

    private void fromHand() {
        ItemStack held = Compat.heldItem();
        if (held.isEmpty()) { note("Hold the item in your main hand first."); return; }
        set(cur, box, held.copy(), true);
        note("Took " + held.getHoverName().getString() + " from your hand.");
    }

    private void count(int d) {
        ItemStack st = get(cur, box);
        if (st.isEmpty()) return;
        st.setCount(Math.max(1, Math.min(st.getMaxStackSize(), st.getCount() + d)));
    }

    // ------------------------------------------------------------------ commands
    private static String item(ItemStack st) {
        return "{id:\"" + BuiltInRegistries.ITEM.getKey(st.getItem()) + "\",count:" + st.getCount() + "}";
    }

    /** The trade with plain items; extra item data (enchantments...) is added afterwards by {@link #components}. */
    private String offer(Trade t) {
        StringBuilder sb = new StringBuilder("{buy:").append(item(t.a));
        if (!t.b.isEmpty()) sb.append(",buyB:").append(item(t.b));
        sb.append(",sell:").append(item(t.s));
        // xp:0 = the villager never levels up, so it never adds its normal trades on top of yours
        sb.append(",maxUses:").append(USES[t.uses]).append(",xp:0");
        if (!fixed) sb.append(",priceMultiplier:0.05f");
        return sb.append('}').toString();
    }

    /**
     * Adds an item's extra data to the trade just appended. Each part gets its own "data modify" command, split down
     * (component, then e.g. single enchantment) until every command fits the server limit. Returns false if a part is still too long.
     */
    private static boolean components(List<String> cmds, String path, ItemStack st, boolean custom) {
        if (!custom || st.isEmpty()) return true;
        net.minecraft.nbt.CompoundTag c = Compat.componentsTag(st);
        return c == null || split(cmds, path + ".components", c);
    }

    private static boolean split(List<String> cmds, String path, net.minecraft.nbt.Tag value) {
        String c = "data modify entity " + SELF + " " + path + " set value " + value;
        boolean local = Access.mode == Access.Mode.LOCAL;
        if (local || c.length() <= MAX_CMD || !(value instanceof net.minecraft.nbt.CompoundTag ct) || Compat.keys(ct).isEmpty()) {
            if (!local && c.length() > MAX_CMD) return false;
            cmds.add(c);
            return true;
        }
        boolean ok = true;
        for (String k : Compat.keys(ct))
            ok &= split(cmds, path + "." + (k.matches("[A-Za-z0-9_]+") ? k : "\"" + k + "\""), ct.get(k));
        return ok;
    }

    private void summon() {
        init();
        List<Trade> ok = new ArrayList<>();
        for (Trade t : trades) if (!t.a.isEmpty() && !t.s.isEmpty()) ok.add(t);

        List<String> base = new ArrayList<>();
        boolean unlimited = Menu.VILL_UNLIMITED.on && ok.isEmpty();   // custom trades have their own Uses setting
        base.add("Tags:[\"" + TAG + "\"" + (unlimited ? ",\"" + UNLIMITED_TAG + "\"" : "") + "]");
        if (trader) base.add("DespawnDelay:0");
        else if (!jobs.isEmpty()) {
            base.add("VillagerData:{profession:\"" + jobs.get(job) + "\",level:" + level + ",type:\"" + biomes.get(biome) + "\"}");
            base.add("Xp:1");   // a level 1 villager with 0 xp and no job block would lose its job
        }
        if (!ok.isEmpty()) base.add("Offers:{Recipes:[]}");
        List<String> extra = new ArrayList<>();
        if (noAi) extra.add("NoAI:1b");
        if (invul) extra.add("Invulnerable:1b");
        if (silent) extra.add("Silent:1b");
        String n = name.text.replaceAll("[\"'\\\\]", "").trim();
        if (!n.isEmpty()) { extra.add(Compat.customName(n)); extra.add("CustomNameVisible:1b"); }

        List<String> cmds = new ArrayList<>();
        cmds.add("execute at @s rotated ~ 0 positioned ^ ^ ^3 run summon minecraft:" + (trader ? "wandering_trader" : "villager")
                + " ~ ~ ~ {" + String.join(",", base) + "}");
        if (!extra.isEmpty()) cmds.add("data merge entity " + SELF + " {" + String.join(",", extra) + "}");
        int skipped = 0;
        for (Trade t : ok) {
            List<String> tc = new ArrayList<>();
            tc.add("data modify entity " + SELF + " Offers.Recipes append value " + offer(t));
            String last = "Offers.Recipes[-1].";
            boolean fits = components(tc, last + "buy", t.a, t.customA)
                    & components(tc, last + "buyB", t.b, t.customB)
                    & components(tc, last + "sell", t.s, t.customS);
            if (fits) cmds.addAll(tc); else skipped++;   // some single piece of item data is over the server's command limit
        }
        if (unlimited) cmds.add(UNLIMITED_CMD);   // right away where the trades already exist (1.21.x), later via Features
        cmds.add("tag @e[tag=" + TAG + "] remove " + TAG);
        Cmd.run(cmds.toArray(new String[0]));

        int bad = trades.size() - ok.size();
        if (skipped > 0) note(skipped + " trade(s) too long for a server, left out.");
        else if (bad > 0) note(bad + " unfinished trade(s) left out.");
        else note(ok.isEmpty() ? (unlimited ? "Summoned with normal trades, unlimited uses." : "Summoned with normal trades.")
                : "Summoned with " + ok.size() + " custom trade(s).");
    }

    @Override
    public boolean drag(int mx, int my) {
        if (bar.dragging()) scrollTo(bar.at(my));
        else if (trBar.dragging()) trScroll = trBar.at(my);
        else return false;
        return true;
    }

    @Override public void release() { bar.release(); trBar.release(); }

    @Override
    public boolean scroll(int mx, int my, double dy) {
        int d = (int) -Math.signum(dy);
        if (mx < x + LW + 4) { if (my >= y + LIST) { if (enchMode) enchScroll += d; else listScroll += d * 3; } }
        else if (my >= y + R_LIST && my < y + R_LIST + TR_ROWS * (TR_H + 1)) trScroll += d;
        else if (cur != null && my >= y + R_ED && my < y + R_ED + 18) count(-d);
        return true;
    }

    private void scrollTo(int pos) { if (enchMode) enchScroll = pos; else listScroll = pos; }

    @Override public boolean typed(char c) { return search.focused ? search.typed(c) : name.typed(c); }
    @Override public boolean key(int key, int scan, int mods) { return search.focused ? search.key(key, mods) : name.key(key, mods); }
}
