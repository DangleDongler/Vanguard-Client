package dev.vanguard.mixin;

import dev.vanguard.Vanguard;
import dev.vanguard.module.modules.combat.AimAssist;
import dev.vanguard.module.modules.combat.ShieldBreaker;
import dev.vanguard.module.modules.combat.TriggerBot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-frame combat hook, run after the local player's own mouse rotation. {@code turn} is called
 * once per frame while the mouse is grabbed (even with no movement), just before the frame is drawn.
 * Aim assist nudges the camera first, then the shield breaker and trigger bot check what the
 * crosshair is on. A shield break resets your charge, so the trigger bot then waits its turn.
 */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
    @Inject(method = "turn(DD)V", at = @At("TAIL"))
    private void vanguard$afterTurn(double xo, double yo, CallbackInfo ci) {
        Object self = this;
        if (self != Minecraft.getInstance().player) return;
        Vanguard vanguard = Vanguard.get();
        if (vanguard == null) return;
        LocalPlayer player = (LocalPlayer) self;
        AimAssist aimAssist = vanguard.aimAssist();
        if (aimAssist != null) aimAssist.onTurn(player);
        ShieldBreaker shieldBreaker = vanguard.shieldBreaker();
        if (shieldBreaker != null) shieldBreaker.onFrame(player);
        TriggerBot triggerBot = vanguard.triggerBot();
        if (triggerBot != null) triggerBot.onFrame(player);
    }
}
