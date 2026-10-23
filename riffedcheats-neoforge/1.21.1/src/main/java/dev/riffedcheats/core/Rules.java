package dev.riffedcheats.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Live state of boolean game rules. The client cannot read game rules on its own, so the menu asks with
 * "/gamerule <id>" and reads the answer ("Gamerule X is currently set to: true") from the command feedback.
 */
public final class Rules {
    private static final Map<String, Boolean> STATE = new HashMap<>();
    private static final Map<String, Long> ASKED = new HashMap<>();

    private static String norm(String id) {
        id = id.toLowerCase(Locale.ROOT);
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    /** null = not known yet; asks the server (at most every 5 s per rule). */
    public static Boolean get(String id) {
        Boolean v = STATE.get(norm(id));
        if (v == null && System.currentTimeMillis() - ASKED.getOrDefault(norm(id), 0L) > 5000) {
            ASKED.put(norm(id), System.currentTimeMillis());
            Cmd.query("gamerule " + id);
        }
        return v;
    }

    public static void flip(String id) {
        Boolean v = STATE.get(norm(id));
        if (v == null) { ASKED.remove(norm(id)); get(id); return; }
        Cmd.query("gamerule " + id + " " + !v);
        STATE.put(norm(id), !v); // optimistic; the server's answer confirms it
    }

    /** Called with the arguments of "commands.gamerule.query" / "commands.gamerule.set" feedback. */
    static void put(String id, String value) {
        if (value.equals("true") || value.equals("false")) STATE.put(norm(id), value.equals("true"));
    }

    public static void clear() { STATE.clear(); ASKED.clear(); }
}
