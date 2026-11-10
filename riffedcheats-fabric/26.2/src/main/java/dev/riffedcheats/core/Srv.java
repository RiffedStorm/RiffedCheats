package dev.riffedcheats.core;

import static dev.riffedcheats.core.Menu.*;

import dev.riffedcheats.RiffedCheats;
import dev.riffedcheats.server.ServerHost;
import java.util.Arrays;

/** The menu's side of the link to RiffedCheats on the server: detection, silent commands, and the per-player switches. */
public final class Srv {
    private static boolean answered, protoOk, permitted;
    private static String version = "";
    private static long firstSeen, lastHello, lastStateAt;
    private static String lastState = "";

    public static boolean present() { return RiffedCheats.canSend(); }
    /** The server has the mod but has not answered yet (only the first moments after joining). */
    public static boolean waiting() { return present() && !answered && System.currentTimeMillis() - firstSeen < 3000; }
    /** The server has a compatible copy of the mod and lets this player use it. */
    public static boolean usable() { return present() && answered && protoOk && permitted; }
    public static boolean outdated() { return present() && answered && !protoOk; }
    public static String version() { return version; }

    public static void send(String... parts) {
        if (!present()) return;
        String s = String.join(ServerHost.SEP, parts);
        if (s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32000) RiffedCheats.send(s);   // the packet limit towards a server
        else System.err.println("[RiffedCheats] Command too long to send: " + s.length() + " characters");
    }

    private static void reset() { answered = protoOk = permitted = false; version = ""; firstSeen = lastHello = 0; lastState = ""; }

    /** Every client tick, before the access mode is worked out. */
    static void tick() {
        if (!present()) { reset(); return; }
        long now = System.currentTimeMillis();
        if (firstSeen == 0) firstSeen = now;
        if (now - lastHello > (answered ? 5000 : 500)) { lastHello = now; send("hello", String.valueOf(ServerHost.PROTO)); }
        if (Access.server()) {
            String s = state();
            if (!s.equals(lastState) || now - lastStateAt > 5000) { lastState = s; lastStateAt = now; send("state", s); }
        } else if (!lastState.isEmpty()) { lastState = ""; send("state", ""); }
    }

    private static String b(Setting s) { return s.active() ? "1" : "0"; }

    private static String state() {
        return "god=" + b(GOD) + ";food=" + b(HUNGER) + ";heal=" + b(HEAL) + ";nofall=" + b(NOFALL) + ";fly=" + b(FLIGHT)
                + ";repair=" + b(AUTO_REPAIR) + ";block=" + b(INSTANT_BLOCK) + ";nocd=" + b(NO_SHIELD_CD)
                + ";magnet=" + (MAGNET.active() ? MAGNET_RANGE.value : 0);
    }

    /** A message from the server. */
    public static void onMessage(String data) {
        String[] a = data.split(ServerHost.SEP, -1);
        switch (a[0]) {
            case "hello" -> {
                if (a.length < 4) return;
                answered = true;
                protoOk = a[1].equals(String.valueOf(ServerHost.PROTO));
                permitted = a[2].equals("1");
                version = a[3];
            }
            case "out" -> {
                if (a.length < 4) return;
                if (a[1].equals("1")) ConsolePage.log(a[2]);
                if (!a[3].isEmpty()) Cmd.dispatch(a[3], Arrays.copyOfRange(a, 4, a.length));
            }
            default -> {}
        }
    }
}
