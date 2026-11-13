package dev.riffedcheats.server;

import java.lang.reflect.Field;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ItemAbilities;

/** The server part that is specific to Minecraft 1.21.1 / NeoForge. */
final class ServerCompat {
    private static final int RAISED = 40;   // ticks a shield counts as raised for, once Instant Block has touched it
    private static Field useRemaining;
    private static boolean fieldFailed;

    private static boolean owner(ServerPlayer p) { return p.server.isSingleplayerOwner(p.getGameProfile()); }

    /** Operator (level 2+), or the player hosting this world. */
    static boolean allowed(ServerPlayer p) { return p.hasPermissions(2) || owner(p); }

    /** The player's own permission level; the host of a world always gets full permissions, even with cheats off. */
    static CommandSourceStack source(ServerPlayer p) {
        CommandSourceStack s = p.createCommandSourceStack();
        return owner(p) ? s.withPermission(4) : s;
    }

    static ServerPlayer byName(net.minecraft.server.MinecraftServer s, String name) { return s.getPlayerList().getPlayerByName(name); }

    static int getInt(CompoundTag t, String k) { return t.getInt(k); }

    static String version() {
        return ModList.get().getModContainerById("riffedcheats").map(c -> c.getModInfo().getVersion().toString()).orElse("?");
    }

    private static boolean shield(ItemStack st) { return !st.isEmpty() && st.canPerformAction(ItemAbilities.SHIELD_BLOCK); }

    /** Vanilla only starts blocking after 5 ticks of holding the shield up: count it as held up long ago. */
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

    static void clearShieldCooldown(ServerPlayer p) {
        ItemCooldowns cd = p.getCooldowns();
        if (cd.isOnCooldown(Items.SHIELD)) cd.removeCooldown(Items.SHIELD);
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack st = p.getItemInHand(h);
            if (shield(st) && cd.isOnCooldown(st.getItem())) cd.removeCooldown(st.getItem());
        }
    }
}
