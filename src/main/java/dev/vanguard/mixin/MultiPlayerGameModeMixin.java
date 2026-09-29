package dev.vanguard.mixin;

import dev.vanguard.Vanguard;
import dev.vanguard.module.modules.combat.ShieldBreaker;
import dev.vanguard.module.modules.combat.SprintReset;
import dev.vanguard.util.Latency;
import dev.vanguard.util.ServerSprintTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
        if (vanguard == null || !(player instanceof LocalPlayer localPlayer)) return;
        ServerSprintTracker sprint = vanguard.sprintTracker();
        sprint.observeSent(((LocalPlayerAccessor) localPlayer).vanguard$wasSprinting());
        sprint.onAttack(player.getAttackStrengthScale(0.5f), player.position(), Latency.answerTicks(Minecraft.getInstance()));
        SprintReset sprintReset = vanguard.sprintReset();
        if (sprintReset != null) sprintReset.onAttack(target);
        ShieldBreaker shieldBreaker = vanguard.shieldBreaker();
        if (shieldBreaker != null) shieldBreaker.onAttackSent();
    }
}
