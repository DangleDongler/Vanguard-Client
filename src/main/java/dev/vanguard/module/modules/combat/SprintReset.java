package dev.vanguard.module.modules.combat;

import dev.vanguard.Vanguard;
import dev.vanguard.mixin.LocalPlayerAccessor;
import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.EnumSetting;
import dev.vanguard.util.Crosshair;
import dev.vanguard.util.SprintTap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Restarts your sprint between hits, so each hit is a sprint hit with extra knockback.
 *
 * <p><b>Why it's needed:</b> a charged hit while sprinting knocks the target back much further (on
 * top of the normal 0.4, another 0.5 in the direction you face, about 75% more). But landing it
 * makes the server stop your sprint without telling your game, which keeps you running at full
 * speed. So from the second hit on, the server thinks you're walking and your hits get no extra
 * knockback, until your sprint really stops and starts again (see
 * {@link dev.vanguard.util.ServerSprintTracker}). A W-tap or S-tap does exactly that.
 *
 * <p>It works by changing your movement keys for a tick or two, exactly as if you had tapped them,
 * so the game itself sends the usual sprint stop and start. It only resets when the server really
 * thinks you've stopped sprinting, never when you aren't holding forward, and only in a fight (you
 * hit someone nearby, or another player is close).
 *
 * <p><b>Crits:</b> a sprint hit can't crit. After a sprint hit you're in the sprint-crit state, where
 * every falling hit crits, so with Keep Crits it doesn't reset while you're in the air. On the ground,
 * where no crit is possible, it always resets. It also never lets go of forward on the tick you
 * jump, which would lose the sprint jump's boost.
 *
 * <p>TriggerBot waits for a reset that's under way (at most a few ticks) before it swings.
 */
public final class SprintReset extends Module {
    public enum Method {
        W_TAP("W-Tap"), S_TAP("S-Tap");

        private final String label;

