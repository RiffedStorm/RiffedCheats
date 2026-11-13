package dev.riffedcheats.core;

import static dev.riffedcheats.core.Menu.*;

import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/** 2D boxes around the selected mobs and players, seen through walls, with a health bar and optional labels. */
final class Wallhack {
    private static final int HOSTILE = 0xFFFF5A5A, FRIENDLY = 0xFF5AE08A, PLAYER = 0xFF5AAEFF, OTHER = 0xFFCC8CFF;

    private static void rect(Gfx g, int x0, int y0, int x1, int y1, int t, int c) {
        g.fill(x0, y0, x1, y0 + t, c);
        g.fill(x0, y1 - t, x1, y1, c);
        g.fill(x0, y0 + t, x0 + t, y1 - t, c);
        g.fill(x1 - t, y0 + t, x1, y1 - t, c);
    }

    private static void small(Gfx g, String s, int cx, int y, int color) {
        float sc = 0.75f;
        g.fill(cx - (int) (g.width(s) * sc / 2) - 1, y - 1, cx + (int) (g.width(s) * sc / 2) + 2, y + 7, 0x70000000);
        g.push();
        g.translate(cx - g.width(s) * sc / 2f, y);
        g.scale(sc);
        g.text(s, 0, 0, color);
        g.pop();
    }

    private static int color(Entity e) {
        if (Aimbot.isTarget(e)) return 0xFFFFFFFF;
        return switch (Targets.kind(e)) {
            case HOSTILE -> HOSTILE;
            case FRIENDLY -> FRIENDLY;
            case PLAYER -> PLAYER;
            default -> OTHER;
        };
    }

    static void render(Gfx g, Projector pj) {
        if (!ESP_ON.active() || Compat.hudHidden()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        Set<String> custom = ESP_CUSTOM.on ? Targets.list("esp_custom") : Set.of();
        double r2 = ESP_RANGE.value * ESP_RANGE.value;
        float pt = Compat.partial();
        for (Entity e : Compat.entities(mc.level)) {
            double d2 = p.distanceToSqr(e);
            if (d2 > r2 || !Targets.selected(e, ESP_HOSTILE.on, ESP_FRIENDLY.on, ESP_PLAYERS.on, custom)) continue;
            if (!Targets.allowed(e, p, ESP_TEAM.on, ESP_SPARE.on, !ESP_INVIS.on)) continue;
            if (ESP_CANHIT.on && !Aimbot.canHit(e)) continue;
            draw(g, pj, e, pt, Math.sqrt(d2));
        }
    }

    private static void draw(Gfx g, Projector pj, Entity e, float pt, double dist) {
        double ox = Mth.lerp(pt, e.xo, e.getX()) - e.getX(), oy = Mth.lerp(pt, e.yo, e.getY()) - e.getY(), oz = Mth.lerp(pt, e.zo, e.getZ()) - e.getZ();
        AABB b = e.getBoundingBox().move(ox, oy, oz);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double[] s = pj.project((i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY, (i & 4) == 0 ? b.minZ : b.maxZ);
            if (s == null) return;
            minX = Math.min(minX, s[0]); maxX = Math.max(maxX, s[0]);
            minY = Math.min(minY, s[1]); maxY = Math.max(maxY, s[1]);
        }
        if (maxX < 0 || minX > pj.w || maxY < 0 || minY > pj.h) return;

        int x0 = (int) Math.floor(minX), y0 = (int) Math.floor(minY), x1 = Math.max(x0 + 3, (int) Math.ceil(maxX)), y1 = Math.max(y0 + 3, (int) Math.ceil(maxY));
        int c = color(e), cx = (x0 + x1) / 2, t = ESP_THICK.intValue();
        if (ESP_TRACERS.on) tracer(g, pj.w / 2, pj.h, cx, y1, c);
        if (ESP_BOX.on) {
            rect(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, t + 2 > 3 ? 3 : t + 2, 0x90000000);
            rect(g, x0, y0, x1, y1, t, c);
        }

        LivingEntity live = e instanceof LivingEntity l ? l : null;
        if (ESP_HEALTH.on && live != null) {
            float f = Mth.clamp(live.getHealth() / Math.max(1f, live.getMaxHealth()), 0f, 1f);
            int bar = 0xFF000000 | (int) ((1 - f) * 230 + 25) << 16 | (int) (f * 200 + 30) << 8 | 60;
            int h = y1 - y0, fill = Math.max(1, Math.round(h * f));
            g.fill(x0 - 5, y0 - 1, x0 - 2, y1 + 1, 0xA0000000);
            g.fill(x0 - 4, y1 - fill, x0 - 3, y1, bar);
        }

        String top = ESP_NAMES.on ? e.getDisplayName().getString() : "";
        if (ESP_HP_TEXT.on && live != null) top += (top.isEmpty() ? "" : " ") + Math.round(live.getHealth()) + "/" + Math.round(live.getMaxHealth());
        if (!top.isEmpty()) small(g, top, cx, y0 - 9, 0xFFFFFFFF);
        if (ESP_DIST.on) small(g, Math.round(dist) + "m", cx, y1 + 2, c);
    }

    private static void tracer(Gfx g, int x0, int y0, int x1, int y1, int c) {
        int steps = Math.min(600, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) / 2);
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / Math.max(1, steps), y = y0 + (y1 - y0) * i / Math.max(1, steps);
            g.fill(x, y, x + 1, y + 1, c & 0xB0FFFFFF);
        }
    }
}
