package dev.riffedcheats.core;

import static dev.riffedcheats.core.Menu.*;

import dev.riffedcheats.RiffedCheats;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Turns your view toward the best target. Targets are picked every tick; the view moves every frame so it stays smooth. */
public final class Aimbot {
    private static final Map<Integer, Double> HITTABLE = new HashMap<>();   // entity id -> height of the point to aim at
    private static Entity target;
    private static boolean toggled;
    private static long lastFrame, lastSwing;

    static void reset() { target = null; HITTABLE.clear(); }

    /** Wallhack's "only if Aimbot can hit": what Aimbot would accept right now, wherever you are looking. */
    static boolean canHit(Entity e) { return HITTABLE.containsKey(e.getId()); }
    static boolean isTarget(Entity e) { return e == target; }

    private static boolean aiming(LocalPlayer p) {
        if (!AIM_ON.active() || !p.isAlive() || p.isSpectator()) return false;
        return switch (AIM_KEYMODE.intValue()) {
            case 1 -> toggled;
            case 2 -> true;
            case 3 -> fighting(p);
            default -> RiffedCheats.AIM.isDown();
        };
    }

    /** Holding right click with this counts as attacking: bows, tridents and modded weapons like Cataclysm's Meat Shredder. */
    private static boolean heldAttack(ItemStack use) {
        return switch (use.getUseAnimation().name()) {
            case "EAT", "DRINK", "BLOCK", "SPYGLASS", "TOOT_HORN", "BRUSH", "BUNDLE" -> false;
            default -> true;
        };
    }

    /** On Attack mode: swinging at something, holding right click with a weapon, or holding a loaded crossbow. */
    private static boolean fighting(LocalPlayer p) {
        if (p.isUsingItem() && heldAttack(p.getUseItem())) return true;
        ItemStack main = p.getMainHandItem();
        if (main.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(main)) return true;
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        // holding attack on a block is mining, unless a fight is already going on
        boolean mining = target == null && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
        if (mc.options.keyAttack.isDown() && !mining) lastSwing = now;
        return now - lastSwing < 400;       // stays on between separate clicks
    }

    private static boolean clear(ClientLevel level, LocalPlayer p, Vec3 eye, Entity e, double y) {
        Vec3 to = new Vec3(e.getX(), e.getY() + y, e.getZ());
        return level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }

    /** Height above the feet to aim at: the chosen spot if it can be seen, else any other that can. NaN when none can. */
    private static double aimPoint(ClientLevel level, LocalPlayer p, Vec3 eye, Entity e) {
        double[] points = { e.getEyeHeight(), e.getBbHeight() * 0.5, e.getBbHeight() * 0.1 };
        int first = AIM_BONE.intValue();
        if (AIM_WALLS.on) return points[first];
        for (int i = 0; i < 3; i++) {
            double y = points[(first + i) % 3];
            if (clear(level, p, eye, e, y)) return y;
        }
        return Double.NaN;
    }