        Method(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public final EnumSetting<Method> method = mode("Method", "W-Tap: lets go of forward for one tick just before your weapon is ready. Keeps almost all your speed. S-Tap: steps back right after a hit while the opponent can reach you, then sprints back in as your weapon gets ready. Keeps your spacing.", Method.W_TAP);
    public final BoolSetting keepCrits = bool("Keep Crits", "Doesn't reset while you're in the air, so your falling hits still crit (a sprint hit can't crit). On the ground it always resets.", true);
    public final BoolSetting skipRunners = bool("Skip Runners", "Doesn't reset when your target is running away from you: extra knockback only helps them get away.", true);

    /** Someone within this distance means a fight is on and a next hit is coming. */
    private static final double FIGHT_RANGE = 12.0;
    /** How close an opponent must be to hit you (their eyes to your hitbox, with a little margin). */
    private static final double OPPONENT_REACH = 3.2;
    /** Running away: faster than this (blocks per tick) away from you... */
    private static final double RUNNING_AWAY_SPEED = 0.1;
    /** ...while facing more than 120 degrees away from you. */
    private static final double FACING_AWAY_COS = -0.5;
    /** Longest TriggerBot waits for a reset. */
    private static final int MAX_HOLD_TICKS = 4;
    /** Ticks of the opponent's movement to average when judging where they're going. */
    private static final int TRAIL_TICKS = 4;

    private final SprintTap planner = new SprintTap();
    private LocalPlayer trackedPlayer;
    private LivingEntity lastTarget;
    private int holdStartTick = -1;
    /** Where the opponent was on each of our last few ticks, newest last. */
    private final Vec3[] trail = new Vec3[TRAIL_TICKS + 1];
    private int trailSize;
    private LivingEntity trailEntity;

    public SprintReset() {
        super("SprintReset", "Restarts your sprint between hits (W-tap or S-tap) so every hit has sprint knockback.", Category.COMBAT);
    }

    /** Called for every attack the client sends, including your own clicks. */
    public void onAttack(Entity target) {
        if (target instanceof LivingEntity living) lastTarget = living;
        holdStartTick = -1;
    }

    /**
     * Called once per tick, right after the game reads your movement keys and before it moves you or
     * updates your sprint. Returns the keys to use this tick.
     */
    public Input adjustInput(LocalPlayer player, Input keys) {
        if (player != trackedPlayer) {
            trackedPlayer = player;
            planner.cancel();
            lastTarget = null;
        }
        boolean active = isEnabled();
        // Turned off halfway through a reset: still finish it, so your sprint comes back.
        if (!active && !planner.busy()) return keys;

        Minecraft mc = Minecraft.getInstance();
        boolean forwardHeld = keys.forward() && !keys.backward();
        LivingEntity opponent = active ? opponent(mc, player) : null;
        recordTrail(opponent);
        boolean needsReset = active && needsReset(player);
        boolean allowed = needsReset && opponent != null && resetAllowed(player, opponent, keys);
        boolean sTap = active && method.get() == Method.S_TAP;
        boolean tooClose = opponent != null && tooClose(player, opponent);
        boolean safeBehind = sTap && tooClose && safeBehind(player);

        SprintTap.Situation situation = new SprintTap.Situation(forwardHeld, player.isSprinting(), needsReset, allowed,
            sTap, tooClose, safeBehind, ticksUntilReady(player));
        return switch (planner.next(situation)) {
            case NONE -> keys;
            case RELEASE -> new Input(false, keys.backward(), keys.left(), keys.right(), keys.jump(), keys.shift(), keys.sprint());
            case BACK_OFF -> new Input(false, true, keys.left(), keys.right(), keys.jump(), keys.shift(), keys.sprint());
            // Forward is already held; the sprint key starts the sprint again, as it would for you.
            case RESUME -> new Input(true, false, keys.left(), keys.right(), keys.jump(), keys.shift(), true);
        };
    }

    /**
     * Whether TriggerBot should hold a ready hit for a moment: a reset is under way, or starts on the
     * next tick, so waiting turns it into a sprint hit. Never for more than a few ticks.
     */
    public boolean holdsHit(Minecraft mc, LocalPlayer player) {
        boolean pending = isEnabled() && (planner.busy() || needsReset(player) && wouldResetNow(mc, player));
        if (!pending) {
            holdStartTick = -1;
            return false;
        }
        if (holdStartTick < 0) holdStartTick = player.tickCount;
        return player.tickCount - holdStartTick <= MAX_HOLD_TICKS;
    }

    private boolean wouldResetNow(Minecraft mc, LocalPlayer player) {
        Input keys = player.input.keyPresses;
        if (!keys.forward() || keys.backward()) return false;
        LivingEntity opponent = opponent(mc, player);
        return opponent != null && resetAllowed(player, opponent, keys);
    }

    /** The client sprints, but the server doesn't think so: the next hit would get no sprint knockback. */
    private static boolean needsReset(LocalPlayer player) {
        return player.isSprinting() && ((LocalPlayerAccessor) player).vanguard$wasSprinting()
            && !Vanguard.get().sprintTracker().serverSprinting();
    }

    /** Whether this tick is a good moment to reset. */
    private boolean resetAllowed(LocalPlayer player, LivingEntity opponent, Input keys) {
        if (player.isSpectator() || player.getAbilities().flying || player.isPassenger() || player.isFallFlying()
            || player.isInWater() || player.isSwimming()) {
            return false;
        }
        if (!canSprintAgain(player)) return false;
        // Letting go of forward on the tick you jump would lose the sprint jump's extra speed.
        if (player.onGround() && keys.jump()) return false;
        if (keepCrits.isOn() && !player.onGround() && critPossibleThisAirtime(player)) return false;
        return !skipRunners.isOn() || !runningAway(player, opponent);
    }

    /** Everything the game checks before it lets you start sprinting, apart from moving forward. */
    private static boolean canSprintAgain(LocalPlayer player) {
        boolean foodOk = player.getFoodData().hasEnoughFood() || player.getAbilities().mayfly;
        boolean itemSlows = player.isUsingItem()
            && !player.getUseItem().getOrDefault(DataComponents.USE_EFFECTS, UseEffects.DEFAULT).canSprint();
        return foodOk && !itemSlows && !player.isMobilityRestricted() && !player.isMovingSlowly()
            && !player.horizontalCollision;
    }

    /** Nothing rules out a crit for the rest of this airtime (the server's crit rules, bar sprinting). */
    private static boolean critPossibleThisAirtime(LocalPlayer player) {
        return !player.onClimbable() && !player.isInWater() && !player.isPassenger()
            && !player.hasEffect(MobEffects.BLINDNESS) && !player.hasEffect(MobEffects.LEVITATION);
    }

    /**
     * Who the fight is with: the last thing you hit, while it's alive and near, or else the closest
     * other player nearby. Null when there's no fight, and so no next hit to reset for.
     */
    private LivingEntity opponent(Minecraft mc, LocalPlayer player) {
        if (lastTarget != null && isOpponent(player, lastTarget)) return lastTarget;
        if (mc.level == null) return null;
        Player closest = null;
        double closestDistance = FIGHT_RANGE * FIGHT_RANGE;
        for (Player other : mc.level.players()) {
            if (!isOpponent(player, other)) continue;
            double distance = other.distanceToSqr(player);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = other;
            }
        }
        return closest;
    }

