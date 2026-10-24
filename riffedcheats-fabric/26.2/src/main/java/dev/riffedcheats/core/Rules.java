package dev.riffedcheats.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Live state of boolean game rules. The client cannot read game rules on its own. In your own world the menu runs
 * "/gamerule <id>" and captures the answer; on a server it stores the value in command storage and has the server
 * /tellraw it back (works with sendCommandFeedback off). Command feedback, when it arrives, is read as well.
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
            if (Access.mode == Access.Mode.LOCAL) Cmd.query("gamerule " + id);
            else {
                String k = norm(id).replaceAll("[^a-z0-9_]", "_");
                Cmd.query("execute store result storage " + Cmd.STORAGE + " " + k + " int 1 run gamerule " + id);
                Cmd.ask("rule", k, k, "\"storage\":\"" + Cmd.STORAGE + "\"");
            }
        }
        return v;
    }

    public static void flip(String id) {
        Boolean v = STATE.get(norm(id));
        if (v == null) { ASKED.remove(norm(id)); get(id); return; }
        Cmd.query("gamerule " + id + " " + !v);
        STATE.put(norm(id), !v); // optimistic; the server's answer confirms it
    }

    /** Called with the arguments of "commands.gamerule.query" / "commands.gamerule.set" feedback, or 1 / 0 from storage. */
    static void put(String id, String value) {
        value = value.trim();
        if (value.equals("true") || value.equals("1")) STATE.put(norm(id), true);
        else if (value.equals("false") || value.equals("0")) STATE.put(norm(id), false);
    }

    public static void clear() { STATE.clear(); ASKED.clear(); }
}
