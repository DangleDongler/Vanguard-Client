package dev.vanguard.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
    /** The sprint state the client last sent to the server. */
    @Accessor("wasSprinting")
    boolean vanguard$wasSprinting();
}
