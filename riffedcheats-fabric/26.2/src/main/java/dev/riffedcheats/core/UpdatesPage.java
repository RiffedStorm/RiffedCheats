package dev.riffedcheats.core;

import java.util.List;

/** Settings > Updates: installed / latest version, check now, one-click update with hot swap. */
public final class UpdatesPage implements Page {
    private int x, y, w, h;

    private int btnY() { return y + 64; }
    private int hw() { return (w - 4) / 2; }

    @Override
    public void render(Gfx g, int mx, int my, int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        Updater.State s = Updater.state;
        g.text("Installed: " + Updater.current() + "  (" + Compat.buildName() + ")", x, y + 2, Ui.TEXT);
        g.text("Latest on GitHub: " + (Updater.latest == null ? "not checked yet" : Updater.latest), x, y + 16, Ui.TEXT);
        int col = switch (s) {
            case AVAILABLE, RESTART -> Ui.ACCENT;
            case UP_TO_DATE -> Ui.GREEN;
            case FAILED -> Ui.RED;
            default -> Ui.DIM;
        };
        List<String> st = Ui.wrap(g, Updater.status, w);
        for (int i = 0; i < Math.min(2, st.size()); i++) g.text(st.get(i), x, y + 34 + i * 11, col);

        int by = btnY(), hw = hw();
        boolean busy = s == Updater.State.CHECKING || s == Updater.State.DOWNLOADING;
        Ui.button(g, busy ? "Working..." : "Check Now", x, by, hw, 20, Ui.in(mx, my, x, by, hw, 20), false);
        boolean can = s == Updater.State.AVAILABLE || Updater.canReinstall();
        String up = s == Updater.State.AVAILABLE ? "Update to " + Updater.latest : can ? "Reinstall " + Updater.latest : "No update to install";
        Ui.selButton(g, up, x + hw + 4, by, hw, 20, can && Ui.in(mx, my, x + hw + 4, by, hw, 20), s == Updater.State.AVAILABLE);

        int ty = by + 26;
        Ui.rr(g, x, ty, w, 20, Ui.in(mx, my, x, ty, w, 20) ? Ui.HOVER : Ui.BTN);
        g.text("Check for updates when the menu opens", x + 8, ty + 6, Ui.TEXT);
        Ui.pill(g, x + w - 36, ty + 4, Menu.UPDATE_CHECK.on);

        String[] info = {
            "Update downloads the " + Compat.buildName() + " jar from github.com/" + "RiffedStorm/RiffedCheats/releases into your mods folder, removes the old jar and reloads the menu right away: no restart, you can stay in your world.",
            "Your settings are kept. If an update ever changes the part of the mod that cannot be reloaded, it says so and the new version starts with your next launch.",
            "Reinstall downloads the version you already have again and reloads it the same way." };
        int ly = ty + 30;
        for (String l : info) for (String line : Ui.wrap(g, l, w)) { g.text(line, x, ly, Ui.DIM); ly += 11; }
    }

    @Override
    public boolean click(int mx, int my, int btn) {
        int by = btnY(), hw = hw();
        if (Ui.in(mx, my, x, by, hw, 20)) { Updater.check(); return true; }
        if (Ui.in(mx, my, x + hw + 4, by, hw, 20)) { Updater.update(); return true; }
        if (Ui.in(mx, my, x, by + 26, w, 20)) { Menu.UPDATE_CHECK.on = !Menu.UPDATE_CHECK.on; Menu.changed(); return true; }
        return false;
    }
}