    private static boolean isOpponent(LocalPlayer player, LivingEntity entity) {
        return entity != player && entity.isAlive() && !entity.isRemoved() && !entity.isSpectator()
            && entity.level() == player.level() && !player.isAlliedTo(entity)
            && entity.distanceToSqr(player) <= FIGHT_RANGE * FIGHT_RANGE;
    }

    /** They could hit you right now: their eyes are within reach of your hitbox. */
    private static boolean tooClose(LocalPlayer player, LivingEntity opponent) {
        Vec3 eyes = Crosshair.serverPosition(opponent).add(0, opponent.getEyeHeight(), 0);
        return player.getBoundingBox().distanceToSqr(eyes) < OPPONENT_REACH * OPPONENT_REACH;
    }

    /** Moving away from you while facing away: running, not fighting. */
    private boolean runningAway(LocalPlayer player, LivingEntity opponent) {
        if (opponent != trailEntity || trailSize < 2) return false;
        Vec3 away = opponent.position().subtract(player.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0e-6) return false;
        away = away.normalize();
        Vec3 facing = Vec3.directionFromRotation(0, opponent.getYHeadRot());
        boolean facingAway = facing.dot(away.scale(-1)) < FACING_AWAY_COS;
        Vec3 velocity = trail[trailSize - 1].subtract(trail[0]).scale(1.0 / (trailSize - 1));
        return facingAway && velocity.dot(away) > RUNNING_AWAY_SPEED;
    }

    /**
     * Remembers where the opponent is each tick. Their movement has to come from our own samples:
     * when your movement is worked out, the game may or may not have moved them yet this tick.
     */
    private void recordTrail(LivingEntity opponent) {
        if (opponent != trailEntity) {
            trailEntity = opponent;
            trailSize = 0;
        }
        if (opponent == null) return;
        if (trailSize == trail.length) {
            System.arraycopy(trail, 1, trail, 0, trail.length - 1);
            trailSize--;
        }
        trail[trailSize++] = opponent.position();
    }

    /** Stepping back won't walk you off a drop of more than one block, or into lava, fire and the like. */
    private static boolean safeBehind(LocalPlayer player) {
        Level level = player.level();
        Vec3 back = Vec3.directionFromRotation(0, player.getYRot()).scale(-1);
        for (double distance : new double[] {0.5, 1.0}) {
            double x = player.getX() + back.x * distance;
            double z = player.getZ() + back.z * distance;
            BlockPos body = BlockPos.containing(x, player.getY() + 0.5, z);
            BlockPos floor = BlockPos.containing(x, player.getY() - 0.5, z);
            BlockPos drop = floor.below();
            if (isHazard(level, body) || isHazard(level, floor)) return false;
            if (!isSolid(level, floor)) {
                if (!isSolid(level, drop) || isHazard(level, drop)) return false;
            }
        }
        return true;
    }

    private static boolean isSolid(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isHazard(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES)
            || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
            || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW);
    }

    /** Ticks until your weapon is charged enough for a sprint hit, as of this tick's movement. */
    private static int ticksUntilReady(LocalPlayer player) {
        float delay = player.getCurrentItemAttackStrengthDelay();
        return SprintTap.ticksUntilReady(player.getAttackStrengthScale(0f) * delay, delay);
    }
}
