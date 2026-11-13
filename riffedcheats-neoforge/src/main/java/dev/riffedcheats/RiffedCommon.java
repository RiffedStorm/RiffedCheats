package dev.riffedcheats;

import dev.riffedcheats.server.ServerHost;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Runs on both sides. The channel is optional, so players without the mod can join and the menu works on servers without it. */
@Mod(RiffedCheats.MODID)
public class RiffedCommon {
    public RiffedCommon(IEventBus modBus) {
        modBus.addListener(this::payloads);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::damage);
        NeoForge.EVENT_BUS.addListener(this::left);
        ServerHost.sender = (p, s) -> PacketDistributor.sendToPlayer(p, new Msg(s));
    }

    private void payloads(RegisterPayloadHandlersEvent e) {
        e.registrar("1").optional().playBidirectional(Msg.TYPE, Msg.CODEC, (m, ctx) -> {
            if (ctx.flow().isClientbound()) Loader.core().onServer(m.data());
            else if (ctx.player() instanceof ServerPlayer p) ServerHost.receive(p, m.data());
        });
    }

    private void tick(ServerTickEvent.Post e) { ServerHost.tick(e.getServer()); }
    private void damage(LivingIncomingDamageEvent e) { if (ServerHost.blocked(e.getEntity(), e.getSource())) e.setCanceled(true); }
    private void left(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) ServerHost.left(p); }
}
