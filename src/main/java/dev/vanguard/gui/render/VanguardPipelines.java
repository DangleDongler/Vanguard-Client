package dev.vanguard.gui.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.vanguard.Vanguard;

public final class VanguardPipelines {
    /** Anti-aliased rounded shapes, rings and soft shadows. See shape.fsh for the UV encoding. */
    public static final RenderPipeline SHAPE = RenderPipeline.builder()
        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
        .withUniform("Projection", UniformType.UNIFORM_BUFFER)
        .withLocation(Vanguard.id("pipeline/shape"))
        .withVertexShader(Vanguard.id("core/shape"))
        .withFragmentShader(Vanguard.id("core/shape"))
        .withBlend(BlendFunction.TRANSLUCENT)
        .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .build();

    private VanguardPipelines() {
    }
}
