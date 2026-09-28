package dev.vanguard.mixin;

import dev.vanguard.Vanguard;
import dev.vanguard.module.modules.combat.AimAssist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies aim assist after the local player's own mouse rotation. {@code turn} is called once per
 * frame while the mouse is grabbed (even with no movement), so this is where the assist nudges
 * the camera smoothly toward a target.
 */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
    @Inject(method = "turn(DD)V", at = @At("TAIL"))
    private void vanguard$aimAssist(double xo, double yo, CallbackInfo ci) {
        Object self = this;
        Minecraft minecraft = Minecraft.getInstance();
        if (self != minecraft.player) return;
        Vanguard vanguard = Vanguard.get();
        if (vanguard == null) return;
        AimAssist aimAssist = vanguard.aimAssist();
        if (aimAssist != null) aimAssist.onTurn((LocalPlayer) self, xo, yo);
    }
}
