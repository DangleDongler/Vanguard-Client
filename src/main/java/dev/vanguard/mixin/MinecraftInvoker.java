package dev.vanguard.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets modules attack through the same code as a real left click. */
@Mixin(Minecraft.class)
public interface MinecraftInvoker {
    @Invoker("startAttack")
    boolean vanguard$startAttack();
}
