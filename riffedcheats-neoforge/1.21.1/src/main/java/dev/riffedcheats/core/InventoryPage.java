package dev.riffedcheats.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory editor (yours, or another player's from the Players tab): armor, offhand, main inventory and hotbar.
 * Click a slot, then put an item in it from the search list, change the count, remove / repair / make it
 * unbreakable, copy another player's item to yourself, or edit its enchantments. Everything is done with /item,
 * so it works on servers too (OP).
 */
public final class InventoryPage implements Page {
    private static final int S = 18, GX = 22, GY = 16, LW = 186, RX0 = 194, LIST = 144, ROWH = 16, EN = 136;
    private static final int[] SLOTS;   // drawing / hit-test order
    static {
        SLOTS = new int[41];
        int[] armor = { 103, 102, 101, 100, 106 };
        System.arraycopy(armor, 0, SLOTS, 0, 5);
        for (int i = 0; i < 36; i++) SLOTS[5 + i] = i;
    }

    private final Supplier<String> target;   // null = yourself
    private final Runnable back;             // null = no Back button
    private final TextField search = new TextField();
    private String lastQuery, requested;
    private List<ItemsPage.Entry> view = List.of();
    private int listScroll, enchScroll, count = 1;
    private Integer sel;                     // selected slot
    private boolean showAll;
    private String note;                     // short result message shown in the hint line
    private long noteUntil;
    private int x, y, w, h;

    public InventoryPage(Supplier<String> target, Runnable back) { this.target = target; this.back = back; }

    @Override public boolean wantsText() { return true; }

    /** Reads the other player's inventory again the next time the page is drawn. */
    public void reload() { requested = null; }

    private String name() { return target == null ? null : target.get(); }
    private String who() { return target == null ? "@s" : name(); }

    private ItemStack stack(int slot) {
        if (target != null) return Inventories.get(name(), slot);
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return ItemStack.EMPTY;
        return switch (slot) {
            case 100 -> p.getItemBySlot(EquipmentSlot.FEET);
            case 101 -> p.getItemBySlot(EquipmentSlot.LEGS);
            case 102 -> p.getItemBySlot(EquipmentSlot.CHEST);
            case 103 -> p.getItemBySlot(EquipmentSlot.HEAD);
            case 106 -> p.getItemBySlot(EquipmentSlot.OFFHAND);
            default -> p.getInventory().getItem(slot);
        };
    }

    private static String slotArg(int slot) {
        return switch (slot) {
            case 100 -> "armor.feet";
            case 101 -> "armor.legs";
            case 102 -> "armor.chest";
            case 103 -> "armor.head";
            case 106 -> "weapon.offhand";
            default -> "container." + slot;
        };
    }

    private static String slotLabel(int slot) {
        return switch (slot) {
            case 100 -> "Feet";
            case 101 -> "Legs";
            case 102 -> "Chest";
            case 103 -> "Head";
            case 106 -> "Offhand";
            default -> slot < 9 ? "Hotbar " + (slot + 1) : "Inventory " + (slot - 8);
        };
    }

    private int sx(int slot) {
        if (slot >= 100) return x;
        return x + GX + (slot < 9 ? slot : (slot - 9) % 9) * S;
    }
    private int sy(int slot) {
        if (slot == 106) return y + GY + 4 * S + 4;
        if (slot >= 100) return y + GY + (103 - slot) * S;
        return slot < 9 ? y + GY + 3 * S + 4 : y + GY + ((slot - 9) / 9) * S;
    }

    private static final int[] PRESETS = { 1, 16, 64 };
    private int presetX(int i) { return rx() + 90 + i * 22; }
    private int rx() { return x + RX0; }
    /** Top of the right column: pushed below the header when the header has Refresh / Back buttons. */
    private int top() { return y + (target != null || back != null ? 18 : 0); }
    private int rw() { return w - RX0; }
    private int listRows() { return Math.max(1, (h - LIST) / ROWH); }
    private int enchRows() { return Math.max(1, (y + h - top() - EN - 22) / 16); }
    private int backX() { return x + w - 50; }
    private int refreshX() { return x + w - (back != null ? 104 : 50); }
    /** Width of the Unbreakable button: shares its row with Copy to Me when viewing another player. */
    private int ubw() { return target != null ? (rw() - 4) / 2 : rw(); }

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

    private List<String> enchList(ItemStack st) {
        List<String> ids = new ArrayList<>();
        if (st.isEmpty()) return ids;
        Map<String, Integer> cur = Compat.enchantsOf(st);
        for (String id : Compat.enchantIds()) if (showAll || cur.containsKey(id) || Compat.enchantFits(id, st)) ids.add(id);
        return ids;
    }

    private static String pretty(String id) {
        String p = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String s : p.split(" ")) if (!s.isEmpty()) sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).append(' ');
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(Gfx g, int mx, int my, int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        refilter();
        search.focused = true;
        String n = name();
        if (target != null && n != null && !n.equals(requested)) { requested = n; sel = null; Inventories.request(n); }

        // ---- header ----
        String title = target == null ? "Your inventory" : "Inventory of " + n + (Inventories.loaded(n) ? "" : "  (loading...)");
        g.text(Ui.fit(g, title, w - 120), x, y + 3, Ui.TEXT);
        if (target != null) Ui.button(g, "Refresh", refreshX(), y, 50, 13, Ui.in(mx, my, refreshX(), y, 50, 13), false);
        if (back != null) Ui.button(g, "Back", backX(), y, 50, 13, Ui.in(mx, my, backX(), y, 50, 13), false);

        // ---- slots ----
        ItemStack hovered = null;
        for (int slot : SLOTS) {
            int bx = sx(slot), by = sy(slot);
            boolean hov = Ui.in(mx, my, bx, by, S - 1, S - 1);
            ItemStack st = stack(slot);
            if (hov && !st.isEmpty()) hovered = st;
            Ui.rr(g, bx, by, S - 1, S - 1, sel != null && sel == slot ? Ui.SEL : hov ? Ui.HOVER : Ui.DARK);
            if (!st.isEmpty()) {
                g.item(st, bx, by);
                g.decorations(st, bx, by);   // count + durability bar
            }
        }
        boolean noted = note != null && System.currentTimeMillis() < noteUntil;
        String hint = hovered != null ? hovered.getHoverName().getString() : noted ? note : sel == null ? "Click a slot to edit it." : "Editing: " + slotLabel(sel);
        g.text(Ui.fit(g, hint, LW), x, y + GY + 5 * S + 2, hovered == null && noted ? Ui.TEXT : Ui.DIM);

        // ---- item picker ----
        search.draw(g, x, y + LIST - 18, LW, 14, sel == null ? "Search item (goes to first free slot)" : "Search item to put in the slot");
        int rows = listRows();
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, view.size() - rows)));
        for (int i = 0; i < rows && listScroll + i < view.size(); i++) {
            ItemsPage.Entry e = view.get(listScroll + i);
            int ry = y + LIST + i * ROWH;
            Ui.rr(g, x, ry, LW, ROWH - 1, Ui.in(mx, my, x, ry, LW, ROWH - 1) ? Ui.HOVER : Ui.BTN);
            g.item(e.stack(), x + 1, ry - 1);
            g.text(Ui.fit(g, e.name(), LW - 24), x + 20, ry + 4, Ui.TEXT);
        }

        // ---- right: selected slot ----
        int rx = rx(), rw = rw();
        ItemStack st = sel == null ? ItemStack.EMPTY : stack(sel);
        Ui.rr(g, rx, top(), rw, 52, Ui.PANEL);
        if (sel == null) Ui.center(g, "No slot selected", rx, top() + 22, rw, Ui.DIM);
        else if (st.isEmpty()) { Ui.center(g, "Empty slot", rx, top() + 16, rw, Ui.DIM); Ui.center(g, slotLabel(sel), rx, top() + 28, rw, Ui.DIM); }
        else {
            g.itemScaled(st, rx + 4, top() + 4, 2.75f);
            g.text(Ui.fit(g, st.getHoverName().getString(), rw - 56), rx + 52, top() + 8, Ui.TEXT);
            g.text(Ui.fit(g, BuiltInRegistries.ITEM.getKey(st.getItem()).toString(), rw - 56), rx + 52, top() + 20, Ui.DIM);
            g.text(slotLabel(sel) + "  x" + st.getCount(), rx + 52, top() + 32, Ui.DIM);
        }

        int cy = top() + 56;
        g.text("Count", rx, cy + 4, Ui.TEXT);
        Ui.button(g, "-", rx + 34, cy, 14, 14, Ui.in(mx, my, rx + 34, cy, 14, 14), false);
        Ui.center(g, String.valueOf(count), rx + 48, cy + 3, 24, Ui.TEXT);
        Ui.button(g, "+", rx + 72, cy, 14, 14, Ui.in(mx, my, rx + 72, cy, 14, 14), false);
        for (int i = 0; i < PRESETS.length; i++)
            Ui.selButton(g, String.valueOf(PRESETS[i]), presetX(i), cy, 20, 14, Ui.in(mx, my, presetX(i), cy, 20, 14), count == PRESETS[i]);
        Ui.button(g, "Set", rx + 158, cy, rw - 158, 14, Ui.in(mx, my, rx + 158, cy, rw - 158, 14), false);

        int ay = top() + 76, aw = (rw - 4) / 2;
        Ui.button(g, "Remove", rx, ay, aw, 16, Ui.in(mx, my, rx, ay, aw, 16), true);
        Ui.button(g, "Repair", rx + aw + 4, ay, aw, 16, Ui.in(mx, my, rx + aw + 4, ay, aw, 16), false);
        int ub = ubw();
        Ui.button(g, "Unbreakable", rx, ay + 20, ub, 16, Ui.in(mx, my, rx, ay + 20, ub, 16), false);
        if (target != null) Ui.button(g, "Copy to Me", rx + ub + 4, ay + 20, ub, 16, Ui.in(mx, my, rx + ub + 4, ay + 20, ub, 16), false);

        // ---- enchantments ----
        int hy = top() + 118;
        g.text("Enchantments", rx, hy + 4, Ui.DIM);
        Ui.rr(g, rx + rw - 80, hy, 80, 14, Ui.in(mx, my, rx + rw - 80, hy, 80, 14) ? Ui.HOVER : Ui.BTN);
        g.text("Show All", rx + rw - 76, hy + 3, Ui.TEXT);
        Ui.pill(g, rx + rw - 31, hy + 1, showAll);
        List<String> ids = enchList(st);
        Map<String, Integer> cur = st.isEmpty() ? Map.of() : Compat.enchantsOf(st);
        int er = enchRows();
        enchScroll = Math.max(0, Math.min(enchScroll, Math.max(0, ids.size() - er)));
        if (st.isEmpty()) g.text(sel == null ? "Select a slot first." : "Put an item in the slot first.", rx, top() + EN + 2, Ui.DIM);
        else if (ids.isEmpty()) g.text("No compatible enchantments.", rx, top() + EN + 2, Ui.DIM);
        for (int i = 0; i < er && enchScroll + i < ids.size(); i++) {
            String id = ids.get(enchScroll + i);
            int ry = top() + EN + i * 16, lv = cur.getOrDefault(id, 0);
            Ui.rr(g, rx, ry, rw, 15, lv > 0 ? Ui.SEL : Ui.BTN);
            g.text(Ui.fit(g, pretty(id), rw - 88), rx + 4, ry + 4, Ui.TEXT);
            Ui.button(g, "-", rx + rw - 80, ry, 14, 15, Ui.in(mx, my, rx + rw - 80, ry, 14, 15), false);
            Ui.center(g, String.valueOf(lv), rx + rw - 66, ry + 4, 22, Ui.TEXT);
            Ui.button(g, "+", rx + rw - 44, ry, 14, 15, Ui.in(mx, my, rx + rw - 44, ry, 14, 15), false);
            Ui.button(g, "Max", rx + rw - 28, ry, 28, 15, Ui.in(mx, my, rx + rw - 28, ry, 28, 15), false);
        }
        int hw = (rw - 4) / 2;
        Ui.button(g, "Max All", rx, y + h - 18, hw, 18, Ui.in(mx, my, rx, y + h - 18, hw, 18), false);
        Ui.button(g, "Clear Enchants", rx + hw + 4, y + h - 18, hw, 18, Ui.in(mx, my, rx + hw + 4, y + h - 18, hw, 18), false);
    }

    // ------------------------------------------------------------------ commands
    private void after() { if (target != null) Inventories.request(name()); } // reread the other player's inventory

    private void modify(String fn) { if (sel != null) Cmd.run("item modify entity " + who() + " " + slotArg(sel) + " " + fn); }

    /** Sets enchantment levels (0 removes). Long lists are split so every command stays under the 255 character limit. */
    private void setEnchants(Map<String, Integer> levels) {
        if (sel == null || levels.isEmpty()) return;
        String pre = "item modify entity " + who() + " " + slotArg(sel) + " {function:\"minecraft:set_enchantments\",enchantments:{";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : levels.entrySet()) {
            String part = "\"" + e.getKey() + "\":" + e.getValue();
            if (sb.length() > 0 && pre.length() + sb.length() + part.length() + 3 > 250) { Cmd.run(pre + sb + "}}"); sb.setLength(0); }
            if (sb.length() > 0) sb.append(',');
            sb.append(part);
        }
        if (sb.length() > 0) Cmd.run(pre + sb + "}}");
        after();
    }

    private void put(ItemsPage.Entry e) {
        Integer slot = sel;
        if (slot == null) for (int i = 0; i < 36; i++) if (stack(i).isEmpty()) { slot = i; break; }
        if (slot == null) return;
        sel = slot;
        Cmd.run("item replace entity " + who() + " " + slotArg(slot) + " with " + e.id() + " " + count);
        after();
    }

    /** Copies the selected slot of the other player (with all its data) into your first free inventory slot. */
    private void copyToMe() {
        if (sel == null || stack(sel).isEmpty()) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        int free = -1;
        for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).isEmpty()) { free = i; break; }
        note(free < 0 ? "Your inventory is full." : "Copied to your " + slotLabel(free) + ".");
        if (free >= 0) Cmd.run("item replace entity @s container." + free + " from entity " + who() + " " + slotArg(sel));
    }

    private void note(String s) { note = s; noteUntil = System.currentTimeMillis() + 3000; }

    // ------------------------------------------------------------------ input
    @Override
    public boolean click(int mx, int my, int btn) {
        if (back != null && Ui.in(mx, my, backX(), y, 50, 13)) { back.run(); return true; }
        if (target != null && Ui.in(mx, my, refreshX(), y, 50, 13)) { Inventories.request(name()); return true; }
        for (int slot : SLOTS)
            if (Ui.in(mx, my, sx(slot), sy(slot), S - 1, S - 1)) { sel = sel != null && sel == slot ? null : slot; enchScroll = 0; return true; }
        int rows = listRows();
        for (int i = 0; i < rows && listScroll + i < view.size(); i++)
            if (Ui.in(mx, my, x, y + LIST + i * ROWH, LW, ROWH - 1)) { put(view.get(listScroll + i)); return true; }

        int rx = rx(), rw = rw(), cy = top() + 56;
        if (Ui.in(mx, my, rx + 34, cy, 14, 14)) { count = Math.max(1, count - (btn == 1 ? 8 : 1)); return true; }
        if (Ui.in(mx, my, rx + 72, cy, 14, 14)) { count = Math.min(99, count + (btn == 1 ? 8 : 1)); return true; }
        for (int i = 0; i < PRESETS.length; i++) if (Ui.in(mx, my, presetX(i), cy, 20, 14)) { count = PRESETS[i]; return true; }
        if (sel == null) return false;
        ItemStack st = stack(sel);
        if (Ui.in(mx, my, rx + 158, cy, rw - 158, 14)) { modify("{function:\"minecraft:set_count\",count:" + count + "}"); after(); return true; }

        int ay = top() + 76, aw = (rw - 4) / 2;
        if (Ui.in(mx, my, rx, ay, aw, 16)) { Cmd.run("item replace entity " + who() + " " + slotArg(sel) + " with minecraft:air"); after(); return true; }
        if (Ui.in(mx, my, rx + aw + 4, ay, aw, 16)) { modify("{function:\"minecraft:set_damage\",damage:1.0}"); after(); return true; }
        if (target != null && Ui.in(mx, my, rx + ubw() + 4, ay + 20, ubw(), 16)) { copyToMe(); return true; }
        if (Ui.in(mx, my, rx, ay + 20, ubw(), 16)) {
            modify("{function:\"minecraft:set_components\",components:{\"minecraft:unbreakable\":{}}}");
            after();
            return true;
        }
        if (Ui.in(mx, my, rx + rw - 80, top() + 118, 80, 14)) { showAll = !showAll; enchScroll = 0; return true; }
        if (st.isEmpty()) return false;

        List<String> ids = enchList(st);
        Map<String, Integer> cur = Compat.enchantsOf(st);
        int er = enchRows();
        for (int i = 0; i < er && enchScroll + i < ids.size(); i++) {
            String id = ids.get(enchScroll + i);
            int ry = top() + EN + i * 16, lv = cur.getOrDefault(id, 0);
            int cap = Menu.OVERLEVEL.on ? 255 : Compat.enchantMax(id), d = btn == 1 && Menu.OVERLEVEL.on ? 10 : 1;
            if (Ui.in(mx, my, rx + rw - 80, ry, 14, 15)) { setEnchants(Map.of(id, Math.max(0, lv - d))); return true; }
            if (Ui.in(mx, my, rx + rw - 44, ry, 14, 15)) { setEnchants(Map.of(id, Math.min(Math.max(cap, lv), lv + d))); return true; }
            if (Ui.in(mx, my, rx + rw - 28, ry, 28, 15)) { setEnchants(Map.of(id, Compat.enchantMax(id))); return true; }
        }
        int hw = (rw - 4) / 2;
        if (Ui.in(mx, my, rx, y + h - 18, hw, 18)) {
            Map<String, Integer> all = new java.util.LinkedHashMap<>();
            for (String id : ids) all.put(id, Compat.enchantMax(id));
            setEnchants(all);
            return true;
        }
        if (Ui.in(mx, my, rx + hw + 4, y + h - 18, hw, 18)) {
            Map<String, Integer> none = new java.util.LinkedHashMap<>();
            for (String id : cur.keySet()) none.put(id, 0);
            setEnchants(none);
            return true;
        }
        return false;
    }

    @Override
    public boolean scroll(int mx, int my, double dy) {
        int d = (int) -Math.signum(dy);
        if (mx >= rx() && my >= top() + 56 && my < top() + 70) count = Math.max(1, Math.min(99, count - d)); // wheel over the count row
        else if (mx < x + LW + 4) listScroll += d * 3; else enchScroll += d;
        return true;
    }

    @Override public boolean typed(char c) { return search.typed(c); }
    @Override public boolean key(int key, int scan, int mods) {
        if (key == 256 && back != null) { back.run(); return true; } // Esc goes back to the player list instead of closing the menu
        return search.key(key, mods);
    }
}
