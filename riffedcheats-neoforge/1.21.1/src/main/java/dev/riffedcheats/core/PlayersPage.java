package dev.riffedcheats.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Online player list with admin actions (needs OP on servers). Think "GTA Online player options". */
public final class PlayersPage implements Page {
    private record Act(String label, Function<String, String[]> cmds, boolean danger) {}

    private static Act a(String l, Function<String, String[]> f) { return new Act(l, f, false); }
    private static Act d(String l, Function<String, String[]> f) { return new Act(l, f, true); }
    private static String[] c(String... s) { return s; }

    /** Important one: gets its own full-width button above the grid. */
    private static final Act RESET = a("Reset Attributes + Effects", Features::resetCommands);
    private static final int GRID = 36, PITCH = 19;

    private static final List<Act> ACTS = List.of(
        a("Teleport To", n -> c("tp @s " + n)), a("Bring Here", n -> c("tp " + n + " @s")),
        a("Heal + Feed", n -> c("effect give " + n + " minecraft:instant_health 1 10 true", "effect give " + n + " minecraft:saturation 1 10 true")),
        a("Clear Inventory", n -> c("clear " + n)),
        a("Creative", n -> c("gamemode creative " + n)), a("Survival", n -> c("gamemode survival " + n)),
        a("Adventure", n -> c("gamemode adventure " + n)), a("Spectator", n -> c("gamemode spectator " + n)),
        a("Gift God Armor", Gear::armor), a("Gift God Tools", Gear::tools),
        a("Launch Skyward", n -> c("effect give " + n + " minecraft:levitation 2 40 true")),
        a("Freeze", n -> c("effect give " + n + " minecraft:slowness 15 255 true", "effect give " + n + " minecraft:jump_boost 15 250 true")),
        a("Blind", n -> c("effect give " + n + " minecraft:blindness 10 0 true")),
        a("Nausea", n -> c("effect give " + n + " minecraft:nausea 15 0 true")),
        a("Glow (reveal)", n -> c("effect give " + n + " minecraft:glowing 60 0 true")),
        a("Trap in Glass", n -> c("execute at " + n + " run fill ~-2 ~-1 ~-2 ~2 ~3 ~2 minecraft:glass hollow")),
        d("Lightning", n -> c("execute at " + n + " run summon minecraft:lightning_bolt")),
        d("Explode", n -> c("execute at " + n + " run summon minecraft:tnt ~ ~ ~ {fuse:0}")),
        d("Set on Fire", n -> c("execute at " + n + " run setblock ~ ~ ~ minecraft:fire")),
        d("Kill", n -> c("kill " + n)),
        d("Kick", n -> c("kick " + n + " Kicked by an operator")));

    private String selected;
    private int scroll;
    private final Scrollbar bar = new Scrollbar();
    /** Inventory editor of the selected player; while open it takes over the whole tab. */
    private InventoryPage inv;
    private final InventoryPage invPage = new InventoryPage(() -> selected, () -> inv = null);
    private int x, y, w, h;
    private static final int LW = 124;

    private List<String> names() { return Compat.onlinePlayers(); }

    @Override public boolean wantsText() { return inv != null; }
    @Override public void reselect() { inv = null; }

