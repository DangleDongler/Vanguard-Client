package dev.vanguard.gui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.jspecify.annotations.Nullable;

/**
 * The captured frame redrawn over the whole screen, softened and dimmed. Its settings ride in the
 * vertex color: red is how blurred, green how dimmed, blue the vignette, alpha the opacity.
 */
record BackdropRenderState(float width, float height, int params, TextureSetup textureSetup) implements GuiElementRenderState {
    @Override
    public void buildVertices(VertexConsumer consumer) {
        consumer.addVertex(0f, 0f, 0f).setColor(params);
        consumer.addVertex(0f, height, 0f).setColor(params);
        consumer.addVertex(width, height, 0f).setColor(params);
        consumer.addVertex(width, 0f, 0f).setColor(params);
    }

    @Override
    public RenderPipeline pipeline() {
        return VanguardPipelines.BACKDROP;
    }

    @Override
    public @Nullable ScreenRectangle scissorArea() {
        return null;
    }

    @Override
    public ScreenRectangle bounds() {
        return new ScreenRectangle(0, 0, (int) Math.ceil(width), (int) Math.ceil(height));
    }
}
