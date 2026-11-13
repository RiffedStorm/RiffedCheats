package dev.riffedcheats;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;

/** Who is phasing through blocks right now. Read by PlayerMixin on both the client and the server. */
public final class Noclip {
    /** The local player while Noclip is on, otherwise null. */
    public static volatile UUID client;
    /** Players the server lets through blocks. */
    public static final Set<UUID> SERVER = ConcurrentHashMap.newKeySet();

    public static boolean on(Player p) { return p.level().isClientSide() ? p.getUUID().equals(client) : SERVER.contains(p.getUUID()); }
}