    @Override
    public void render(Gfx g, int mx, int my, int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        List<String> names = names();
        if (inv != null && selected != null && names.contains(selected)) { inv.render(g, mx, my, x, y, w, h); return; }
        inv = null;
        if (selected != null && !names.contains(selected)) selected = null;
        if (selected == null && !names.isEmpty()) selected = names.get(0);

        g.text("Players (" + names.size() + ")", x, y, Ui.DIM);
        int rows = (h - 14) / 18;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, names.size() - rows)));
        bar.draw(g, x + LW + 2, y + 14, rows * 18 - 1, names.size(), rows, scroll, mx, my);
        for (int i = 0; i < rows && scroll + i < names.size(); i++) {
            String n = names.get(scroll + i);
            int ry = y + 14 + i * 18;
            boolean sel = n.equals(selected);
            Ui.rr(g, x, ry, LW, 17, sel ? Ui.SEL : Ui.in(mx, my, x, ry, LW, 17) ? Ui.HOVER : Ui.BTN);
            g.text(Ui.fit(g, n, LW - 10), x + 5, ry + 5, Ui.TEXT);
        }

        int rx = x + LW + 8, rw = w - LW - 8;
        if (selected == null) { g.text("No players online.", rx, y, Ui.DIM); return; }
        g.text(Ui.fit(g, "Target: " + selected, rw - 80), rx, y, Ui.TEXT);
        Ui.button(g, "Inventory", rx + rw - 74, y - 3, 74, 14, Ui.in(mx, my, rx + rw - 74, y - 3, 74, 14), false);
        int bw = (rw - 4) / 2;
        for (int i = 0; i < ACTS.size(); i++) {
            int bx = rx + (i % 2) * (bw + 4), by = y + GRID + (i / 2) * PITCH;
            Ui.button(g, ACTS.get(i).label(), bx, by, bw, 17, Ui.in(mx, my, bx, by, bw, 17), ACTS.get(i).danger());
        }
        Ui.button(g, RESET.label(), rx, y + 14, rw, 18, Ui.in(mx, my, rx, y + 14, rw, 18), false);
        int ey = y + GRID + ((ACTS.size() + 1) / 2) * PITCH + 3;
        g.text("Everyone", rx, ey, Ui.DIM);
        String[] el = { "Bring All", "Heal All", "Feed All", "Reset All" };
        int ew = (rw - 12) / 4;
        for (int i = 0; i < 4; i++) {
            int bx = rx + i * (ew + 4);
            Ui.button(g, el[i], bx, ey + 11, ew, 18, Ui.in(mx, my, bx, ey + 11, ew, 18), false);
        }
    }

    @Override
    public boolean click(int mx, int my, int btn) {
        if (inv != null) return inv.click(mx, my, btn);
        if (bar.press(mx, my)) { scroll = bar.at(my); return true; }
        List<String> names = names();
        int rows = (h - 14) / 18;
        for (int i = 0; i < rows && scroll + i < names.size(); i++)
            if (Ui.in(mx, my, x, y + 14 + i * 18, LW, 17)) { selected = names.get(scroll + i); return true; }
        int rx = x + LW + 8, rw = w - LW - 8, bw = (rw - 4) / 2;
        if (selected != null) {
            if (Ui.in(mx, my, rx + rw - 74, y - 3, 74, 14)) {
                inv = invPage;
                invPage.reload();
                if (selected.equals(Compat.selfName())) inv = new InventoryPage(null, () -> inv = null); // yourself: live client copy
                return true;
            }
            for (int i = 0; i < ACTS.size(); i++) {
                int bx = rx + (i % 2) * (bw + 4), by = y + GRID + (i / 2) * PITCH;
                if (Ui.in(mx, my, bx, by, bw, 17)) { Cmd.run(ACTS.get(i).cmds().apply(selected)); return true; }
            }
            if (Ui.in(mx, my, rx, y + 14, rw, 18)) { Cmd.run(RESET.cmds().apply(selected)); return true; }
        }
        int ey = y + GRID + ((ACTS.size() + 1) / 2) * PITCH + 3, ew = (rw - 12) / 4;
        String[][] all = { { "tp @a @s" }, { "effect give @a minecraft:instant_health 1 10 true" }, { "effect give @a minecraft:saturation 1 10 true" },
                           Features.resetCommands("@a") };
        for (int i = 0; i < 4; i++)
            if (Ui.in(mx, my, rx + i * (ew + 4), ey + 11, ew, 18)) { Cmd.run(all[i]); return true; }
        return false;
    }

    @Override
    public boolean drag(int mx, int my) {
        if (inv != null) return inv.drag(mx, my);   // lets drag-select work in the inventory editor
        if (!bar.dragging()) return false;
        scroll = bar.at(my);
        return true;
    }

    @Override public void release() { if (inv != null) inv.release(); bar.release(); }

    @Override
    public boolean scroll(int mx, int my, double dy) {
        if (inv != null) return inv.scroll(mx, my, dy);
        scroll -= (int) Math.signum(dy);
        return true;
    }

    @Override public boolean typed(char c) { return inv != null && inv.typed(c); }
    @Override public boolean key(int key, int scan, int mods) { return inv != null && inv.key(key, scan, mods); }
}
