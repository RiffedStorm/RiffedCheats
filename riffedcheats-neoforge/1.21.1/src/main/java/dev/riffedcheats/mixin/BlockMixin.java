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

/** Xray: while enabled, only selected ores / chests are drawn, every other block face is skipped. */
@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true, require = 0)
    private static void riffed$xray(BlockState state, BlockGetter level, BlockPos pos, Direction face, BlockPos neighbor,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (Xray.enabled) cir.setReturnValue(Xray.test(state.getBlock()));
    }
}