    private static double[] angles(LocalPlayer p, Vec3 from, double x, double y, double z) {
        double dx = x - from.x, dy = y - from.y, dz = z - from.z;
        return new double[] { Math.toDegrees(Math.atan2(dz, dx)) - 90.0, -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))) };
    }

    private static double offAngle(LocalPlayer p, Vec3 eye, Entity e, double y) {
        double[] a = angles(p, eye, e.getX(), e.getY() + y, e.getZ());
        return Math.hypot(Mth.wrapDegrees(a[0] - p.getYRot()), a[1] - p.getXRot());
    }

    private static double score(LocalPlayer p, Vec3 eye, Entity e, double y) {
        return switch ((ranged(p) ? AIM_PRIORITY_BOW : AIM_PRIORITY).intValue()) {
            case 1 -> p.distanceToSqr(e);
            case 2 -> e instanceof LivingEntity l ? l.getHealth() : Double.MAX_VALUE;
            default -> offAngle(p, eye, e, y);
        };
    }

    /** Every client tick. */
    static void tick(LocalPlayer p, ClientLevel level, Minecraft mc) {
        while (RiffedCheats.AIM.consumeClick()) if (AIM_KEYMODE.intValue() == 1) toggled = !toggled;
        HITTABLE.clear();
        if (!AIM_ON.active() && !(ESP_ON.active() && ESP_CANHIT.on)) { target = null; return; }

        Set<String> custom = AIM_CUSTOM.on ? Targets.list("aim_custom") : Set.of();
        Vec3 eye = p.getEyePosition();
        double range = ranged(p) ? AIM_RANGE_BOW.value : AIM_RANGE.value, r2 = range * range;
        for (Entity e : Compat.entities(level)) {
            if (p.distanceToSqr(e) > r2 || !Targets.selected(e, AIM_HOSTILE.on, AIM_FRIENDLY.on, AIM_PLAYERS.on, custom)) continue;
            if (!Targets.allowed(e, p, AIM_TEAM.on, AIM_SPARE.on, AIM_INVIS.on)) continue;
            double y = aimPoint(level, p, eye, e);
            if (!Double.isNaN(y)) HITTABLE.put(e.getId(), y);
        }

        if (!aiming(p)) { target = null; return; }
        double cone = AIM_FOV.value >= 355 ? Double.MAX_VALUE : AIM_FOV.value / 2.0;
        boolean keep = AIM_STICKY.on && target != null && HITTABLE.containsKey(target.getId())
                && offAngle(p, eye, target, HITTABLE.get(target.getId())) <= cone;
        if (!keep) {
            Entity best = null;
            double bestScore = Double.MAX_VALUE;
            for (Entity e : Compat.entities(level)) {
                Double y = HITTABLE.get(e.getId());
                if (y == null || offAngle(p, eye, e, y) > cone) continue;
                double s = score(p, eye, e, y);
                if (s < bestScore) { bestScore = s; best = e; }
            }
            target = best;
        }

        if (target != null && AIM_AUTOHIT.on && target instanceof LivingEntity && p.getAttackStrengthScale(0.5f) >= 1f
                && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() == target) {
            Features.preAttack(p);
            Compat.attack(target);
            p.swing(InteractionHand.MAIN_HAND);
        }
        if (System.currentTimeMillis() - lastFrame > 100) frame();      // the HUD is not being drawn (F1)
    }

    /** Holding a bow or crossbow (modded ones too), or charging a trident to throw it: the ranged distance applies. */
    private static boolean ranged(LocalPlayer p) {
        return p.getMainHandItem().getItem() instanceof ProjectileWeaponItem
                || p.isUsingItem() && (p.getUseItem().getItem() instanceof ProjectileWeaponItem || p.getUseItem().getItem() instanceof TridentItem);
    }

    /** Arrow speed of what you are using, 0 for anything that is not a projectile weapon. */
    private static double projectileSpeed(LocalPlayer p) {
        ItemStack use = p.isUsingItem() ? p.getUseItem() : ItemStack.EMPTY;
        if (use.getItem() instanceof BowItem) return 3.0;     // always as if fully drawn: a barely drawn bow would need aiming nearly straight up
        if (use.getItem() instanceof TridentItem) return 2.5;
        return p.getMainHandItem().getItem() instanceof CrossbowItem ? 3.15 : 0;
    }

    private static final int MAX_FLIGHT = 100;

    /** Ticks an arrow needs to cover this horizontal distance, with the 1% air drag per tick. */
    private static double flightTicks(double distance, double speed) {
        double left = 1 - 0.01 * distance / speed;
        return left <= 0 ? MAX_FLIGHT : Math.min(MAX_FLIGHT, Math.log(left) / Math.log(0.99));
    }

    /** How far an arrow has fallen after this many ticks (gravity 0.05 per tick, same drag). */
    private static double arrowDrop(double ticks) {
        double drop = 0, fall = 0;
        for (int i = 0; i < (int) ticks; i++) { drop += fall; fall = fall * 0.99 + 0.05; }
        return drop + fall * (ticks - (int) ticks);
    }

    /** Every frame: moves your view part of the way to the target. */
    static void frame() {
        long now = System.currentTimeMillis();
        double dt = Math.min(0.05, (now - lastFrame) / 1000.0);
        lastFrame = now;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || target == null || Compat.screenOpen() || !aiming(p)) return;
        if (!target.isAlive() || target.isRemoved()) { target = null; return; }
        Double h = HITTABLE.get(target.getId());
        if (h == null) return;

        float pt = Compat.partial();
        Vec3 eye = p.getEyePosition(pt);
        double vx = target.getX() - target.xo, vy = target.getY() - target.yo, vz = target.getZ() - target.zo;
        if (vx * vx + vy * vy + vz * vz > 4) vx = vy = vz = 0;     // it teleported, that is not movement to lead
        double x = Mth.lerp(pt, target.xo, target.getX()), y = Mth.lerp(pt, target.yo, target.getY()) + h, z = Mth.lerp(pt, target.zo, target.getZ());

        double speed = AIM_BOW.on ? projectileSpeed(p) : 0;
        if (speed > 0) {
            double t = 0, ax = x, ay = y, az = z;
            for (int i = 0; i < 3; i++) {       // where it will be when the arrow arrives
                t = flightTicks(Math.hypot(ax - eye.x, az - eye.z), speed);
                ax = x + vx * t; ay = y + Mth.clamp(vy * t, -3, 3); az = z + vz * t;
            }
            x = ax; z = az;
            y = ay + Math.min(arrowDrop(t), Math.hypot(ax - eye.x, az - eye.z));     // never more than 45 degrees of correction
        } else if (AIM_LEAD.on) { x += vx; y += vy; z += vz; }

        double[] a = angles(p, eye, x, y, z);
        double dy = Mth.wrapDegrees(a[0] - p.getYRot()), dp = a[1] - p.getXRot();
        double s = AIM_SMOOTH.value / 100.0;
        double k = s <= 0 ? 1 : 1 - Math.exp(-dt * (40 * (1 - s) + 2));
        float yaw = (float) (p.getYRot() + dy * k), pitch = Mth.clamp((float) (p.getXRot() + dp * k), -90f, 90f);
        p.yRotO += yaw - p.getYRot();
        p.xRotO += pitch - p.getXRot();
        p.setYRot(yaw);
        p.setXRot(pitch);
    }

    /** "Aimbot: ON" / "Aimbot: OFF" on the HUD, with the current target's name while locked on. */
    static void drawStatus(Gfx g, Projector pj) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !AIM_ON.active() || !AIM_STATUS.on || Compat.hudHidden()) return;
        boolean on = aiming(p);
        String s = "Aimbot: " + (on ? "ON" : "OFF");
        if (on && target != null) s += " - " + target.getDisplayName().getString();
        int tw = g.width(s), pos = AIM_STATUS_POS.intValue();
        int x = pos == 0 ? 4 : pos == 1 ? pj.w - tw - 4 : (pj.w - tw) / 2;
        int y = pos == 0 ? 4 : pos == 1 ? 16 : pj.h / 2 + 12;      // Top Right starts below where an FPS counter usually sits
        g.fill(x - 2, y - 2, x + tw + 2, y + 9, 0x70000000);
        g.text(s, x, y, on ? 0xFF5AE08A : 0xFFFF7A7A);
    }

    /** The circle that shows Aimbot's field of view. */
    static void drawFov(Gfx g, Projector pj) {
        if (!AIM_ON.active() || !AIM_SHOW_FOV.on || AIM_FOV.value >= 170) return;
        double r = Math.tan(Math.toRadians(AIM_FOV.value / 2.0)) * pj.scale;
        int n = (int) Math.max(32, r * 2);
        for (int i = 0; i < n; i++) {
            int x = (int) Math.round(pj.w / 2.0 + Math.cos(i * 2 * Math.PI / n) * r), y = (int) Math.round(pj.h / 2.0 + Math.sin(i * 2 * Math.PI / n) * r);
            g.fill(x, y, x + 1, y + 1, 0x80FFFFFF);
        }
    }
}
