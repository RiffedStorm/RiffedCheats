package dev.riffedcheats.core;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Command runner. In your own world commands run instantly with full permissions (no "allow cheats" needed).
 * On a server they are sent as normal commands through a small queue so vanilla's chat-spam kick never triggers.
 */
public final class Cmd {
    private static final ArrayDeque<String> Q = new ArrayDeque<>();
    private static final Set<String> CONSOLE = new HashSet<>();   // queued commands typed in the console tab
    private static int wait;
    private static long lastMark, consoleSentAt;
    private static boolean lastWasConsole;

    private static void mark() { lastMark = System.currentTimeMillis(); }

    /** Start of the hidden /tellraw answers the menu asks for on servers. */
    private static final String TAG = "RCQ#";
    /** Command storage the menu writes game rule values into on servers. */
    static final String STORAGE = "riffedcheats:q";

    /**
     * Asks the server to send an NBT value back to this player only, as a /tellraw message the menu reads and hides.
     * Unlike command feedback this arrives with sendCommandFeedback off and is never shown to other operators.
     * @param source "entity":"<name>" or "storage":"<id>"
     */
    static void ask(String kind, String key, String path, String source) {
        query("tellraw @s {\"text\":\"" + TAG + kind + "#" + key + "#\",\"extra\":[{\"nbt\":\"" + path + "\"," + source + "}]}");
    }

    /** "kind#key#value" from a hidden answer. */
    private static void answer(String s) {
        String[] p = s.split("#", 3);
        if (p.length < 3) return;
        switch (p[0]) {
            case "rule" -> Rules.put(p[1], p[2]);
            case "inv" -> { if (!p[2].isEmpty()) Inventories.onData(p[1], p[2]); }
            case "eq" -> { if (p[2].isEmpty()) Inventories.noEquipment(); else Inventories.onData(p[1], p[2]); }
            default -> {}
        }
    }

    /**
     * Called for every incoming system/game message. Reads answers the menu asked for (game rules, inventories),
     * logs console output, and returns true when the message is vanilla command feedback caused by this mod
     * ("Applied effect...", "Set the time...") and should be hidden from the chat. Chat from players is never touched.
     */
    public static boolean onMessage(Component c) {
        String s = c.getString();
        if (s.startsWith(TAG)) { answer(s.substring(TAG.length())); return true; }
        dispatch(c);
        // On a server the console output arrives here. Slow commands (locate in big modded worlds) may answer late.
        if (Access.mode != Access.Mode.LOCAL && lastWasConsole && System.currentTimeMillis() - consoleSentAt < 30000)
            ConsolePage.log(c.getString());
        if (Q.isEmpty() && System.currentTimeMillis() - lastMark > 2000) return false;
        return c.getContents() instanceof TranslatableContents t && t.getKey().startsWith("commands.");
    }

    private static String str(Object o) { return o instanceof Component k ? k.getString() : String.valueOf(o); }

    /** Picks the answers the menu is waiting for out of command feedback. */
    static void dispatch(Component c) {
        if (!(c.getContents() instanceof TranslatableContents t)) return;
        Object[] a = t.getArgs();
        if (a.length < 1) return;
        switch (t.getKey()) {
            case "commands.gamerule.query", "commands.gamerule.set" -> { if (a.length >= 2) Rules.put(str(a[0]), str(a[1])); }
            case "commands.data.entity.query" -> { if (a.length >= 2) Inventories.onData(str(a[0]), str(a[1])); }
            case "arguments.nbtpath.nothing_found" -> { if (str(a[0]).startsWith("equipment")) Inventories.noEquipment(); }
            default -> {}
        }
    }

    public static void run(String... cmds) { for (String c : cmds) run1(c, false); }

    /** A command typed in the console: its output goes to the console tab (also slow output and output hidden by sendCommandFeedback=false in your own world). */
    public static void console(String c) {
        if (Access.mode == Access.Mode.LOCAL) {
            if (!Access.allowed || c.isBlank()) return;
            String cmd = c.startsWith("/") ? c.substring(1) : c;
            Compat.runCapture(cmd, m -> Minecraft.getInstance().execute(() -> { ConsolePage.log(m.getString()); dispatch(m); }));
        } else run1(c, true);
    }

    /** A command whose answer the menu reads (game rule state, inventories). In your own world it is captured directly. */
    public static void query(String c) {
        if (Access.mode == Access.Mode.LOCAL) {
            if (Access.allowed) Compat.runCapture(c, m -> Minecraft.getInstance().execute(() -> dispatch(m)));
        } else run1(c, false);
    }

    private static void run1(String c, boolean console) {
        if (!Access.allowed || c == null || c.isBlank()) return;
        if (c.startsWith("/")) c = c.substring(1);
        mark();
        if (Access.mode == Access.Mode.LOCAL) { Compat.runLocal(c); return; }
        if (c.length() > 255) return; // vanilla command packet limit
        if (console) CONSOLE.add(c);
        if (!Q.contains(c)) Q.add(c);
    }

    public static void tick() {
        if (wait > 0) { wait--; return; }
        String c = Q.poll();
        if (c != null) {
            mark();
            lastWasConsole = CONSOLE.remove(c);
            if (lastWasConsole) consoleSentAt = System.currentTimeMillis();
            Compat.runRemote(c);
            wait = 3;
        }
    }

    public static void clear() { Q.clear(); CONSOLE.clear(); }
}
