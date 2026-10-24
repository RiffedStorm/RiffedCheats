package dev.riffedcheats.core;

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

/**
 * Mob summoner, laid out like the item spawner: search + filter on the left; on the right a live 3D preview, options and
 * per-mob looks (wolf / cat variant, collar, tamed, horse color, sheep wool...). The preview gets exactly the NBT the
 * summon command uses, so what you see is what spawns.
 */
public final class MobsPage implements Page {
    private record Entry(String id, String name, String lower, ItemStack icon, MobCategory cat) {}
    /** One look option: a row of choices; choice 0 adds nothing (the game picks / default). values are SNBT values for key. */
    private record Opt(String label, String key, String[] names, String[] values) {}

    private static final String OWNER = "%OWNER%";   // replaced with your UUID when building the NBT
    private static final String[] DYES = { "White", "Orange", "Magenta", "Light Blue", "Yellow", "Lime", "Pink", "Gray",
            "Light Gray", "Cyan", "Purple", "Blue", "Brown", "Green", "Red", "Black" };

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
    private List<Opt> opts = List.of();
    private int[] optIdx = new int[0];
    private net.minecraft.world.entity.Entity preview;
    private String previewKey;
    private final Scrollbar bar = new Scrollbar();

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
    private static final int PH = 80, COUNT_Y = 84, T1 = 102, T2 = 119, T3 = 136, NAME_Y = 154, OPT_Y = 174, OPT_ROWS = 3, FRONT_W = 80;
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
            Ui.label(g, e.name(), x + 20, ry + 5, LW - 24, Ui.TEXT);
        }
        bar.draw(g, x + LW + 2, y + LIST, rows * ROWH - 1, view.size(), rows, listScroll, mx, my);
        g.text(view.size() + " entities", x + 2, y + h - 9, Ui.DIM);

        // ---- right: preview ----
        int rx = rx(), rw = rw(), hw = hw();
        drawPreview(g, mx, my, hovered != null ? hovered : selected);

        // ---- count ----
        int cy = y + COUNT_Y;
        g.text("Count", rx, cy + 5, Ui.TEXT);
        Ui.button(g, "-", rx + 40, cy, 14, 16, Ui.in(mx, my, rx + 40, cy, 14, 16), false);
        Ui.center(g, String.valueOf(count), rx + 54, cy + 4, 24, Ui.TEXT);
        Ui.button(g, "+", rx + 78, cy, 14, 16, Ui.in(mx, my, rx + 78, cy, 14, 16), false);
        int[] presets = { 1, 5, 10 };
        for (int i = 0; i < 3; i++) {
            int bx = rx + 100 + i * 34;
            Ui.selButton(g, String.valueOf(presets[i]), bx, cy, 30, 16, Ui.in(mx, my, bx, cy, 30, 16), count == presets[i]);
        }

        // ---- options (two columns) ----
        chip(g, "No AI", noAi, rx, y + T1, hw, mx, my);
        chip(g, "Baby", baby, rx + hw + 4, y + T1, hw, mx, my);
        chip(g, "Glowing", glow, rx, y + T2, hw, mx, my);
        chip(g, "Invincible", invul, rx + hw + 4, y + T2, hw, mx, my);
        chip(g, "No Despawn", persist, rx, y + T3, hw, mx, my);
        chip(g, "Silent", silent, rx + hw + 4, y + T3, hw, mx, my);
        name.draw(g, rx, y + NAME_Y, rw - FRONT_W - 4, 16, "Custom name (optional)");
        chip(g, front ? "In front" : "At feet", front, rx + rw - FRONT_W, y + NAME_Y, FRONT_W, mx, my);

        // ---- looks of the selected mob ----
        if (selected != null && opts.isEmpty()) g.text(Ui.fit(g, "No extra looks for this mob.", rw), rx, y + OPT_Y + 4, Ui.DIM);
        for (int i = 0; i < opts.size() && i < OPT_ROWS; i++) {
            Opt o = opts.get(i);
            Ui.cycler(g, mx, my, rx, y + OPT_Y + i * 18, rw, o.label() + ": " + o.names()[optIdx[i]], null);
        }

        // ---- actions ----
        int gy = y + h - 40;
        String sum = selected == null ? "Select a mob" : "Summon x" + count;
        Ui.button(g, sum, rx, gy, rw, 20, Ui.in(mx, my, rx, gy, rw, 20), false);
        Ui.button(g, "Kill Type (64m)", rx, y + h - 18, hw, 18, Ui.in(mx, my, rx, y + h - 18, hw, 18), true);
        Ui.button(g, "Kill All Mobs (64m)", rx + hw + 4, y + h - 18, hw, 18, Ui.in(mx, my, rx + hw + 4, y + h - 18, hw, 18), true);
    }

    /** Live model of the mob (with the chosen looks when it is the selected one); an icon for things that are not mobs. */
    private void drawPreview(Gfx g, int mx, int my, Entry shown) {
        int rx = rx(), rw = rw();
        Ui.rr(g, rx, y, rw, PH, Ui.PANEL);
        if (shown == null) { Ui.center(g, "Hover or click a mob", rx, y + PH / 2 - 4, rw, Ui.DIM); return; }
        String nbt = shown == selected ? looksNbt() : "";
        String key = shown.id() + nbt + "|" + System.identityHashCode(net.minecraft.client.Minecraft.getInstance().level);
        if (!key.equals(previewKey)) {
            previewKey = key;
            try {
                preview = Compat.createEntity(shown.id());
                if (preview != null && !nbt.isEmpty()) Compat.loadNbt(preview, nbt);
            } catch (Throwable t) { preview = null; }
        }
        int bx = rx + rw - 110;
        if (preview instanceof net.minecraft.world.entity.LivingEntity le) {
            float bh = Math.max(0.3f, preview.getBbHeight()), bw = Math.max(0.3f, preview.getBbWidth());
            float size = Math.min(40f, Math.min((PH - 14) / bh, 96f / bw));
            try { g.entity(le, bx, y + 2, rx + rw - 4, y + PH - 2, size, mx, my); } catch (Throwable ignored) {}
        } else g.itemScaled(shown.icon(), bx + 31, y + 16, 3f);
        Ui.label(g, shown.name(), rx + 5, y + 6, rw - 116, Ui.TEXT);
        Ui.label(g, shown.id(), rx + 5, y + 18, rw - 116, Ui.DIM);
        int ly = y + 32;
        if (shown == selected) for (int i = 0; i < opts.size() && ly < y + PH - 10; i++) {
            if (optIdx[i] == 0) continue;
            Ui.label(g, opts.get(i).names()[optIdx[i]], rx + 5, ly, rw - 116, Ui.DIM);
            ly += 11;
        }
        if (shown == selected && baby && ly < y + PH - 10) g.text("Baby", rx + 5, ly, Ui.DIM);
    }

    // ------------------------------------------------------------------ looks
    private static Opt opt(String label, String key, String[] names, String... values) {
        String[] n = new String[names.length + 1], v = new String[values.length + 1];
        n[0] = "Random"; v[0] = "";
        System.arraycopy(names, 0, n, 1, names.length);
        System.arraycopy(values, 0, v, 1, values.length);
        return new Opt(label, key, n, v);
    }

    private static Opt ints(String label, String key, String suffix, String[] names, int... values) {
        String[] v = new String[values.length];
        for (int i = 0; i < values.length; i++) v[i] = values[i] + suffix;
        return opt(label, key, names, v);
    }

    private static Opt words(String label, String key, String... words) {
        String[] n = new String[words.length], v = new String[words.length];
        for (int i = 0; i < words.length; i++) { n[i] = pretty(words[i]); v[i] = "\"" + words[i] + "\""; }
        return opt(label, key, n, v);
    }

    private static Opt dye(String label, String key) {
        int[] v = new int[16];
        for (int i = 0; i < 16; i++) v[i] = i;
        Opt o = ints(label, key, "b", DYES, v);
        o.names()[0] = "Default";
        return o;
    }

    private static Opt flag(String label, String nbt) { return new Opt(label, "", new String[] { "No", "Yes" }, new String[] { "", nbt }); }

    /** Variants that live in a registry (wolf, cat, frog; pig / cow / chicken on newer versions). null if the registry is missing. */
    private static Opt variants(String registry) {
        List<String> ids = Compat.registryIds(registry);
        return ids.isEmpty() ? null : words("Variant", "variant", ids.toArray(new String[0]));
    }

    private static String pretty(String id) {
        String p = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String s : p.split(" ")) if (!s.isEmpty()) sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).append(' ');
        return sb.toString().trim();
    }

    private static List<Opt> looks(String id) {
        List<Opt> l = new ArrayList<>();
        String tamed = "Owner:" + OWNER;
        switch (id) {
            case "minecraft:wolf" -> { l.add(variants("minecraft:wolf_variant")); l.add(dye("Collar", "CollarColor")); l.add(flag("Tamed (yours)", tamed)); }
            case "minecraft:cat" -> { l.add(variants("minecraft:cat_variant")); l.add(dye("Collar", "CollarColor")); l.add(flag("Tamed (yours)", tamed)); }
            case "minecraft:parrot" -> { l.add(ints("Color", "Variant", "", new String[] { "Red", "Blue", "Green", "Cyan", "Gray" }, 0, 1, 2, 3, 4)); l.add(flag("Tamed (yours)", tamed)); }
            case "minecraft:horse" -> {
                l.add(ints("Color", "Variant#", "", new String[] { "White", "Creamy", "Chestnut", "Brown", "Black", "Gray", "Dark Brown" }, 0, 1, 2, 3, 4, 5, 6));
                l.add(ints("Markings", "Variant#", "", new String[] { "None", "White", "White Field", "White Dots", "Black Dots" }, 0, 256, 512, 768, 1024));
                l.add(flag("Tamed", "Tame:1b"));
            }
            case "minecraft:llama", "minecraft:trader_llama" -> {
                l.add(ints("Color", "Variant", "", new String[] { "Creamy", "White", "Brown", "Gray" }, 0, 1, 2, 3));
                l.add(flag("Tamed", "Tame:1b"));
            }
            case "minecraft:donkey", "minecraft:mule", "minecraft:skeleton_horse", "minecraft:zombie_horse", "minecraft:camel" -> l.add(flag("Tamed", "Tame:1b"));
            case "minecraft:rabbit" -> l.add(ints("Type", "RabbitType", "", new String[] { "Brown", "White", "Black", "Black & White", "Gold", "Salt & Pepper", "Killer Bunny" }, 0, 1, 2, 3, 4, 5, 99));
            case "minecraft:fox" -> l.add(words("Type", "Type", "red", "snow"));
            case "minecraft:axolotl" -> l.add(ints("Color", "Variant", "", new String[] { "Pink", "Brown", "Gold", "Cyan", "Blue" }, 0, 1, 2, 3, 4));
            case "minecraft:mooshroom" -> l.add(words("Type", "Type", "red", "brown"));
            case "minecraft:sheep" -> { l.add(dye("Wool", "Color")); l.add(flag("Sheared", "Sheared:1b")); }
            case "minecraft:shulker" -> l.add(dye("Color", "Color"));
            case "minecraft:frog" -> l.add(variants("minecraft:frog_variant"));
            case "minecraft:pig" -> l.add(variants("minecraft:pig_variant"));
            case "minecraft:cow" -> l.add(variants("minecraft:cow_variant"));
            case "minecraft:chicken" -> l.add(variants("minecraft:chicken_variant"));
            case "minecraft:panda" -> {
                String[] genes = { "normal", "lazy", "worried", "playful", "brown", "weak", "aggressive" };
                l.add(words("Main Gene", "MainGene", genes));
                l.add(words("Hidden Gene", "HiddenGene", genes));
            }
            case "minecraft:goat" -> l.add(flag("Screaming", "IsScreamingGoat:1b"));
            case "minecraft:creeper" -> l.add(flag("Charged", "powered:1b"));
            default -> {}
        }
        l.removeIf(java.util.Objects::isNull);
        return l;
    }

    private void select(Entry e) {
        if (e == selected) return;
        selected = e;
        opts = looks(e.id());
        optIdx = new int[opts.size()];
    }

    /** NBT for the chosen looks (and Baby), shared by the preview and the summon command. */
    private String looksNbt() {
        List<String> t = new ArrayList<>();
        Map<String, Integer> sums = new HashMap<>();   // keys ending in # are added up (horse color + markings)
        for (int i = 0; i < opts.size(); i++) {
            Opt o = opts.get(i);
            String v = o.values()[optIdx[i]];
            if (v.isEmpty()) continue;
            if (o.key().endsWith("#")) sums.merge(o.key().substring(0, o.key().length() - 1), Integer.parseInt(v), Integer::sum);
            else t.add(o.key().isEmpty() ? v : o.key() + ":" + v);
        }
        sums.forEach((k, v) -> t.add(k + ":" + v));
        if (baby) { t.add("IsBaby:1b"); t.add("Age:-24000"); }
        String s = String.join(",", t);
        if (s.contains(OWNER)) s = s.replace(OWNER, ownerUuid());
        return s.isEmpty() ? "" : "{" + s + "}";
    }

    private static String ownerUuid() {
        var p = net.minecraft.client.Minecraft.getInstance().player;
        if (p == null) return "[I;0,0,0,0]";
        java.util.UUID u = p.getUUID();
        long m = u.getMostSignificantBits(), l = u.getLeastSignificantBits();
        return "[I;" + (int) (m >> 32) + "," + (int) m + "," + (int) (l >> 32) + "," + (int) l + "]";
    }

    private static void chip(Gfx g, String label, boolean on, int cx, int cy, int cw, int mx, int my) {
        Ui.rr(g, cx, cy, cw, 16, Ui.in(mx, my, cx, cy, cw, 16) ? Ui.HOVER : Ui.BTN);
        Ui.label(g, label, cx + 4, cy + 4, cw - 38, Ui.TEXT);
        Ui.pill(g, cx + cw - 32, cy + 2, on);
    }

    @Override
    public boolean click(int mx, int my, int btn) {
        if (bar.press(mx, my)) { listScroll = bar.at(my); return true; }
        if (Ui.in(mx, my, x, y, LW, 16)) { search.focused = true; name.focused = false; return true; }
        int rx = rx(), rw = rw(), hw = hw();
        if (Ui.in(mx, my, rx, y + NAME_Y, rw - FRONT_W - 4, 16)) { name.focused = true; search.focused = false; return true; }
        if (Ui.in(mx, my, rx + rw - FRONT_W, y + NAME_Y, FRONT_W, 16)) { front = !front; return true; }
        for (int i = 0; i < opts.size() && i < OPT_ROWS; i++) {
            int d = Ui.cycled(mx, my, btn, rx, y + OPT_Y + i * 18, rw);
            if (d != 0) { optIdx[i] = Math.floorMod(optIdx[i] + d, opts.get(i).names().length); return true; }
        }

        for (int i = 0; i < FILTERS.length; i++) if (Ui.in(mx, my, fx(i), y + 20, FW[i], 14)) { filter = i; return true; }
        int rows = listRows();
        for (int i = 0; i < rows && listScroll + i < view.size(); i++)
            if (Ui.in(mx, my, x, y + LIST + i * ROWH, LW, ROWH - 1)) { select(view.get(listScroll + i)); return true; }

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
        String looks = looksNbt();   // variant, collar, tamed... and Baby
        if (!looks.isEmpty()) t.add(looks.substring(1, looks.length() - 1));
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

    @Override public boolean drag(int mx, int my) { if (!bar.dragging()) return false; listScroll = bar.at(my); return true; }
    @Override public void release() { bar.release(); }

    @Override
    public boolean scroll(int mx, int my, double dy) {
        if (mx >= rx() && my >= y + COUNT_Y && my < y + COUNT_Y + 16) count = Math.max(1, Math.min(64, count + (int) Math.signum(dy))); // wheel over the count row
        else if (mx < x + LW + 4) listScroll -= (int) Math.signum(dy) * 3;
        return true;
    }

    @Override public boolean typed(char c) { return search.focused ? search.typed(c) : name.typed(c); }
    @Override public boolean key(int key, int scan, int mods) { return search.focused ? search.key(key, mods) : name.key(key, mods); }
}
