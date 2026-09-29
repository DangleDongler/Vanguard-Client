package dev.vanguard.gui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.vanguard.mixin.BufferBuilderAccessor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/**
 * One piece of glass: a quad over the glass and its shadow, in screen coordinates, with the pixel
 * offsets from the glass's center at its corners and the shape and optics every vertex carries.
 */
record GlassRenderState(
    float x0, float y0, float x1, float y1,
    float localX0, float localY0, float localX1, float localY1,
    float[] shape,
    float[] optics,
    float[] light,
    int tint,
    TextureSetup textureSetup,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {
    @Override
    public void buildVertices(VertexConsumer consumer) {
        vertex(consumer, x0, y0, localX0, localY0);
        vertex(consumer, x0, y1, localX0, localY1);
        vertex(consumer, x1, y1, localX1, localY1);
        vertex(consumer, x1, y0, localX1, localY0);
    }

    private void vertex(VertexConsumer consumer, float x, float y, float localX, float localY) {
        consumer.addVertex(x, y, 0f).setColor(tint).setUv(localX, localY);
        put(consumer, GlassFormat.SHAPE, shape);
        put(consumer, GlassFormat.OPTICS, optics);
        put(consumer, GlassFormat.LIGHT, light);
    }

    private static void put(VertexConsumer consumer, VertexFormatElement element, float[] values) {
        long address = ((BufferBuilderAccessor) consumer).vanguard$beginElement(element);
        if (address == -1L) return;
        for (int i = 0; i < 4; i++) MemoryUtil.memPutFloat(address + i * 4L, values[i]);
    }

    @Override
    public RenderPipeline pipeline() {
        return VanguardPipelines.GLASS;
    }
}
