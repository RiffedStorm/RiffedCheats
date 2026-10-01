package dev.riffedcheats;

import static dev.riffedcheats.Menu.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * All cheat logic. Everything is gated by Setting.active() (= toggle on AND permission),
 * so the moment you lose OP nothing is applied any more, while the toggles stay remembered in memory.
 * Server-side things are done with commands, only when a value actually changes.
 */
public final class Features {
    private static long ticks;
    private static LocalPlayer lastPlayer;
    private static boolean flyWas, srvFlySent, fbWas, espWas, resumeSprint;
    private static int lastXraySig = -1, jumpCd, auraCd;
    private static long lastFreezeSend;

    // freecam
    private static Entity cam;
    private static Object savedInput;
    private static double cx, cy, cz;

    private static final Set<String> effSent = new HashSet<>();
    private static final Map<String, Long> effLast = new HashMap<>();
    private static final Map<String, Integer> effAmpSent = new HashMap<>();

    // ---------- debounce helper ----------
    private static final class Stable {
        int last = Integer.MIN_VALUE, n;
        boolean ready(int v) { if (v != last) { last = v; n = 0; return false; } return ++n >= 8; }
    }
    private static final class Attr {
        final String key; final double def; double sent = Double.NaN; final Stable st = new Stable();
        Attr(String key, double def) { this.key = key; this.def = def; }
    }
    private static final Attr A_STEP = new Attr("step_height", 0.6), A_BREACH = new Attr("block_interaction_range", 4.5),
            A_EREACH = new Attr("entity_interaction_range", 3.0), A_KB = new Attr("knockback_resistance", 0.0),
            A_SPEED = new Attr("attack_speed", 4.0), A_FALL = new Attr("fall_damage_multiplier", 1.0), A_GRAV = new Attr("gravity", 0.08);
    private static final Attr[] ATTRS = { A_STEP, A_BREACH, A_EREACH, A_KB, A_SPEED, A_FALL, A_GRAV };

    public static boolean freecamOn() { return cam != null; }

