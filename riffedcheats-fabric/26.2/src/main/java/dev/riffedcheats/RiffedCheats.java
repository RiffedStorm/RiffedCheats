package dev.riffedcheats;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import org.lwjgl.glfw.GLFW;

public class RiffedCheats implements ClientModInitializer {
    public static final String MODID = "riffedcheats";

    /** Options > Controls > Key Binds > RiffedCheats. Default: Insert. */
    public static KeyMapping TOGGLE;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MODID, "main"));
        TOGGLE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.riffedcheats.toggle_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_INSERT, category));

        Menu.load();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (TOGGLE.consumeClick()) {
                if (mc.gui.screen() == null && mc.player != null) mc.gui.setScreen(new MenuScreen());
            }
            Features.tick();
        });

        // Hide the vanilla command feedback caused by this menu (the console tab still shows it).
        ClientReceiveMessageEvents.ALLOW_GAME.register((msg, overlay) -> overlay || !Cmd.onMessage(msg));

        // Crit hack hooks in right before your own attacks; freecam blocks all interaction.
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide()) {
                if (Features.freecamOn()) return InteractionResult.FAIL;
                Features.preAttack(Minecraft.getInstance().player);
            }
            return InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> Features.freecamOn() ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> Features.freecamOn() ? InteractionResult.FAIL : InteractionResult.PASS);
        UseItemCallback.EVENT.register((player, level, hand) -> Features.freecamOn() ? InteractionResult.FAIL : InteractionResult.PASS);
    }
}
