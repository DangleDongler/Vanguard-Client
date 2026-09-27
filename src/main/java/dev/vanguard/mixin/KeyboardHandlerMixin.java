package dev.vanguard.mixin;

import dev.vanguard.Vanguard;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    /**
     * Vanilla clicks key mappings only for in-game input: no screen open and the key
     * wasn't part of an F3 combo. Hooking the same spot gives module binds those rules,
     * and a screen opened here doesn't receive the key that opened it.
     */
    @Inject(
        method = "keyPress",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;click(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V")
    )
    private void vanguard$onGameKeyPress(long handle, int action, KeyEvent event, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS) Vanguard.get().modules().onKeyPressed(event.key());
    }
}
