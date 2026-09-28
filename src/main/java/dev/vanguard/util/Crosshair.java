package dev.vanguard.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * What the crosshair is on this frame, for combat modules.
 *
 * <p>Besides where entities are drawn, it can check their latest position from the server. Your
 * game draws other players and mobs a little behind that: each position update is eased in over
 * three ticks so movement looks smooth. The server judges hits from its own position.
 */
public final class Crosshair {
    /** Beyond this gap between drawn and server position, the server position is treated as stale. */
    private static final double SERVER_POSITION_DRIFT = 4.0;

    private Crosshair() {
    }

    /**
     * The target under the crosshair right now, re-picking with this frame's camera. Returns the
     * closest valid target within {@code reach}, checking both the drawn hitbox and (optionally) the
     * hitbox at the latest server position, never through blocks. Null if there is none.
     */
    public static EntityHitResult pick(Minecraft mc, LocalPlayer player, double reach,
                                       Predicate<LivingEntity> valid, boolean useServerPosition) {
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        mc.gameRenderer.pick(partialTick);
        Vec3 eye = player.getEyePosition(partialTick);

        EntityHitResult drawn = mc.hitResult instanceof EntityHitResult entityHit
            && entityHit.getEntity() instanceof LivingEntity living && valid.test(living)
            && eye.distanceTo(entityHit.getLocation()) <= reach + 1.0e-4 ? entityHit : null;
        if (!useServerPosition) return drawn;

        Vec3 look = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
        double limit = reach;
        if (drawn != null) limit = Math.min(limit, eye.distanceTo(drawn.getLocation()));
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            limit = Math.min(limit, eye.distanceTo(mc.hitResult.getLocation()));
        }
        Vec3 end = eye.add(look.scale(limit));
        AABB search = new AABB(eye, end).inflate(SERVER_POSITION_DRIFT + 1.0);

        EntityHitResult best = drawn;
        double bestDistance = limit;
        for (Entity entity : mc.level.getEntities(player, search)) {
            if (!(entity instanceof LivingEntity living) || !valid.test(living)) continue;
            AABB box = serverBox(living);
            if (box == null) continue;
            Vec3 point = box.contains(eye) ? eye : box.clip(eye, end).orElse(null);
            if (point == null) continue;
            double distance = eye.distanceTo(point);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new EntityHitResult(living, point);
            }
        }
        return best;
    }

    /** Points the game's own attack at {@code hit}, exactly as if the crosshair pick had found it. */
    public static void aimAt(Minecraft mc, EntityHitResult hit) {
        mc.hitResult = hit;
        mc.crosshairPickEntity = hit.getEntity();
    }

    /** The entity's latest position from the server, or where it's drawn if that looks stale. */
    public static Vec3 serverPosition(Entity entity) {
        Vec3 server = entity.getPositionCodec().getBase();
        return server.distanceToSqr(entity.position()) > SERVER_POSITION_DRIFT * SERVER_POSITION_DRIFT
            ? entity.position() : server;
    }

    /**
     * The entity's hitbox at the latest position the server sent, or null if that looks stale (a
     * teleport doesn't update it, so a big gap means it can't be trusted).
     */
    public static AABB serverBox(Entity entity) {
        Vec3 offset = entity.getPositionCodec().getBase().subtract(entity.position());
        if (offset.lengthSqr() > SERVER_POSITION_DRIFT * SERVER_POSITION_DRIFT) return null;
        return entity.getBoundingBox().move(offset);
    }
}
