package dev.riffedcheats.core;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Draws a marker on screen for every saved location with its Marker switched on, in the dimension you are in. */
public final class Waypoints {
    private static final int COLOR = 0xFF6CE0B0;

    public static void render(Gfx g) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !Access.allowed || Compat.hudHidden()) return;
        List<Teleports.Loc> list = Teleports.waypoints();
        if (list.isEmpty()) return;

        double[] cam = Compat.camera();      // x, y, z, yaw, pitch
        double yaw = Math.toRadians(cam[3]), pitch = Math.toRadians(cam[4]);
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;          // up = right x forward

        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        double scale = (h / 2.0) / Math.tan(Math.toRadians(Compat.fov()) / 2.0);

        for (Teleports.Loc l : list) {
            double dx = l.x() - cam[0], dy = l.y() + 1 - cam[1], dz = l.z() - cam[2];
            double z = dx * fx + dy * fy + dz * fz;
            if (z < 0.1) continue;      // behind you
            int sx = (int) Math.round(w / 2.0 + (dx * rx + dz * rz) / z * scale);
            int sy = (int) Math.round(h / 2.0 - (dx * ux + dy * uy + dz * uz) / z * scale);
            if (sx < -40 || sx > w + 40 || sy < -40 || sy > h + 40) continue;
            draw(g, sx, sy, l.name(), Math.sqrt(p.distanceToSqr(l.x(), l.y(), l.z())));
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
