package dev.vanguard.gui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import org.jspecify.annotations.Nullable;

import java.util.OptionalInt;

/**
 * What the glass looks through: a copy of the frame as it is just before the menu draws, and a
 * blurred version of it.
 *
 * <p>The menu asks for a capture while it builds its frame ({@link #request()}); Minecraft's GUI
 * renderer then calls {@link #captureIfRequested()} at the point where it would otherwise blur the
 * screen, after the world and HUD and before the menu. The blur is a dual-filter (Kawase) blur:
 * a few halvings then as many doublings, each averaging a handful of taps.
 */
public final class Backdrop {
    private static final Backdrop INSTANCE = new Backdrop();
    private static final String LABEL = "Vanguard glass";
    private static final int MAX_LEVELS = 5;

    private @Nullable GpuTexture frame;
    private @Nullable GpuTextureView frameView;
    private final GpuTexture[] down = new GpuTexture[MAX_LEVELS];
    private final GpuTextureView[] downViews = new GpuTextureView[MAX_LEVELS];
    private final GpuTexture[] up = new GpuTexture[MAX_LEVELS];
    private final GpuTextureView[] upViews = new GpuTextureView[MAX_LEVELS];
    private int width = -1;
    private int height = -1;
    private int levels;
    private @Nullable TextureSetup textures;
    private boolean requested;

    private Backdrop() {
    }

    public static Backdrop get() {
        return INSTANCE;
    }

    /**
     * Asks for this frame to be captured before the menu draws, and returns the textures to sample:
     * the frame, then its blurred copy, or null when there's no frame. The menu must also have called
     * {@code GuiGraphics.blurBeforeThisStratum()}, which is what makes the GUI renderer stop there.
     */
    public @Nullable TextureSetup request() {
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        // A minimized window has nothing to look through; the glass draws as plain shapes.
        if (target.width < 2 || target.height < 2) return null;
        ensureTextures(target.width, target.height);
        requested = true;
        return textures;
    }

    /** Captures and blurs the frame if a capture was asked for this frame. Returns whether it did. */
    public boolean captureIfRequested() {
        if (!requested) return false;
        requested = false;
        capture();
        return true;
    }

    /** Drops a request the GUI renderer never reached, so it can't swallow another screen's blur. */
    public void endFrame() {
        requested = false;
    }

    /** Frees the textures while no menu uses them. */
    public void release() {
        requested = false;
        closeTextures();
    }

    private void capture() {
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        GpuTexture color = target.getColorTexture();
        if (color == null || frame == null || color.getWidth(0) != width || color.getHeight(0) != height) return;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(color, frame, 0, 0, 0, 0, 0, width, height);
        GpuTextureView source = frameView;
        for (int i = 0; i < levels; i++) {
            pass(encoder, VanguardPipelines.BLUR_DOWN, source, downViews[i]);
            source = downViews[i];
        }
        for (int i = levels - 2; i >= 0; i--) {
            pass(encoder, VanguardPipelines.BLUR_UP, source, upViews[i]);
            source = upViews[i];
        }
    }

    private static void pass(CommandEncoder encoder, RenderPipeline pipeline, GpuTextureView in, GpuTextureView out) {
        try (RenderPass pass = encoder.createRenderPass(() -> LABEL + " blur", out, OptionalInt.empty())) {
            pass.setPipeline(pipeline);
            pass.bindTexture("InSampler", in, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(0, 3);
        }
    }

    private void ensureTextures(int w, int h) {
        if (frame != null && w == width && h == height) return;
        closeTextures();
        width = w;
        height = h;
        GpuDevice device = RenderSystem.getDevice();
        int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
        frame = device.createTexture(() -> LABEL + " / frame", usage | GpuTexture.USAGE_COPY_DST, TextureFormat.RGBA8, w, h, 1, 1);
        frameView = device.createTextureView(frame);

        // Enough halvings that the blur covers a similar share of the screen at any resolution.
        levels = Math.clamp(Math.round((float) (Math.log(h / 135.0) / Math.log(2))), 2, MAX_LEVELS);
        int lw = w, lh = h;
        for (int i = 0; i < levels; i++) {
            lw = Math.max(1, lw / 2);
            lh = Math.max(1, lh / 2);
            int level = i;
            down[i] = device.createTexture(() -> LABEL + " / down " + level, usage, TextureFormat.RGBA8, lw, lh, 1, 1);
            downViews[i] = device.createTextureView(down[i]);
            if (i < levels - 1) {
                up[i] = device.createTexture(() -> LABEL + " / up " + level, usage, TextureFormat.RGBA8, lw, lh, 1, 1);
                upViews[i] = device.createTextureView(up[i]);
            }
        }
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        textures = new TextureSetup(frameView, upViews[0], null, linear, linear, null);
    }

    private void closeTextures() {
        if (frameView != null) frameView.close();
        if (frame != null) frame.close();
        frameView = null;
        frame = null;
        for (int i = 0; i < MAX_LEVELS; i++) {
            if (downViews[i] != null) downViews[i].close();
            if (down[i] != null) down[i].close();
            if (upViews[i] != null) upViews[i].close();
            if (up[i] != null) up[i].close();
            downViews[i] = upViews[i] = null;
            down[i] = up[i] = null;
        }
        textures = null;
        width = height = -1;
    }
}
