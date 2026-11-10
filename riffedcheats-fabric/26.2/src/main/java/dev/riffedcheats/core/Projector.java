package dev.riffedcheats.core;

import net.minecraft.client.Minecraft;

/** Turns a world position into a position on the HUD for the current frame, the same way the game's camera does (view bobbing included). */
final class Projector {
    final int w, h;
    final double scale;
    private final double cx, cy, cz, fx, fy, fz, rx, rz, ux, uy, uz;
    private final double tx, ty, ca, sa, cb, sb;     // view bobbing

    Projector(Minecraft mc) {
        double[] cam = Compat.camera();      // x, y, z, yaw, pitch
        cx = cam[0]; cy = cam[1]; cz = cam[2];
        double yaw = Math.toRadians(cam[3]), pitch = Math.toRadians(cam[4]);
        fx = -Math.sin(yaw) * Math.cos(pitch); fy = -Math.sin(pitch); fz = Math.cos(yaw) * Math.cos(pitch);
        rx = -Math.cos(yaw); rz = -Math.sin(yaw);
        ux = -rz * fy; uy = rz * fx - rx * fz; uz = rx * fy;       // up = right x forward
        w = mc.getWindow().getGuiScaledWidth();
        h = mc.getWindow().getGuiScaledHeight();
        scale = (h / 2.0) / Math.tan(Math.toRadians(Compat.fov()) / 2.0);

        double[] b = Compat.bob();           // walk phase, bob strength; null when off
        double a = 0, c = 0, ox = 0, oy = 0;
        if (b != null) {
            double s = Math.sin(b[0] * Math.PI);
            ox = s * b[1] * 0.5;
            oy = -Math.abs(Math.cos(b[0] * Math.PI) * b[1]);
            c = Math.toRadians(s * b[1] * 3);
            a = Math.toRadians(Math.abs(Math.cos(b[0] * Math.PI - 0.2) * b[1]) * 5);
        }
        tx = ox; ty = oy;
        ca = Math.cos(a); sa = Math.sin(a); cb = Math.cos(c); sb = Math.sin(c);
    }

    /** {screen x, screen y}, or null when the point is behind the camera. */
    double[] project(double x, double y, double z) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        double vx = dx * rx + dz * rz, vy = dx * ux + dy * uy + dz * uz, vz = -(dx * fx + dy * fy + dz * fz);
        double y1 = vy * ca - vz * sa, z1 = vy * sa + vz * ca;
        double x2 = vx * cb - y1 * sb + tx, y2 = vx * sb + y1 * cb + ty;
        if (z1 > -0.05) return null;
        return new double[] { w / 2.0 + x2 / -z1 * scale, h / 2.0 - y2 / -z1 * scale };
    }
}
