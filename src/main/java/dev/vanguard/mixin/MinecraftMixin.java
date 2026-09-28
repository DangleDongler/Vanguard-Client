package dev.vanguard.mixin;

import dev.vanguard.Vanguard;
import dev.vanguard.module.modules.combat.ShieldBreaker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    /** Positive while attacks are blocked, e.g. right after closing a screen. */
    @Shadow
    protected int missTime;

    /** Runs before every attack (your clicks and modules alike), before the held item is read. */
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void vanguard$beforeAttack(CallbackInfoReturnable<Boolean> cir) {
        if (this.missTime > 0) return;
        Vanguard vanguard = Vanguard.get();
        ShieldBreaker shieldBreaker = vanguard == null ? null : vanguard.shieldBreaker();
        if (shieldBreaker != null) shieldBreaker.beforeAttack((Minecraft) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void vanguard$afterTick(CallbackInfo ci) {
        Vanguard vanguard = Vanguard.get();
        ShieldBreaker shieldBreaker = vanguard == null ? null : vanguard.shieldBreaker();
        if (shieldBreaker != null) shieldBreaker.onClientTick((Minecraft) (Object) this);
    }
}
