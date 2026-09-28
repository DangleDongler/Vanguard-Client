package dev.vanguard.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Yaw/pitch math for aiming at a point or an entity hitbox. */
public final class RotationUtil {
    private RotationUtil() {
    }

    /**
     * Yaw and pitch (degrees) that point from {@code from} to {@code to}, matching Minecraft's
     * convention: yaw 0 faces +Z, increasing clockwise; positive pitch looks down.
     */
    public static float[] toRotation(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new float[] {yaw, Mth.clamp(pitch, -90f, 90f)};
    }

    /** The point on (or in) {@code box} closest to {@code point}; aims at the nearest surface. */
    public static Vec3 closestPoint(Vec3 point, AABB box) {
        return new Vec3(
            Mth.clamp(point.x, box.minX, box.maxX),
            Mth.clamp(point.y, box.minY, box.maxY),
            Mth.clamp(point.z, box.minZ, box.maxZ));
    }

    /**
     * The point on {@code box} that needs the least turning to aim at from {@code origin} looking
     * along {@code direction}: the spot on the ray level with the box's center, pulled into the box.
     */
    public static Vec3 closestPointToRay(Vec3 origin, Vec3 direction, AABB box) {
        double along = Math.max(0.0, box.getCenter().subtract(origin).dot(direction));
        return closestPoint(origin.add(direction.scale(along)), box);
    }

    /** Whether a ray from {@code origin} along {@code direction} passes through {@code box}. */
    public static boolean rayHits(Vec3 origin, Vec3 direction, AABB box, double maxDistance) {
        return box.contains(origin) || box.clip(origin, origin.add(direction.scale(maxDistance))).isPresent();
    }

    /** Straight-line angle (degrees) between two yaw/pitch rotations. */
    public static float angleBetween(float yaw1, float pitch1, float yaw2, float pitch2) {
        float dYaw = Mth.wrapDegrees(yaw2 - yaw1);
        float dPitch = pitch2 - pitch1;
        return (float) Math.sqrt(dYaw * dYaw + dPitch * dPitch);
    }
}
