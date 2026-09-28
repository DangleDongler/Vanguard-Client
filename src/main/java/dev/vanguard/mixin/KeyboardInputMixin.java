package dev.vanguard.mixin;

import dev.vanguard.Vanguard;
import dev.vanguard.module.modules.combat.SprintReset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets SprintReset tap your movement keys. The game reads the keys here once per tick, before it
 * moves you or updates your sprint, so a change made here is exactly what pressing or releasing the
 * key that tick would do, down to the key state sent to the server.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void vanguard$adjustKeys(CallbackInfo ci) {
        Vanguard vanguard = Vanguard.get();
        SprintReset sprintReset = vanguard == null ? null : vanguard.sprintReset();
        Minecraft mc = Minecraft.getInstance();
        if (sprintReset == null || mc.player == null || mc.player.input != (Object) this) return;

        Input adjusted = sprintReset.adjustInput(mc.player, this.keyPresses);
        if (adjusted.equals(this.keyPresses)) return;
        this.keyPresses = adjusted;
        // Same as the game's own conversion from keys to movement.
        this.moveVector = new Vec2(impulse(adjusted.left(), adjusted.right()), impulse(adjusted.forward(), adjusted.backward())).normalized();
    }

    private static float impulse(boolean positive, boolean negative) {
        if (positive == negative) return 0.0f;
        return positive ? 1.0f : -1.0f;
    }
}
