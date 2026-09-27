package dev.vanguard.gui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/** One submitted shape: a batch of quads drawn with {@link VanguardPipelines#SHAPE}. */
record ShapeRenderState(
    Matrix3x2fc pose,
    float[] vertices,
    int[] colors,
    int vertexCount,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {
    static final int FLOATS_PER_VERTEX = 4;

    @Override
    public void buildVertices(VertexConsumer consumer) {
        for (int i = 0; i < vertexCount; i++) {
            int o = i * FLOATS_PER_VERTEX;
            consumer.addVertexWith2DPose(pose, vertices[o], vertices[o + 1])
                .setUv(vertices[o + 2], vertices[o + 3])
                .setColor(colors[i]);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return VanguardPipelines.SHAPE;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }
}
