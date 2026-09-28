package dev.vanguard.module.modules.combat;

import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.EnumSetting;
import dev.vanguard.setting.NumberSetting;
import dev.vanguard.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Controller-style aim assist: it gently steers the real camera toward the best target every
 * frame instead of snapping. Because it modifies the player's own rotation (rather than sending
 * separate rotation packets), the assist is visible on screen and the player keeps full control.
 *
 * <p>Design goals, drawn from how aim assist behaves in practice:
 * <ul>
 *   <li><b>Assist, don't take over</b> — strength is a per-second pull fraction, capped by a
 *       human-plausible max turn speed, with a deadzone so fine aim stays yours.</li>
 *   <li><b>Frame-rate independent</b> — smoothing uses real elapsed time, so it feels the same
 *       at 60 or 240 fps.</li>
 *   <li><b>Human-like</b> — reaction delay before locking a new target, smoothed jitter, and
 *       eased approach (fast when far, slow when near) rather than a rigid line.</li>
 *   <li><b>Magnetism</b> — optional slowdown that reduces your own mouse sensitivity near a
 *       target, the hallmark of console aim assist.</li>
 *   <li><b>Prediction</b> — leads a strafing target using its smoothed velocity.</li>
 * </ul>
 */
public final class AimAssist extends Module {
    public enum TargetMode { CROSSHAIR, DISTANCE, HEALTH, SMART }

    public enum AimPoint { EYES, BODY, NEAREST }

    public final EnumSetting<TargetMode> targetMode = mode("Target", "How to pick between targets in view.", TargetMode.SMART);
    public final EnumSetting<AimPoint> aimPoint = mode("Aim Point", "Where on the target to aim. Nearest tracks the closest part of the hitbox.", AimPoint.NEAREST);
    public final NumberSetting range = number("Range", "Only assist against targets within this distance.", 4.0, 1, 6, 0.1, "m");
    public final NumberSetting fov = number("FOV", "Only assist while the target is within this cone of your crosshair.", 90, 10, 180, 5, "°");

    public final BoolSetting players = bool("Players", "Assist against players.", true);
    public final BoolSetting hostiles = bool("Hostiles", "Assist against hostile mobs.", false);
    public final BoolSetting passives = bool("Passives", "Assist against passive mobs.", false);

    public final NumberSetting horizontal = number("Horizontal", "Horizontal pull strength.", 45, 0, 100, 1, "%");
    public final NumberSetting vertical = number("Vertical", "Vertical pull strength.", 35, 0, 100, 1, "%");
    public final NumberSetting maxSpeed = number("Max Speed", "Cap on how fast the camera turns.", 400, 40, 1200, 10, "°/s");
    public final NumberSetting deadzone = number("Deadzone", "Stop assisting once the crosshair is this close, so micro-aim stays yours.", 1.0, 0, 8, 0.1, "°");

    public final BoolSetting slowdown = bool("Slowdown", "Reduce your own mouse sensitivity near a target (aim magnetism).", true);
    public final NumberSetting slowdownAmount = number("Slowdown Amount", "How much to slow the mouse near a target.", 55, 0, 90, 1, "%").visibleWhen(slowdown::isOn);

    public final NumberSetting prediction = number("Prediction", "Lead moving targets by their velocity.", 25, 0, 100, 1, "%");
    public final NumberSetting jitter = number("Jitter", "Randomizes aim slightly so it isn't perfectly smooth.", 0.6, 0, 5, 0.1, "°");
    public final NumberSetting reaction = number("Reaction", "Delay before locking onto a new target.", 90, 0, 500, 10, "ms");

    public final BoolSetting requireAttack = bool("While Attacking", "Only assist while holding attack.", true);
    public final BoolSetting requireVisible = bool("Require Visible", "Ignore targets you can't see.", true);

    private static final float SLOWDOWN_RANGE = 14f;
    private static final float MOUSE_TO_DEG = 0.15f;

    private LivingEntity target;
    private long engageAtNanos;
    private long lastFrameNanos;

    // Prediction state.
    private Entity velTarget;
    private Vec3 velLastPos;
    private Vec3 smoothedVel = Vec3.ZERO;

    // Smoothed jitter offsets and where they're easing toward.
    private float jitterYaw, jitterPitch, jitterTargetYaw, jitterTargetPitch;
    private long jitterNextNanos;