    // =====================================================================================
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Access.update(mc);
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) { lastPlayer = null; Cmd.clear(); return; }
        ticks++;
        if (p != lastPlayer) { onNewPlayer(); lastPlayer = p; }

        client(p, level, mc);
        if (Access.allowed) { Cmd.tick(); server(p); } else Cmd.clear();

        if (resumeSprint) { resumeSprint = false; if (p.isSprinting()) Compat.sendSprint(p, true); }
    }

    private static void onNewPlayer() {
        if (cam != null) { cam = null; savedInput = null; Compat.setCamera(null); Compat.setRenderHand(true); }
        flyWas = srvFlySent = fbWas = espWas = false;
        lastXraySig = -1;
        effSent.clear(); effLast.clear(); effAmpSent.clear();
        for (Attr a : ATTRS) a.sent = Double.NaN;
        Cmd.clear();
    }

    // ---------------- client-side features ----------------
    private static void client(LocalPlayer p, ClientLevel level, Minecraft mc) {
        // Flight
        if (FLIGHT.active()) {
            Compat.setMayFly(p, true);
            Compat.setFlySpeed(p, 0.05f * FLY_SPEED.value);
            if (Access.mode == Access.Mode.LOCAL && !srvFlySent) { Compat.setServerMayFly(true); srvFlySent = true; }
            flyWas = true;
        } else if (flyWas) {
            Compat.setFlySpeed(p, 0.05f);
            boolean keep = p.isCreative() || p.isSpectator();
            Compat.setMayFly(p, keep);
            if (!keep) Compat.setFlying(p, false);
            if (srvFlySent) { Compat.setServerMayFly(false); srvFlySent = false; }
            flyWas = false;
        }

        // Infinite jump + auto sprint
        if (jumpCd > 0) jumpCd--;
        if (INFJUMP.active() && jumpCd == 0 && mc.options.keyJump.isDown() && !p.onGround() && !Compat.isFlying(p) && !p.isInWater()) {
            Vec3 d = p.getDeltaMovement();
            p.setDeltaMovement(d.x, 0.42, d.z);
            jumpCd = 6;
        }
        if (AUTOSPRINT.active() && mc.options.keyUp.isDown() && !p.isUsingItem() && !p.isShiftKeyDown()) p.setSprinting(true);

        // Fullbright (also auto-enabled by Xray)
        boolean wantFb = FULLBRIGHT.active() || (XRAY.active() && XRAY_FB.on);
        if (wantFb) { if (!Compat.hasNightVision(p)) Compat.addNightVision(p); fbWas = true; }
        else if (fbWas) { Compat.removeNightVision(p); fbWas = false; }

        // ESP via vanilla glow outline
        if (ESP.active()) {
            for (Entity e : Compat.entities(level)) if (e instanceof LivingEntity && e != p) Compat.glow(e, true);
            espWas = true;
        } else if (espWas) {
            for (Entity e : Compat.entities(level)) Compat.glow(e, false);
            espWas = false;
        }

        // Xray (only rebuilds chunks when something changed)
        boolean xr = XRAY.active();
        int sig = 0;
        for (int i = 0; i < XRAY_ORES.length; i++) if (XRAY_ORES[i].on) sig |= 1 << i;
        sig = xr ? (sig | (1 << 20)) : 0;
        if (sig != lastXraySig) {
            lastXraySig = sig;
            Xray.set(xr, sig & 0xFFFFF);
            Compat.setSmartCull(!xr);
            Compat.rebuildChunks();
        }

        // Freecam
        if (FREECAM.active()) freecamTick(p, level, mc); else if (cam != null) freecamStop(p);

        // Kill aura
        if (auraCd > 0) auraCd--;
        if (AURA.active() && auraCd == 0 && cam == null && p.getAttackStrengthScale(0.5f) >= 1f) {
            double best = AURA_RANGE.value * AURA_RANGE.value;
            LivingEntity target = null;
            for (Entity e : Compat.entities(level)) {
                if (e == p || !(e instanceof LivingEntity le) || !le.isAlive() || e instanceof ArmorStand) continue;
                boolean player = e instanceof Player;
                if (player && !AURA_PLAYERS.on) continue;
                if (!player && AURA_HOSTILE.on && !(e instanceof Enemy)) continue;
                double d = p.distanceToSqr(e);
                if (d < best) { best = d; target = le; }
            }
            if (target != null) {
                preAttack(p);
                Compat.attack(target);
                p.swing(InteractionHand.MAIN_HAND);
                auraCd = AURA_DELAY.intValue();
            }
        }
    }

    /** Called right before every attack (your clicks and the aura). Makes the hit a critical. */
    public static void preAttack(LocalPlayer p) {
        if (p == null || !CRIT.active()) return;
        if (!p.onGround() || p.isInWater() || p.isPassenger() || Compat.isFlying(p)) return;
        if (p.isSprinting()) { Compat.sendSprint(p, false); resumeSprint = true; }
        double x = p.getX(), y = p.getY(), z = p.getZ();
        Compat.sendPos(x, y + 0.0625, z, false);
        Compat.sendPos(x, y, z, false);
    }

    // ---------------- freecam ----------------
    /** Set by the platform when it can call frame() every rendered frame (smoother mouse look). */
    public static boolean frameHook;
    private static float camYaw, camPitch, lockYaw, lockPitch, startYaw, startPitch;

    /** Mouse look turns the real player; we turn that rotation into camera rotation and put the player back. */
    private static void rotateFromPlayer(LocalPlayer p) {
        float dy = p.getYRot() - lockYaw;
        dy = ((dy + 540f) % 360f) - 180f;
        float dp = p.getXRot() - lockPitch;
        camYaw += dy;
        camPitch = Math.max(-90f, Math.min(90f, camPitch + dp));
        Compat.lockRotation(p, lockYaw, lockPitch);
    }

    /** Called once per rendered frame (if the platform supports it) so the camera follows the mouse without lag. */
    public static void frame() {
        if (cam == null) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        rotateFromPlayer(p);
        Compat.setCameraAngles(cam, camYaw, camPitch);
    }

    private static void freecamTick(LocalPlayer p, ClientLevel level, Minecraft mc) {
        if (cam == null) {
            cam = Compat.newCamera(level, p);
            Vec3 eye = p.getEyePosition();
            cx = eye.x; cy = eye.y; cz = eye.z;
            startYaw = p.getYRot(); startPitch = p.getXRot();
            camYaw = startYaw; camPitch = startPitch;
            lockYaw = startYaw; lockPitch = 0f;
            savedInput = Compat.freezeInput(p);
            Compat.setRenderHand(false);
            Compat.moveCamera(cam, cx, cy - 1.62, cz, camYaw, camPitch, false);
            Compat.setCamera(cam);
            Compat.lockRotation(p, lockYaw, lockPitch);
        }
        rotateFromPlayer(p);
        double yr = Math.toRadians(camYaw), pr = Math.toRadians(camPitch);
        double fx = -Math.sin(yr) * Math.cos(pr), fy = -Math.sin(pr), fz = Math.cos(yr) * Math.cos(pr);
        double rx = -Math.cos(yr), rz = -Math.sin(yr);
        double mx = 0, my = 0, mz = 0;
        if (mc.options.keyUp.isDown()) { mx += fx; my += fy; mz += fz; }
        if (mc.options.keyDown.isDown()) { mx -= fx; my -= fy; mz -= fz; }
        if (mc.options.keyRight.isDown()) { mx += rx; mz += rz; }
        if (mc.options.keyLeft.isDown()) { mx -= rx; mz -= rz; }
        if (mc.options.keyJump.isDown()) my += 1;
        if (mc.options.keyShift.isDown()) my -= 1;
        double sp = 0.2 * FREECAM_SPEED.value * (mc.options.keySprint.isDown() ? 2 : 1);
        cx += mx * sp; cy += my * sp; cz += mz * sp;
        Compat.moveCamera(cam, cx, cy - 1.62, cz, camYaw, camPitch, !frameHook);
    }

    private static void freecamStop(LocalPlayer p) {
        Compat.setCamera(null);
        Compat.restoreInput(p, savedInput);
        Compat.setRenderHand(true);
        Compat.lockRotation(p, startYaw, startPitch);
        cam = null; savedInput = null;
    }

    // ---------------- server-side features (commands, only on change) ----------------
    private static void server(LocalPlayer p) {
        for (Eff e : EFFECTS) effect(p, e);

        attr(A_STEP, STEP.active(), STEP_H.value);
        boolean reach = REACH.active();
        attr(A_BREACH, reach, BLOCK_REACH.value);
        double er = Math.max(reach ? ENTITY_REACH.value : 3.0, AURA.active() ? AURA_RANGE.value : 3.0);
        attr(A_EREACH, reach || AURA.active(), er);
        attr(A_KB, ANTIKB.active(), 1.0);
        attr(A_SPEED, NODELAY.active(), 1024.0);
        attr(A_FALL, NOFALL.active(), 0.0);
        attr(A_GRAV, LOWGRAV.active(), 0.08 * GRAV_MULT.value);

        // XP freeze: a plain int compare every tick, a command only when the level actually differs (max once/second)
        if (XP_FREEZE.active() && p.experienceLevel != XP_LEVEL.intValue() && System.currentTimeMillis() - lastFreezeSend >= 1000) {
            Cmd.run("xp set @s " + XP_LEVEL.intValue() + " levels");
            lastFreezeSend = System.currentTimeMillis();
        }
    }

    private static void effect(LocalPlayer p, Eff e) {
        String id = e.id();
        if (id.equals("minecraft:saturation")) { hunger(p, e); return; }
        if (e.on().active()) {
            int amp = e.amp();
            Integer sentAmp = effAmpSent.get(id);
            if (sentAmp != null && sentAmp != amp && effSent.contains(id)) {          // level changed -> replace
                if (ticks - effLast.getOrDefault(id + "#c", 0L) >= 10) {
                    effLast.put(id + "#c", ticks);
                    Cmd.run("effect clear @s " + id);
                    effLast.put(id, 0L);
                    effAmpSent.put(id, amp);
                }
                return;
            }
            int rem = Compat.effectTicks(p, id);
            if ((rem < 200 || Compat.effectAmp(p, id) < amp) && ticks - effLast.getOrDefault(id, 0L) >= 25) {
                Cmd.run("effect give @s " + id + " 30 " + amp + " true");
                effLast.put(id, ticks);
                effAmpSent.put(id, amp);
                effSent.add(id);
            }
        } else if (effSent.remove(id)) {
            Cmd.run("effect clear @s " + id);
            effAmpSent.remove(id);
        }
    }

    /** Saturation is an instant effect (it never shows as "active"), so only top up when food/saturation is actually low. */
    private static void hunger(LocalPlayer p, Eff e) {
        if (!e.on().active()) return;
        boolean low = p.getFoodData().getFoodLevel() < 20 || p.getFoodData().getSaturationLevel() < 10f;
        if (low && ticks - effLast.getOrDefault("hunger", 0L) >= 20) {
            Cmd.run("effect give @s minecraft:saturation 1 10 true");
            effLast.put("hunger", ticks);
        }
    }

    private static void attr(Attr a, boolean on, double value) {
        if (on) {
            if (a.st.ready((int) Math.round(value * 1000)) && a.sent != value) {
                Cmd.run("attribute @s " + Compat.attr(a.key) + " base set " + fmt(value));
                a.sent = value;
            }
        } else if (!Double.isNaN(a.sent)) {
            Cmd.run("attribute @s " + Compat.attr(a.key) + " base set " + fmt(a.def));
            a.sent = Double.NaN;
        }
    }

    private static String fmt(double v) { return String.format(Locale.ROOT, "%.4f", v); }

    /**
     * Commands that put every attribute this menu can touch back to vanilla and clear all effects.
     * target = "@s", a player name, or "@a" for everyone. Used by Settings and by the Players tab.
     */
    public static String[] resetCommands(String target) {
        boolean all = target.equals("@a");
        String who = all ? "@s" : target, pre = all ? "execute as @a run " : "";
        String[] out = new String[ATTRS.length + 1];
        for (int i = 0; i < ATTRS.length; i++)
            out[i] = pre + "attribute " + who + " " + Compat.attr(ATTRS[i].key) + " base set " + fmt(ATTRS[i].def);
        out[ATTRS.length] = "effect clear " + target;
        return out;
    }

    /** Settings > Reset: resets your own server-side values. Needs OP. */
    public static void resetServerState() {
        if (!Access.allowed) return;
        Cmd.run(resetCommands("@s"));
        for (Attr a : ATTRS) a.sent = Double.NaN;
        effSent.clear(); effAmpSent.clear();
    }

    // ---------------- helpers used by menu buttons ----------------
    /** Moves time FORWARD to the target time of day so the day counter never resets. */
    public static void setTime(int target) {
        long dt = Compat.dayTime();
        if (dt == Long.MIN_VALUE) { Cmd.run("time set " + target); return; } // time unknown on this version: fall back to plain set
        long cur = Math.floorMod(Math.abs(dt), 24000L);
        long d = Math.floorMod(target - cur, 24000L);
        if (d == 0) return;
        Cmd.run("time add " + d);
    }

    public static void tpSpawn() {
        int[] s = Compat.spawnPos();
        Cmd.run("tp @s " + s[0] + " " + (s[1] + 1) + " " + s[2]);
    }
}
