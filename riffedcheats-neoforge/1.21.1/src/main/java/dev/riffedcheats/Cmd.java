package dev.riffedcheats;

import java.util.ArrayDeque;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Command runner. In your own world commands run instantly with full permissions (no "allow cheats" needed).
 * On a server they are sent as normal commands through a small queue so vanilla's chat-spam kick never triggers.
 */
public final class Cmd {
    private static final ArrayDeque<String> Q = new ArrayDeque<>();
    private static int wait;
    private static long lastMark;

    private static void mark() { lastMark = System.currentTimeMillis(); }

    /**
     * Called for every incoming system/game message. Logs it for the console tab and returns true when the
     * message is vanilla command feedback caused by this mod ("Applied effect...", "Set the time...") and should
     * be hidden from the chat. Chat from players and server messages are never touched.
     */
    public static boolean onMessage(Component c) {
        ConsolePage.log(c.getString());
        if (Q.isEmpty() && System.currentTimeMillis() - lastMark > 2000) return false;
        return c.getContents() instanceof TranslatableContents t && t.getKey().startsWith("commands.");
    }

    public static void run(String... cmds) { for (String c : cmds) run1(c); }

    private static void run1(String c) {
        if (!Access.allowed || c == null || c.isBlank()) return;
        if (c.startsWith("/")) c = c.substring(1);
        mark();
        if (Access.mode == Access.Mode.LOCAL) { Compat.runLocal(c); return; }
        if (c.length() > 255) return; // vanilla command packet limit
        if (!Q.contains(c)) Q.add(c);
    }

    public static void tick() {
        if (wait > 0) { wait--; return; }
        String c = Q.poll();
        if (c != null) { mark(); Compat.runRemote(c); wait = 3; }
    }

    public static void clear() { Q.clear(); }
}
