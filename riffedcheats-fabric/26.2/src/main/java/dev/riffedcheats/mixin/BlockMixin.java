package dev.riffedcheats.mixin;

import dev.riffedcheats.Xray;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Xray. Minecraft changed Block.shouldRenderFace's signature between versions, so both known shapes are
 * targeted with require = 0: whichever one exists is used, the other is silently skipped.
 */
@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(method = "shouldRenderFace(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void riffed$xrayNew(BlockState state, BlockState neighbor, Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (Xray.enabled) cir.setReturnValue(Xray.test(state.getBlock()));
    }

    @Inject(method = "shouldRenderFace(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void riffed$xrayOld(BlockState state, BlockGetter level, BlockPos pos, Direction face, BlockPos neighbor, CallbackInfoReturnable<Boolean> cir) {
        if (Xray.enabled) cir.setReturnValue(Xray.test(state.getBlock()));
    }
}
