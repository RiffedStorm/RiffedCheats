package dev.riffedcheats;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Permanent entry point: key bind + events, forwarded to the hot-swappable core (see Loader). */
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
        Path jar = null;
        try { jar = ModList.get().getModFileById(MODID).getFile().getFilePath(); } catch (Throwable ignored) {}
        Loader.init(jar);
    }

    private void registerKeys(RegisterKeyMappingsEvent e) { e.register(TOGGLE); }

    private void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        while (TOGGLE.consumeClick()) {
            if (mc.screen == null && mc.player != null) Loader.core().openMenu();
        }
        Loader.core().tick();
    }

    private void onInteract(InputEvent.InteractionKeyMappingTriggered e) {
        if (Loader.core().freecamOn()) { e.setCanceled(true); e.setSwingHand(false); return; }
        if (e.isAttack() && Minecraft.getInstance().hitResult instanceof EntityHitResult) Loader.core().preAttack();
    }

    /** Hides the vanilla "Applied effect..." style feedback caused by this menu; the console tab still shows it. */
    private void onChat(ClientChatReceivedEvent e) { if (Loader.core().onMessage(e.getMessage())) e.setCanceled(true); }

    private void onFrame(RenderFrameEvent.Pre e) { Loader.core().frame(); }
}
