package dev.vanguard.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Writes the glass renderer's own vertex elements, which BufferBuilder has no setter for. */
@Mixin(BufferBuilder.class)
public interface BufferBuilderAccessor {
    /** Memory address to write {@code element} of the current vertex to, or -1 if the format lacks it. */
    @Invoker("beginElement")
    long vanguard$beginElement(VertexFormatElement element);
}
