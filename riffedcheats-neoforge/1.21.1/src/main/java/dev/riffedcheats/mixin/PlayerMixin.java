package dev.riffedcheats.mixin;

import dev.riffedcheats.Noclip;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every tick the game sets noPhysics to "is a spectator"; Noclip extends that to the players who have it on. */
@Mixin(Player.class)
abstract class PlayerMixin {
    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Player;noPhysics:Z", opcode = org.objectweb.asm.Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void riffedcheats$noclip(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (Noclip.on(self)) { self.noPhysics = true; self.setOnGround(false); }
    }
}