    public AimAssist() {
        super("AimAssist", "Steers your aim toward the best target, like console aim assist.", Category.COMBAT);
    }

    @Override
    protected void onDisable() {
        target = null;
        smoothedVel = Vec3.ZERO;
        velTarget = null;
        lastFrameNanos = 0;
    }

    /** Called every frame from the camera-turn hook, after the player's own mouse rotation is applied. */
    public void onTurn(LocalPlayer player, double mouseXo, double mouseYo) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.screen != null) return;

        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0f : (float) Math.min((now - lastFrameNanos) / 1.0e9, 0.1);
        lastFrameNanos = now;
        if (dt <= 0f) return;

        if (requireAttack.isOn() && !mc.options.keyAttack.isDown()) {
            target = null;
            return;
        }

        target = selectTarget(player, mc, now);
        if (target == null) return;
        if (now < engageAtNanos) return;

        Vec3 eye = player.getEyePosition();
        Vec3 point = aimPointFor(player, target, eye).add(leadFor(target, dt));
        float[] rot = RotationUtil.toRotation(eye, point);

        float curYaw = player.getYRot();
        float curPitch = player.getXRot();
        float dYaw = Mth.wrapDegrees(rot[0] - curYaw);
        float dPitch = Mth.clamp(rot[1], -90f, 90f) - curPitch;
        float angle = (float) Math.sqrt(dYaw * dYaw + dPitch * dPitch);

        if (angle > fov.floatValue() / 2f) return;

        // Magnetism: cancel part of the player's own mouse movement when hovering a target.
        if (slowdown.isOn() && angle < SLOWDOWN_RANGE) {
            float proximity = 1f - angle / SLOWDOWN_RANGE;
            float reduce = slowdownAmount.floatValue() / 100f * proximity;
            applyDelta(player, (float) (-mouseXo * MOUSE_TO_DEG * reduce), (float) (-mouseYo * MOUSE_TO_DEG * reduce));
        }

        if (angle <= deadzone.floatValue()) return;

        float stepYaw = dYaw * smoothingAlpha(horizontal.floatValue(), dt);
        float stepPitch = dPitch * smoothingAlpha(vertical.floatValue(), dt);

        float cap = maxSpeed.floatValue() * dt;
        stepYaw = Mth.clamp(stepYaw, -cap, cap);
        stepPitch = Mth.clamp(stepPitch, -cap, cap);

        if (jitter.get() > 0) {
            float[] j = jitter(now, dt);
            stepYaw += j[0];
            stepPitch += j[1];
        }

        applyDelta(player, stepYaw, stepPitch);
    }

    /** Fraction of the remaining angle to close this frame, made frame-rate independent. */
    private static float smoothingAlpha(float strengthPercent, float dt) {
        if (strengthPercent <= 0) return 0f;
        if (strengthPercent >= 100) return 1f;
        // strength is defined at a 20 Hz reference so the feel is stable across frame rates.
        return 1f - (float) Math.pow(1f - strengthPercent / 100f, dt * 20f);
    }

    private void applyDelta(Entity entity, float dYaw, float dPitch) {
        entity.setYRot(entity.getYRot() + dYaw);
        entity.setXRot(Mth.clamp(entity.getXRot() + dPitch, -90f, 90f));
        // Advance the previous-rotation fields too, so the render view doesn't stutter.
        entity.yRotO += dYaw;
        entity.xRotO = Mth.clamp(entity.xRotO + dPitch, -90f, 90f);
    }

    private float[] jitter(long now, float dt) {
        if (now >= jitterNextNanos) {
            float amp = jitter.floatValue();
            jitterTargetYaw = (ThreadLocalRandom.current().nextFloat() * 2f - 1f) * amp;
            jitterTargetPitch = (ThreadLocalRandom.current().nextFloat() * 2f - 1f) * amp * 0.6f;
            jitterNextNanos = now + (long) (120_000_000L + ThreadLocalRandom.current().nextLong(120_000_000L));
        }
        float ease = Math.min(1f, dt * 6f);
        jitterYaw += (jitterTargetYaw - jitterYaw) * ease;
        jitterPitch += (jitterTargetPitch - jitterPitch) * ease;
        return new float[] {jitterYaw * dt * 20f, jitterPitch * dt * 20f};
    }

    private Vec3 aimPointFor(LocalPlayer player, LivingEntity t, Vec3 eye) {
        return switch (aimPoint.get()) {
            case EYES -> t.getEyePosition();
            case BODY -> t.position().add(0, t.getBbHeight() / 2f, 0);
            case NEAREST -> RotationUtil.closestPoint(eye, t.getBoundingBox());
        };
    }

    private Vec3 leadFor(LivingEntity t, float dt) {
        Vec3 pos = t.position();
        Vec3 lead = Vec3.ZERO;
        if (t == velTarget && velLastPos != null && dt > 0) {
            Vec3 instantaneous = pos.subtract(velLastPos).scale(1.0 / dt);
            smoothedVel = smoothedVel.add(instantaneous.subtract(smoothedVel).scale(Math.min(1.0, dt * 10.0)));
            double leadSeconds = prediction.get() / 100.0 * 0.15;
            lead = smoothedVel.scale(leadSeconds);
        } else {
            smoothedVel = Vec3.ZERO;
        }
        velTarget = t;
        velLastPos = pos;
        return lead;
    }

    private LivingEntity selectTarget(LocalPlayer player, Minecraft mc, long now) {
        Vec3 eye = player.getEyePosition();
        float curYaw = player.getYRot();
        float curPitch = player.getXRot();
        double rangeValue = range.get();
        float fovLimit = fov.floatValue() / 2f;

        // Keep the current target while it's still valid — avoids flicking between equals.
        if (target != null && isValidTarget(target, player, mc, eye, curYaw, curPitch, rangeValue, fovLimit)) {
            return target;
        }

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!isValidTarget(living, player, mc, eye, curYaw, curPitch, rangeValue, fovLimit)) continue;
            double score = score(living, eye, curYaw, curPitch);
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }

        if (best != null && best != target) {
            engageAtNanos = now + (long) (reaction.get() * 1_000_000.0);
        }
        target = best;
        return best;
    }

    private boolean isValidTarget(LivingEntity e, LocalPlayer player, Minecraft mc, Vec3 eye,
                                  float curYaw, float curPitch, double rangeValue, float fovLimit) {
        if (e == player || !e.isAlive() || e.isSpectator()) return false;
        if (e instanceof Player && e.isSpectator()) return false;
        if (!typeAllowed(e)) return false;
        if (player.isAlliedTo(e)) return false;

        Vec3 point = RotationUtil.closestPoint(eye, e.getBoundingBox());
        if (eye.distanceToSqr(point) > rangeValue * rangeValue) return false;

        float[] rot = RotationUtil.toRotation(eye, point);
        if (RotationUtil.angleBetween(curYaw, curPitch, rot[0], rot[1]) > fovLimit) return false;

        return !requireVisible.isOn() || isVisible(player, mc, eye, e);
    }

    private boolean typeAllowed(LivingEntity e) {
        if (e instanceof Player) return players.isOn();
        if (e instanceof Monster) return hostiles.isOn();
        if (e instanceof Animal) return passives.isOn();
        // Anything else living (villagers, etc.) counts as passive.
        return passives.isOn();
    }

    private boolean isVisible(LocalPlayer player, Minecraft mc, Vec3 eye, LivingEntity e) {
        // Check a few points on the hitbox so a partly-hidden target still counts.
        AABB box = e.getBoundingBox();
        Vec3[] samples = {
            e.getEyePosition(),
            box.getCenter(),
            RotationUtil.closestPoint(eye, box)
        };
        for (Vec3 sample : samples) {
            HitResult hit = mc.level.clip(new ClipContext(eye, sample, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    private double score(LivingEntity e, Vec3 eye, float curYaw, float curPitch) {
        Vec3 point = RotationUtil.closestPoint(eye, e.getBoundingBox());
        float[] rot = RotationUtil.toRotation(eye, point);
        double angle = RotationUtil.angleBetween(curYaw, curPitch, rot[0], rot[1]);
        double distance = Math.sqrt(eye.distanceToSqr(point));
        double health = e.getHealth();
        return switch (targetMode.get()) {
            case CROSSHAIR -> angle;
            case DISTANCE -> distance;
            case HEALTH -> health * 100.0 + distance;
            case SMART -> angle * 1.0 + distance * 3.0 + health * 0.3;
        };
    }
}
