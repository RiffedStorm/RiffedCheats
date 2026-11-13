package dev.riffedcheats.core;

/** Settings > Server: which way the menu talks to the world (Auto / Client Side / Server Side) and what each way can do. */
public final class ServerPage implements Page {
    private static final String[] MODES = { "Auto", "Client Side", "Server Side" };
    private int x, y, w, h;

    private int bw() { return (w - 8) / 3; }

    @Override
    public void render(Gfx g, int mx, int my, int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        g.text("Connection mode", x, y + 2, Ui.DIM);
        int mode = Menu.CONN.intValue(), bw = bw();
        for (int i = 0; i < 3; i++) {
            int bx = x + i * (bw + 4);
            if (i == 2 && !Srv.present()) Ui.greyButton(g, MODES[i], bx, y + 14, bw, 20);
            else Ui.selButton(g, MODES[i], bx, y + 14, bw, 20, Ui.in(mx, my, bx, y + 14, bw, 20), mode == i);
        }

        int ty = y + 44;
        boolean srv = Access.server();
        String detected = Srv.outdated() ? "Found here, but a different version: update it on the server."
                : Srv.present() ? (Srv.version().isEmpty() ? "Found here." : "Found here, version " + Srv.version() + ".")
                : "Not on this server.";
        g.text("Server part of the mod:", x, ty, Ui.DIM);
        Ui.label(g, detected, x + g.width("Server part of the mod:") + 6, ty, w - g.width("Server part of the mod:") - 6, Srv.present() && !Srv.outdated() ? Ui.GREEN : Ui.TEXT);
        ty += 13;
        g.text("Now using:", x, ty, Ui.DIM);
        g.text(Access.label(), x + g.width("Now using:") + 6, ty, srv ? Ui.GREEN : Access.allowed ? 0xFFE0C04C : Ui.RED);
        ty += 18;

        if (mode == 2 && !srv && Access.allowed) { ty = para(g, "Server Side is not possible here, so the menu is using Client Side.", ty, 0xFFE0C04C); ty += 4; }
        if (!srv && Access.mode == Access.Mode.OP) {
            ty = para(g, feedbackWarning(), ty, feedbackOn() ? Ui.RED : Ui.TEXT);
            ty += 4;
        }
        ty = para(g, "Server Side: put this same jar in the server's mods folder (singleplayer and Open to LAN already have it). "
                + "Players without the mod can still join. Only operators can use it.", ty, Ui.DIM);
        ty += 6;
        g.text("Only with Server Side (grey elsewhere):", x, ty, Ui.TEXT);
        ty += 12;
        para(g, "real God Mode, Noclip, Infinite Items, Instant Shield Block, No Shield Cooldown, Auto Repair, Item Magnet. "
                + "Also: nothing in anyone's chat, no command length limit, full Console output, and Flight without allow-flight.", ty, Ui.DIM);
    }

    static boolean feedbackOn() { return !Boolean.FALSE.equals(Rules.get(Compat.gamerule("feedback"))); }

    private static String feedbackWarning() {
        return feedbackOn()
                ? "Warning: command feedback is on here, so other operators see a chat line for each command the menu sends. "
                        + "Effects now use one command per toggle, so it is rare, but only sendCommandFeedback off or Server Side removes it."
                : "Command feedback is off here, so other operators do not see the menu's commands.";
    }

    private int para(Gfx g, String s, int ty, int color) {
        for (String l : Ui.wrap(g, s, w - 4)) { g.text(l, x, ty, color); ty += 11; }
        return ty;
    }

    @Override
    public boolean click(int mx, int my, int btn) {
        int bw = bw();
        for (int i = 0; i < 3; i++) {
            if (i == 2 && !Srv.present()) continue;
            if (Ui.in(mx, my, x + i * (bw + 4), y + 14, bw, 20)) { Menu.CONN.value = i; Menu.changed(); return true; }
        }
        return false;
    }
}
