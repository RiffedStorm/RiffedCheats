package dev.riffedcheats.core;

import dev.riffedcheats.Core;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** The hot-swappable part of the mod: everything the menu and its features do. Loaded by dev.riffedcheats.Loader. */
public final class CoreImpl implements Core {
    @Override public int needsLoader() { return 4; }
    @Override public void start() { Menu.load(); }
    @Override public void shutdown() { Compat.closeMenu(); Features.shutdown(); }
    @Override public void tick() { Features.tick(); }
    @Override public boolean onMessage(Component c) { return Cmd.onMessage(c); }
    @Override public void preAttack() { Features.preAttack(Minecraft.getInstance().player); }
    @Override public void frame() { Features.frameHook = true; Features.frame(); }
    @Override public boolean freecamOn() { return Features.freecamOn(); }
    @Override public void openMenu() { Compat.openMenu(); }
    @Override public void onServer(String data) { Srv.onMessage(data); }
    private boolean hudFailed;
    @Override public void hud(Object g) {
        try {
            Aimbot.frame();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            Gfx gfx = Gfx.of(g);
            Projector pj = new Projector(mc);
            Ores.render(gfx, pj);
            Wallhack.render(gfx, pj);
            Aimbot.drawFov(gfx, pj);
            Aimbot.drawStatus(gfx, pj);
            Waypoints.render(gfx, pj);
        } catch (Throwable t) {
            if (!hudFailed) { hudFailed = true; System.err.println("[RiffedCheats] HUD drawing failed:"); t.printStackTrace(); }
        }
    }
}
