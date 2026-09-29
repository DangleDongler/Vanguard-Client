package dev.vanguard.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
    /** The hotbar slot the client last told the server it's holding. */
    @Accessor("carriedIndex")
    int vanguard$carriedIndex();
}
