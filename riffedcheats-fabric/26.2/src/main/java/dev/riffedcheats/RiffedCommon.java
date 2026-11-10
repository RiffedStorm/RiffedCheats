package dev.riffedcheats;

import dev.riffedcheats.server.ServerHost;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Runs on both sides. Players without the mod can join, and the menu works on servers without it. */
public class RiffedCommon implements ModInitializer {
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(Msg.TYPE, Msg.CODEC);
        PayloadTypeRegistry.clientboundPlay().registerLarge(Msg.TYPE, Msg.CODEC, 8 << 20);
        ServerHost.sender = (p, s) -> { if (ServerPlayNetworking.canSend(p, Msg.TYPE)) ServerPlayNetworking.send(p, new Msg(s)); };
        ServerPlayNetworking.registerGlobalReceiver(Msg.TYPE, (m, ctx) -> ServerHost.receive(ctx.player(), m.data()));
        ServerTickEvents.END_SERVER_TICK.register(ServerHost::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amount) -> !ServerHost.blocked(e, src));
        ServerPlayConnectionEvents.DISCONNECT.register((h, srv) -> ServerHost.left(h.player));
    }
}
