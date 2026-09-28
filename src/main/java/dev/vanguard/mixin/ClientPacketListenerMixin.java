package dev.vanguard.mixin;

import dev.vanguard.Vanguard;
import dev.vanguard.module.modules.combat.TriggerBot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reads a few of the server's packets: swings (for Hit Select) and sounds (shield breaks and blocks,
 * for the shield tracker; the sounds of your own hits, for the sprint tracker). Each handler first runs on the network thread, which only hands the
 * packet over to the game thread; the second run, on the game thread, is the one that reaches the
 * end of the method, so these see each packet once, in order with the rest of the game.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleAnimate", at = @At("TAIL"))
    private void vanguard$onAnimate(ClientboundAnimatePacket packet, CallbackInfo ci) {
        Vanguard vanguard = Vanguard.get();
        if (vanguard == null) return;
        TriggerBot triggerBot = vanguard.triggerBot();
        if (triggerBot == null) return;
        int action = packet.getAction();
        if (action == ClientboundAnimatePacket.SWING_MAIN_HAND || action == ClientboundAnimatePacket.SWING_OFF_HAND) {
            triggerBot.onSwingPacket(packet.getId());
        }
    }

    @Inject(method = "handleSoundEvent", at = @At("TAIL"))
    private void vanguard$onSound(ClientboundSoundPacket packet, CallbackInfo ci) {
        Vanguard vanguard = Vanguard.get();
        if (vanguard == null) return;
        vanguard.shieldTracker().onSound(Minecraft.getInstance(), packet.getSound(), packet.getX(), packet.getY(), packet.getZ());
        vanguard.sprintTracker().onSound(packet.getSound(), packet.getX(), packet.getY(), packet.getZ());
    }
}
