package dev.riffedcheats.core;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Draws a marker on screen for every saved location with its Marker switched on, in the dimension you are in. */
public final class Waypoints {
    private static final int COLOR = 0xFF6CE0B0;

    static void render(Gfx g, Projector pj) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !Access.allowed || Compat.hudHidden()) return;
        List<Teleports.Loc> list = Teleports.waypoints();
        for (Teleports.Loc l : list) {
            double[] s = pj.project(l.x(), l.y() + 1, l.z());
            if (s == null || s[0] < -40 || s[0] > pj.w + 40 || s[1] < -40 || s[1] > pj.h + 40) continue;
            draw(g, (int) Math.round(s[0]), (int) Math.round(s[1]), l.name(), Math.sqrt(p.distanceToSqr(l.x(), l.y(), l.z())));
        }
    }

    private static void draw(Gfx g, int x, int y, String name, double dist) {
        for (int i = 0; i < 5; i++) g.fill(x - 4 + i, y - i, x + 5 - i, y + i + 1, COLOR);     // diamond
        String m = dist >= 10000 ? Math.round(dist / 1000) + "km" : Math.round(dist) + "m";
        int nw = g.width(name), mw = g.width(m), bw = Math.max(nw, mw) + 6;
        g.fill(x - bw / 2, y - 27, x + bw / 2 + 1, y - 6, 0x90000000);
        g.text(name, x - nw / 2, y - 25, 0xFFFFFFFF);
        g.text(m, x - mw / 2, y - 15, COLOR);
    }
}
