package dev.riffedcheats;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = RiffedCheats.MODID, dist = Dist.CLIENT)
public class RiffedCheats {
    public static final String MODID = "riffedcheats";

    /** Options > Controls > Key Binds > RiffedCheats. Default: Insert. */
    public static final KeyMapping TOGGLE = new KeyMapping(
            "key.riffedcheats.toggle_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_INSERT, "key.categories.riffedcheats");

    public RiffedCheats(IEventBus modBus) {
        modBus.addListener(this::registerKeys);
        NeoForge.EVENT_BUS.addListener(this::onTick);
        NeoForge.EVENT_BUS.addListener(this::onInteract);
        NeoForge.EVENT_BUS.addListener(this::onChat);
        NeoForge.EVENT_BUS.addListener(this::onFrame);
        Features.frameHook = true;
        Menu.load();
    }

    private void registerKeys(RegisterKeyMappingsEvent e) { e.register(TOGGLE); }

    private void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        while (TOGGLE.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(new MenuScreen());
        }
        Features.tick();
    }

    private void onInteract(InputEvent.InteractionKeyMappingTriggered e) {
        if (Features.freecamOn()) { e.setCanceled(true); e.setSwingHand(false); return; }
        Minecraft mc = Minecraft.getInstance();
        if (e.isAttack() && mc.hitResult instanceof EntityHitResult) Features.preAttack(mc.player);
    }

    /** Hides the vanilla "Applied effect..." style feedback caused by this menu; the console tab still shows it. */
    private void onChat(ClientChatReceivedEvent e) { if (Cmd.onMessage(e.getMessage())) e.setCanceled(true); }

    private void onFrame(RenderFrameEvent.Pre e) { Features.frame(); }
}
