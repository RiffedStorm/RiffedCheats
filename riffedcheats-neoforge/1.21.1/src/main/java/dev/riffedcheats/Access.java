package dev.riffedcheats;

import net.minecraft.client.Minecraft;

/**
 * Decides, every tick, whether the menu may do anything.
 * LOCAL = you host the world (singleplayer / Open to LAN) -> always allowed, no cheats needed.
 * OP    = connected to someone else's server and you are an operator -> allowed.
 * DENIED = connected to a server without operator rights -> everything disabled.
 */
public final class Access {
    public enum Mode { NONE, LOCAL, OP, DENIED }

    public static volatile Mode mode = Mode.NONE;
    public static volatile boolean allowed;

    public static void update(Minecraft mc) {
        Mode m;
        if (mc.player == null || mc.level == null) m = Mode.NONE;
        else if (Compat.hostingLocalServer()) m = Mode.LOCAL;
        else m = Compat.hasOp(mc.player) ? Mode.OP : Mode.DENIED;
        mode = m;
        allowed = m == Mode.LOCAL || m == Mode.OP;
    }

    public static String label() {
        return switch (mode) {
            case LOCAL -> "Singleplayer / LAN host";
            case OP -> "Operator";
            case DENIED -> "No Operator";
            default -> "Not in a world";
        };
    }
}
