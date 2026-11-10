package dev.riffedcheats;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import org.lwjgl.glfw.GLFW;

/** Permanent entry point: key bind + events, forwarded to the hot-swappable core (see Loader). */
public class RiffedCheats implements ClientModInitializer {
    public static final String MODID = "riffedcheats";

    /** Options > Controls > Key Binds > RiffedCheats. Default: Insert. */
    public static KeyMapping TOGGLE;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MODID, "main"));
        TOGGLE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.riffedcheats.toggle_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_INSERT, category));

        Path jar = null;
        try {
            List<Path> paths = FabricLoader.getInstance().getModContainer(MODID).orElseThrow().getOrigin().getPaths();
            if (paths.size() == 1) jar = paths.get(0);
        } catch (Throwable ignored) {}
        Loader.init(jar);

        ClientPlayNetworking.registerGlobalReceiver(Msg.TYPE, (m, ctx) -> Loader.core().onServer(m.data()));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MODID, "waypoints"), (g, dt) -> Loader.core().hud(g));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (TOGGLE.consumeClick()) {
                if (mc.gui.screen() == null && mc.player != null) Loader.core().openMenu();
            }
            Loader.core().tick();
        });

        // Hide the vanilla command feedback caused by this menu (the console tab still shows it).
        ClientReceiveMessageEvents.ALLOW_GAME.register((msg, overlay) -> overlay || !Loader.core().onMessage(msg));

        // Crit hack hooks in right before your own attacks; freecam blocks all interaction.
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide()) {
                if (Loader.core().freecamOn()) return InteractionResult.FAIL;
                Loader.core().preAttack();
            }
            return InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> Loader.core().freecamOn() ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> Loader.core().freecamOn() ? InteractionResult.FAIL : InteractionResult.PASS);
        UseItemCallback.EVENT.register((player, level, hand) -> Loader.core().freecamOn() ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    /** True when the server you are on has RiffedCheats too. */
    public static boolean canSend() {
        return net.minecraft.client.Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(Msg.TYPE);
    }
    public static void send(String data) { ClientPlayNetworking.send(new Msg(data)); }
}
