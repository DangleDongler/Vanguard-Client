package dev.vanguard.module.modules.combat;

import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.EnumSetting;
import dev.vanguard.setting.NumberSetting;
import dev.vanguard.util.AimSpring;
import dev.vanguard.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Controller-style aim assist: it gently steers the real camera toward the best target every
 * frame instead of snapping, so you keep full control and see exactly what it does.
 *
 * <p>What keeps the motion smooth:
 * <ul>
 *   <li><b>Interpolated positions</b>: players and mobs only move 20 times a second (each game
 *       tick), but frames are drawn far more often. Aiming at those raw positions makes the aim
 *       jump in steps. This aims where the target is <em>drawn</em> in the current frame.</li>
 *   <li><b>A spring, not easing</b>: see {@link AimSpring}. The turn speed ramps up and settles
 *       without overshooting, and moves the same at 30 or 240 fps.</li>
 *   <li><b>Follow</b>: the target's sideways motion is fed forward, so a strafing target is
 *       tracked instead of trailed.</li>
 *   <li><b>No hop chasing</b>: vertical aim only corrects when your crosshair is off the hitbox,
 *       and a target bouncing up to a jump's height (their jumps, or the knockback from your own
 *       hits) is treated as still standing where they were. Chasing those hops swung the camera
 *       up and down after every hit.</li>
 *   <li><b>No hard edges</b>: the assist fades in when it picks a target and fades out near the
 *       edge of the field of view and range, so it never starts or stops abruptly.</li>
 * </ul>
 */
public final class AimAssist extends Module {
    public enum AimAt { CLOSEST, HEAD, BODY }

    public enum Priority { CROSSHAIR, NEAREST, LOWEST_HEALTH }

    public final NumberSetting speed = number("Speed", "How quickly your aim is pulled onto the target.", 50, 1, 100, 1, "%");
    public final EnumSetting<AimAt> aimAt = mode("Aim At", "Where on the target to aim. Closest only helps when your crosshair is off their hitbox.", AimAt.CLOSEST);
    public final BoolSetting vertical = bool("Vertical", "Also help you aim up and down. Off: left and right only.", true);
    public final NumberSetting range = number("Range", "How close a target has to be.", 4.5, 1, 8, 0.1, "m");
    public final NumberSetting fov = number("Field of View", "How far from your crosshair a target can be and still get help.", 90, 10, 180, 5, "°");
    public final EnumSetting<Priority> priority = mode("Priority", "Which target to pick when several are in view.", Priority.CROSSHAIR);
    public final BoolSetting players = bool("Players", "Help aim at players.", true);
    public final BoolSetting mobs = bool("Mobs", "Help aim at mobs and animals.", false);
    public final BoolSetting throughWalls = bool("Through Walls", "Also aim at targets hidden behind blocks.", false);

    /** Stiffness range for the spring, in radians per second, mapped from Speed. */
    private static final double OMEGA_MIN = 3, OMEGA_MAX = 22;
    /** Hard cap on how fast the assist can turn the camera. */
    private static final double MAX_TURN_SPEED = 720;
    /** How long the assist takes to fade in on a new target. */
    private static final double FADE_IN_SECONDS = 0.15;
    /** The assist fades out over the outer part of the field of view and the last bit of range. */
    private static final double FOV_FADE_START = 0.75, RANGE_FADE_BLOCKS = 0.75;
    /** How high above their last footing a target can bounce before the aim follows them up. */
    private static final double MAX_HOP = 1.3;
    /** Smoothing for the target's measured turning speed. */
    private static final double RATE_SMOOTHING_SECONDS = 0.05;
    /** A gap between frames longer than this (a pause or a screen) restarts the motion. */
    private static final double MAX_FRAME_SECONDS = 0.15;

    private final AimSpring yawSpring = new AimSpring();
    private final AimSpring pitchSpring = new AimSpring();

    private LivingEntity target;
    private long acquiredAtNanos;
    private long lastFrameNanos;

    // The target's direction last frame and how fast it's turning sideways (degrees per second).
    private boolean haveLastDirection;
    private float lastTargetYaw;
    private double targetYawRate;

    // Where the target last stood, to tell a short hop from real vertical movement.
    private boolean haveGround;
    private double groundY;

    public AimAssist() {
        super("AimAssist", "Smoothly pulls your aim toward the best target, like console aim assist.", Category.COMBAT);
    }

