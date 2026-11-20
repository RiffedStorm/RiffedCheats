package dev.riffedcheats.core;

import java.util.HashMap;
import java.util.Map;

/** Small eased values for the Modern layout (toggle knobs, hovers, the tab indicator...), keyed by name. */
final class Anim {
    private static final Map<String, Float> V = new HashMap<>();
    private static long last;
    static float dt;

    static boolean on() { return Menu.UI_ANIM.on; }

    static void frame() {
        long n = System.nanoTime();
        dt = last == 0 ? 0 : Math.min(0.1f, (n - last) / 1e9f);
        last = n;
    }

    /** Moves the value toward target (frame-rate independent) and returns it. */
    static float to(String k, float target, float speed) {
        Float c = V.get(k);
        if (!on() || c == null) { V.put(k, target); return target; }
        float v = c + (target - c) * (1 - (float) Math.exp(-speed * dt));
        if (Math.abs(v - target) < 0.002f) v = target;
        V.put(k, v);
        return v;
    }

    static void set(String k, float v) { V.put(k, v); }

    static float ease(float t) { t = Math.max(0, Math.min(1, t)); return 1 - (1 - t) * (1 - t) * (1 - t); }
}
