package dev.riffedcheats.server;

import java.lang.reflect.Field;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The server part that is specific to Minecraft 26.2 / Fabric. */
final class ServerCompat {
    private static final int RAISED = 40;   // ticks a shield counts as raised for, once Instant Block has touched it
    private static Field useRemaining;
    private static boolean fieldFailed;

    private static boolean owner(ServerPlayer p) { return p.level().getServer().isSingleplayerOwner(p.nameAndId()); }

    /** Operator (game master level or higher), or the player hosting this world. */
    static boolean allowed(ServerPlayer p) { return p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || owner(p); }

    /** The player's own permission level; the host of a world always gets full permissions, even with cheats off. */
    static CommandSourceStack source(ServerPlayer p) {
        CommandSourceStack s = p.createCommandSourceStack();
        return owner(p) ? s.withPermission(LevelBasedPermissionSet.OWNER) : s;
    }

    static ServerPlayer byName(net.minecraft.server.MinecraftServer s, String name) { return s.getPlayerList().getPlayer(name); }

    static int getInt(CompoundTag t, String k) { return t.getIntOr(k, 0); }

    static String version() {
        return FabricLoader.getInstance().getModContainer("riffedcheats").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    private static boolean shield(ItemStack st) { return !st.isEmpty() && st.has(DataComponents.BLOCKS_ATTACKS); }

    /** Vanilla only starts blocking after a short delay of holding the shield up: count it as held up long ago. */
    static void instantBlock(ServerPlayer p) {
        if (!p.isUsingItem() || !shield(p.getUseItem()) || p.getTicksUsingItem() >= RAISED) return;
        try {
            if (useRemaining == null) {
                if (fieldFailed) return;
                useRemaining = LivingEntity.class.getDeclaredField("useItemRemaining");
                useRemaining.setAccessible(true);
            }
            useRemaining.setInt(p, Math.max(1, p.getUseItem().getUseDuration(p) - RAISED));
        } catch (Throwable t) {
            fieldFailed = true;
            System.err.println("[RiffedCheats] Instant Block is not available: " + t);
        }
    }

    private static void clear(ItemCooldowns cd, ItemStack st) { if (cd.isOnCooldown(st)) cd.removeCooldown(cd.getCooldownGroup(st)); }

    static void clearShieldCooldown(ServerPlayer p) {
        ItemCooldowns cd = p.getCooldowns();
        clear(cd, new ItemStack(Items.SHIELD));
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack st = p.getItemInHand(h);
            if (shield(st)) clear(cd, st);
        }
    }
}
