package dev.vanguard.mixin;

import dev.vanguard.Vanguard;
import dev.vanguard.module.modules.combat.TriggerBot;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sees every attack the client sends (clicks and modules) before the charge resets. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "attack", at = @At("HEAD"))
    private void vanguard$onAttack(Player player, Entity target, CallbackInfo ci) {
        Vanguard vanguard = Vanguard.get();
        if (vanguard == null) return;
        TriggerBot triggerBot = vanguard.triggerBot();
        if (triggerBot != null) triggerBot.onAttackSent(player);
    }
}
