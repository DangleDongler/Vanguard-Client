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

    /** Liquid glass: refracts, frosts and lights the captured frame. See glass.fsh. */
    public static final RenderPipeline GLASS = RenderPipeline.builder()
        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
        .withUniform("Projection", UniformType.UNIFORM_BUFFER)
        .withLocation(Vanguard.id("pipeline/glass"))
        .withVertexShader(Vanguard.id("core/glass"))
        .withFragmentShader(Vanguard.id("core/glass"))
        .withSampler("Sampler0")
        .withSampler("Sampler1")
        .withBlend(BlendFunction.TRANSLUCENT)
        .withVertexFormat(GlassFormat.FORMAT, VertexFormat.Mode.QUADS)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .build();

    /** The world behind the menu: the captured frame, softened and dimmed. See backdrop.fsh. */
    public static final RenderPipeline BACKDROP = RenderPipeline.builder()
        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
        .withUniform("Projection", UniformType.UNIFORM_BUFFER)
        .withLocation(Vanguard.id("pipeline/backdrop"))
        .withVertexShader(Vanguard.id("core/backdrop"))
        .withFragmentShader(Vanguard.id("core/backdrop"))
        .withSampler("Sampler0")
        .withSampler("Sampler1")
        .withBlend(BlendFunction.TRANSLUCENT)
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .build();

    /** Dual-filter blur: halves the image while blurring it. */
    public static final RenderPipeline BLUR_DOWN = blurPass("blur_down");

    /** Dual-filter blur: doubles the image while blurring it. */
    public static final RenderPipeline BLUR_UP = blurPass("blur_up");

    private static RenderPipeline blurPass(String name) {
        return RenderPipeline.builder()
            .withLocation(Vanguard.id("pipeline/" + name))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Vanguard.id("core/" + name))
            .withSampler("InSampler")
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
            .build();
    }

    private VanguardPipelines() {
    }
}
