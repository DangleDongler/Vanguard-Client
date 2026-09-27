package dev.vanguard.gui.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.vanguard.Vanguard;
import net.minecraft.client.renderer.BindGroupLayouts;

public final class VanguardPipelines {
    /** Anti-aliased rounded shapes, rings and soft shadows. See shape.fsh for the UV encoding. */
    public static final RenderPipeline SHAPE = RenderPipeline.builder()
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
        .withLocation(Vanguard.id("pipeline/shape"))
        .withVertexShader(Vanguard.id("core/shape"))
        .withFragmentShader(Vanguard.id("core/shape"))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .build();

    private VanguardPipelines() {
    }
}
