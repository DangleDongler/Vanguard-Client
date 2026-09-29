package dev.vanguard.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.vanguard.gui.render.Backdrop;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The GUI renderer stops once per frame, between what's under a screen and the screen itself, to
 * blur the frame. While the ClickGUI is open that's where its glass captures the frame instead.
 */
@Mixin(GuiRenderer.class)
abstract class GuiRendererMixin {
    @WrapOperation(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;processBlurEffect()V"))
    private void vanguard$captureBackdrop(GameRenderer renderer, Operation<Void> original) {
        if (!Backdrop.get().captureIfRequested()) original.call(renderer);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void vanguard$endFrame(CallbackInfo ci) {
        Backdrop.get().endFrame();
    }
}