    @Override
    protected void onDisable() {
        target = null;
        lastFrameNanos = 0;
        resetMotion();
    }

    private void resetMotion() {
        yawSpring.reset();
        pitchSpring.reset();
        haveLastDirection = false;
        targetYawRate = 0;
    }

    /** Called every frame from the camera-turn hook, after the player's own mouse movement. */
    public void onTurn(LocalPlayer player) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.screen != null) return;

        long now = System.nanoTime();
        double dt = lastFrameNanos == 0 ? 0 : (now - lastFrameNanos) / 1.0e9;
        lastFrameNanos = now;
        if (dt <= 0 || dt > MAX_FRAME_SECONDS) {
            resetMotion();
            return;
        }

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = Vec3.directionFromRotation(player.getXRot(), player.getYRot());

        LivingEntity picked = selectTarget(player, mc, eye, look, partialTick);
        if (picked != target) {
            target = picked;
            acquiredAtNanos = now;
            haveLastDirection = false;
            targetYawRate = 0;
            haveGround = false;
        }

        double errorYaw = 0, errorPitch = 0, rateYaw = 0, weight = 0;
        if (target != null) {
            AABB drawn = drawnBox(target, partialTick);
            double lift = hopHeight(drawn);
            AABB box = drawn.move(0, -lift, 0);
            float[] aim = aimRotation(eye, look, box, lift, partialTick);
            if (aim != null) {
                errorYaw = Mth.wrapDegrees(aim[0] - player.getYRot());
                errorPitch = aim[1] - player.getXRot();
            }
            measureTargetRate(eye, box.getCenter(), dt);
            rateYaw = targetYawRate;
            weight = fadeIn(now) * fovFade(eye, look, drawn) * rangeFade(eye, drawn);
        }

        double strength = speed.get() / 100.0;
        double omega = OMEGA_MIN + (OMEGA_MAX - OMEGA_MIN) * strength;
        double follow = 0.25 + 0.75 * strength;

        double turnYaw = yawSpring.step(weight * errorYaw, weight * rateYaw, follow, omega, dt);
        yawSpring.limitVelocity(MAX_TURN_SPEED);
        double turnPitch = 0;
        if (vertical.isOn()) {
            // No follow on pitch: the vertical aim only moves when the crosshair is off the hitbox.
            turnPitch = pitchSpring.step(weight * errorPitch, 0, 0, omega, dt);
            pitchSpring.limitVelocity(MAX_TURN_SPEED);
        } else {
            pitchSpring.reset();
        }

        if (turnYaw != 0 || turnPitch != 0) applyTurn(player, (float) turnYaw, (float) turnPitch);
    }

    private void applyTurn(Entity entity, float dYaw, float dPitch) {
        entity.setYRot(entity.getYRot() + dYaw);
        entity.setXRot(Mth.clamp(entity.getXRot() + dPitch, -90f, 90f));
        // Advance the previous-rotation fields too, so the body and third-person view don't stutter.
        entity.yRotO += dYaw;
        entity.xRotO = Mth.clamp(entity.xRotO + dPitch, -90f, 90f);
    }

    /** The target's hitbox where it's drawn this frame, between its last two tick positions. */
    private static AABB drawnBox(Entity entity, float partialTick) {
        return entity.getBoundingBox().move(entity.getPosition(partialTick).subtract(entity.position()));
    }

    /**
     * How much of the target's height above where it last stood to ignore. A hop up to a jump's
     * height ({@link #MAX_HOP}) is ignored entirely, as if they were still standing there. Higher
     * than that, it eases back to zero by twice that height, so launches (wind charges, mace
     * fights) are tracked fully, and so are falls to lower ground.
     */
    private double hopHeight(AABB drawn) {
        if (target.onGround()) {
            groundY = target.getY();
            haveGround = true;
        }
        if (!haveGround) return 0.0;
        double height = drawn.minY - groundY;
        if (height <= 0) return 0.0;
        return height <= MAX_HOP ? height : Math.max(0.0, 2 * MAX_HOP - height);
    }

    /** Yaw and pitch to aim at, or null when the crosshair is already where it should be. */
    private float[] aimRotation(Vec3 eye, Vec3 look, AABB box, double lift, float partialTick) {
        Vec3 point = switch (aimAt.get()) {
            case HEAD -> target.getEyePosition(partialTick).subtract(0, lift, 0);
            case BODY -> box.getCenter();
            case CLOSEST -> {
                // Aim a little inside the edge, so the crosshair ends up on the target.
                AABB inner = box.deflate(box.getXsize() * 0.2, box.getYsize() * 0.1, box.getZsize() * 0.2);
                if (RotationUtil.rayHits(eye, look, inner, range.get() + 4)) yield null;
                yield RotationUtil.closestPointToRay(eye, look, inner);
            }
        };
        return point == null ? null : RotationUtil.toRotation(eye, point);
    }

    /** Tracks how fast the target is moving sideways across your view, so the assist can move with it. */
    private void measureTargetRate(Vec3 eye, Vec3 center, double dt) {
        float yaw = RotationUtil.toRotation(eye, center)[0];
        if (haveLastDirection) {
            double yawRate = Mth.wrapDegrees(yaw - lastTargetYaw) / dt;
            double blend = 1 - Math.exp(-dt / RATE_SMOOTHING_SECONDS);
            targetYawRate += (yawRate - targetYawRate) * blend;
        }
        lastTargetYaw = yaw;
        haveLastDirection = true;
    }

    private double fadeIn(long now) {
        return smoothstep((now - acquiredAtNanos) / 1.0e9 / FADE_IN_SECONDS);
    }

    private double fovFade(Vec3 eye, Vec3 look, AABB box) {
        double half = fov.get() / 2.0;
        double angle = angleTo(eye, look, box);
        return 1 - smoothstep((angle - half * FOV_FADE_START) / (half * (1 - FOV_FADE_START)));
    }

    private double rangeFade(Vec3 eye, AABB box) {
        double distance = Math.sqrt(box.distanceToSqr(eye));
        return 1 - smoothstep((distance - (range.get() - RANGE_FADE_BLOCKS)) / RANGE_FADE_BLOCKS);
    }

    private static double smoothstep(double x) {
        double t = Mth.clamp(x, 0.0, 1.0);
        return t * t * (3 - 2 * t);
    }

    /** Degrees the crosshair has to turn to reach the nearest part of the box (0 when it's on it). */
    private static double angleTo(Vec3 eye, Vec3 look, AABB box) {
        if (RotationUtil.rayHits(eye, look, box, 64)) return 0;
        Vec3 toPoint = RotationUtil.closestPointToRay(eye, look, box).subtract(eye).normalize();
        return Math.toDegrees(Math.acos(Mth.clamp(toPoint.dot(look), -1.0, 1.0)));
    }

    private LivingEntity selectTarget(LocalPlayer player, Minecraft mc, Vec3 eye, Vec3 look, float partialTick) {
        // Keep the current target while it's still valid, so the assist doesn't flick between equals.
        if (target != null && isValidTarget(target, player, mc, eye, look, partialTick)) return target;

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!isValidTarget(living, player, mc, eye, look, partialTick)) continue;
            double score = score(living, eye, look, partialTick);
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }

    private boolean isValidTarget(LivingEntity e, LocalPlayer player, Minecraft mc, Vec3 eye, Vec3 look, float partialTick) {
        if (e == player || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand) return false;
        if (e instanceof Player ? !players.isOn() : !mobs.isOn()) return false;
        if (player.isAlliedTo(e)) return false;

        AABB box = drawnBox(e, partialTick);
        if (box.distanceToSqr(eye) > range.get() * range.get()) return false;
        if (angleTo(eye, look, box) > fov.get() / 2.0) return false;
        return throughWalls.isOn() || isVisible(player, mc, eye, box, e.getEyePosition(partialTick));
    }

    private static boolean isVisible(LocalPlayer player, Minecraft mc, Vec3 eye, AABB box, Vec3 targetEye) {
        // Check a few points on the hitbox so a partly hidden target still counts.
        Vec3[] samples = {targetEye, box.getCenter(), RotationUtil.closestPoint(eye, box)};
        for (Vec3 sample : samples) {
            HitResult hit = mc.level.clip(new ClipContext(eye, sample, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    private double score(LivingEntity e, Vec3 eye, Vec3 look, float partialTick) {
        AABB box = drawnBox(e, partialTick);
        double angle = angleTo(eye, look, box);
        return switch (priority.get()) {
            case CROSSHAIR -> angle;
            case NEAREST -> Math.sqrt(box.distanceToSqr(eye));
            case LOWEST_HEALTH -> e.getHealth() * 1000.0 + angle;
        };
    }
}
