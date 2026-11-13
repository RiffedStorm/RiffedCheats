package dev.riffedcheats.core;

import net.minecraft.client.Minecraft;

/**
 * Decides, every tick, whether and how the menu may do anything.
 * SERVER = RiffedCheats is on the server too (always true for a world you host) and you are allowed: everything is silent.
 * LOCAL  = you host the world but chose Client Side: commands run directly, with full permissions.
 * OP     = someone else's server without the mod, and you are an operator: normal commands.
 * DENIED = connected to a server without operator rights: everything disabled.
 * WAIT   = the server has the mod and is about to answer (the first moments after joining).
 */
public final class Access {
    public enum Mode { NONE, WAIT, SERVER, LOCAL, OP, DENIED }

    public static volatile Mode mode = Mode.NONE;
    public static volatile boolean allowed;

    public static void update(Minecraft mc) {
        Mode m;
        boolean clientOnly = Menu.CONN.intValue() == 1;
        if (mc.player == null || mc.level == null) m = Mode.NONE;
        else if (!clientOnly && Srv.waiting()) m = Mode.WAIT;
        else if (!clientOnly && Srv.usable()) m = Mode.SERVER;
        else if (Compat.hostingLocalServer()) m = Mode.LOCAL;
        else m = Compat.hasOp(mc.player) ? Mode.OP : Mode.DENIED;
        mode = m;
        allowed = m == Mode.SERVER || m == Mode.LOCAL || m == Mode.OP;
    }

    /** RiffedCheats on the server is doing the work. */
    public static boolean server() { return mode == Mode.SERVER; }
    /** Command output can be read directly (no hidden /tellraw needed). */
    public static boolean direct() { return mode == Mode.SERVER || mode == Mode.LOCAL; }

    public static String label() {
        return switch (mode) {
            case SERVER -> "Server Mode";
            case LOCAL -> "Client Mode (your world)";
            case OP -> "Client Mode (Operator)";
            case DENIED -> "No Operator";
            case WAIT -> "Connecting...";
            default -> "Not in a world";
        };
    }
}
