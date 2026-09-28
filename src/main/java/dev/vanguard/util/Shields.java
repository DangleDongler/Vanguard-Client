package dev.vanguard.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.phys.Vec3;

/**
 * The game's shield rules, as the server applies them (see {@code LivingEntity#applyItemBlocking}
 * and {@code Player#blockUsingItem}).
 *
 * <ul>
 *   <li>A shield only blocks once it has been held up for its block delay (5 ticks for a shield).
 *       {@code getItemBlockingWith} already accounts for that.</li>
 *   <li>It only covers hits from within its blocking angle (90 degrees either side) of where the
 *       holder's head is facing, measured flat.</li>
 *   <li>A blocked hit from a weapon that disables blocking (every axe: 5 seconds) puts all of the
 *       holder's shields on cooldown and lowers them. It needs no charge and has no randomness, and
 *       it works even while the holder is still immune from a previous hit.</li>
 * </ul>
 */
public final class Shields {
    private Shields() {
    }

    /** Whether the target's raised shield would block a melee hit from {@code attackerPos}. */
    public static boolean wouldBlock(LivingEntity target, Vec3 attackerPos) {
        ItemStack shield = target.getItemBlockingWith();
        if (shield == null) return false;
        BlocksAttacks blocks = shield.get(DataComponents.BLOCKS_ATTACKS);
        if (blocks == null) return false;
        double maxAngle = 0;
        for (BlocksAttacks.DamageReduction reduction : blocks.damageReductions()) {
            maxAngle = Math.max(maxAngle, reduction.horizontalBlockingAngle());
        }
        Vec3 from = Crosshair.serverPosition(target);
        return coversDirection(target.getYHeadRot(), attackerPos.x - from.x, attackerPos.z - from.z, maxAngle);
    }

    /** Whether the target's raised shield can be disabled at all (a cooldown scale of 0 means never). */
    public static boolean canBeDisabled(LivingEntity target) {
        ItemStack shield = target.getItemBlockingWith();
        BlocksAttacks blocks = shield == null ? null : shield.get(DataComponents.BLOCKS_ATTACKS);
        return blocks != null && blocks.disableCooldownScale() > 0;
    }

    /** Whether hitting a raised shield with this item disables it (every axe does). */
    public static boolean disablesShields(ItemStack weapon) {
        Weapon component = weapon.get(DataComponents.WEAPON);
        return component != null && component.disableBlockingForSeconds() > 0;
    }

    /**
     * Whether a hit coming from ({@code dx}, {@code dz}), relative to the holder, is inside the
     * shield's cover when the holder's head faces {@code headYaw}. Same math as the server: the flat
     * angle between where they look and the direction to the attacker.
     */
    public static boolean coversDirection(float headYaw, double dx, double dz, double maxAngleDegrees) {
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0e-7) return true;
        double yaw = Math.toRadians(headYaw);
        // Minecraft's facing for a yaw: x = -sin, z = cos.
        double dot = (-Math.sin(yaw) * dx + Math.cos(yaw) * dz) / length;
        double angle = Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
        return angle <= Math.toRadians(maxAngleDegrees) + 1.0e-9;
    }
}
