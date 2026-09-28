package dev.vanguard.module.modules.combat;

import dev.vanguard.Vanguard;
import dev.vanguard.mixin.LocalPlayerAccessor;
import dev.vanguard.mixin.MinecraftInvoker;
import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.EnumSetting;
import dev.vanguard.setting.NumberSetting;
import dev.vanguard.util.Crosshair;
import dev.vanguard.util.FallTiming;
import dev.vanguard.util.ServerSprintTracker;
import dev.vanguard.util.ShieldTracker;
import dev.vanguard.util.Shields;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Attacks the moment your crosshair is on a target and the hit is worth taking.
 *
 * <p><b>Responsive:</b> it checks every frame, right after the camera turns, rather than once per
 * game tick. The attack goes through the same code as a real click, so it's sent the instant the
 * crosshair lands on a target or your weapon finishes charging, often a whole tick sooner than a
 * click would register.
 *
 * <p><b>Full-strength hits only:</b> it waits for a full charge, since an early hit does less damage
 * and can't crit.
 *
 * <p><b>Crits</b> follow the server's exact rules: over 90% charge, falling, not sprinting, not in
 * water, climbing, riding or blind. With Priority, it holds a charged hit while you're rising
 * (from a jump, or from the knockback of being hit, for P-crits) and swings as the fall begins. It
 * gives up waiting if the target would leave your reach first. Sprint state comes from what the
 * <em>server</em> believes (see {@link ServerSprintTracker}), so crits still land in the
 * sprint-crit state after a sprint hit.
 *
 * <p><b>Spacing:</b> with Spacing at 100% it swings on the first frame the target is in reach
 * (outspacing). Lower values wait until they're closer.
 *
 * <p><b>Server Position:</b> your game draws other players and mobs a little behind where the server
 * says they are: each position update is eased in over three ticks so movement looks smooth. The
 * exact latest position from the server is kept too, and this checks the crosshair and reach
 * against both. The server judges reach from its own position, so hits on an approaching target
 * land a tick or two sooner.
 */
public final class TriggerBot extends Module {
    public enum Crits {
        OFF("Off"), PRIORITY("Priority"), ONLY("Crits Only");

        private final String label;

        Crits(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public final EnumSetting<Crits> crits = mode("Crits", "Priority: in the air, waits for the fall so the hit crits (jump crits and P-crits); on the ground, hits right away. Crits Only: only hits when it will crit.", Crits.PRIORITY);
    public final NumberSetting spacing = number("Spacing", "How far into your reach a target must be before you hit. 100% hits the moment they step into reach (outspacing).", 100, 50, 100, 1, "%");
    public final BoolSetting serverPosition = bool("Server Position", "Also aims at where the server says the target is right now. Your game draws them a little behind that, so first hits land sooner.", true);
    public final BoolSetting hitSelect = bool("Hit Select", "In ground trades, waits for the opponent to swing first (hit or miss), then hits back instantly. Hits anyway after a short wait.", false);
    public final BoolSetting weaponsOnly = bool("Weapons Only", "Only attack while holding a sword, axe, mace, spear or trident.", true);
    public final BoolSetting skipShields = bool("Skip Shields", "Don't waste a hit on a player blocking with a shield. Axes still hit, to disable it, and ShieldBreaker swaps to one for you.", true);
    public final BoolSetting players = bool("Players", "Attack players.", true);
    public final BoolSetting mobs = bool("Mobs", "Attack mobs and animals.", false);

    /**
     * Full charge, allowing for float rounding: weapon attack speeds are stored as floats, so a sword
     * reads 0.99999994 on the tick it's really full. Waiting for exactly 1.0 would cost a whole tick
     * on every swing (13 ticks between hits instead of 12).
     */
    private static final float FULL_CHARGE = 0.9999f;
    /** Longest a charged hit is held for a crit, and the most the rise may still take (ticks). */
    private static final int MAX_CRIT_WAIT_TICKS = 12;
    /** Hit select: counter within this long of being hit, and never hold longer than the max. */
    private static final long HIT_SELECT_WINDOW_NANOS = 200_000_000L;
    private static final long HIT_SELECT_MAX_HOLD_NANOS = 400_000_000L;
    /** How close an opponent must be to hit you back, for hit select to be worth it. */
    private static final double OPPONENT_REACH = 3.2;

    private final ServerSprintTracker serverSprint = new ServerSprintTracker();
    private LocalPlayer trackedPlayer;

    private int critWaitStartTick = -1;
    private long holdStartNanos;
    private int lastHurtTime;
    private long lastHurtNanos;

    /** When each entity last swung, by entity id, from the server's swing packets. */
    private final Int2LongOpenHashMap swingNanos = new Int2LongOpenHashMap();

    public TriggerBot() {
        super("TriggerBot", "Attacks when your crosshair is on a target, timed for full damage and crits.", Category.COMBAT);
    }

    @Override
    protected void onDisable() {
        resetWaits();
    }

    private void resetWaits() {
        critWaitStartTick = -1;
        holdStartNanos = 0;
    }

    /** Called for every attack the client sends, including your own clicks. */
    public void onAttackSent(Player player) {
        serverSprint.onAttack(player.getAttackStrengthScale(0.5f));
    }

    /** Called every frame from the camera-turn hook, after aim assist has moved the camera. */
    public void onFrame(LocalPlayer player) {
        long now = System.nanoTime();
        track(player, now);
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.screen != null || mc.gameMode == null || mc.getOverlay() != null) return;
        if (player.isSpectator() || player.isUsingItem() || player.isHandsBusy()) return;

        ItemStack held = player.getMainHandItem();
        if (weaponsOnly.isOn() && !isWeapon(held)) {
            resetWaits();
            return;
        }
        // Only ever swing at full charge: anything less does less damage and can't crit.
        if (player.getAttackStrengthScale(0.5f) < FULL_CHARGE || player.cannotAttackWithItem(held, 0)) return;

        // Re-check the crosshair now: the mouse and aim assist may have moved it this frame.
        double reach = reach(player, held);
        EntityHitResult hit = Crosshair.pick(mc, player, reach, living -> isValidTarget(player, living), serverPosition.isOn());
        if (hit == null) {
            resetWaits();
            return;
        }
        Crosshair.aimAt(mc, hit);
        LivingEntity target = (LivingEntity) hit.getEntity();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 eye = player.getEyePosition(partialTick);

        double hitRange = reach * spacing.get() / 100.0;
        if (eye.distanceTo(hit.getLocation()) > hitRange + 1.0e-4) {
            resetWaits();
            return;
        }
        AttackRange weaponRange = held.get(DataComponents.ATTACK_RANGE);
        if (weaponRange != null && !weaponRange.isInRange(player, hit.getLocation())) return;

        if (skipShields.isOn() && shieldBlocks(mc, player, target, held)) return;
        // Fast items can re-hit before the target's damage immunity wears off; wait it out.
        if (player.getCurrentItemAttackStrengthDelay() < 10f && target.hurtTime > 0) return;

        boolean critNow = canCritNow(player);
        switch (crits.get()) {
            case ONLY -> {
                if (!critNow) return;
            }
            case PRIORITY -> {
                if (!critNow && shouldWaitForCrit(mc, player, target, eye, hitRange)) return;
            }
            case OFF -> {
            }
        }
        if (hitSelect.isOn() && !critNow && shouldHoldForHitSelect(player, target, partialTick, now)) return;

        resetWaits();
        ((MinecraftInvoker) mc).vanguard$startAttack();
    }

    /** Keeps the sprint and hurt state current, even while the module is off. */
    private void track(LocalPlayer player, long now) {
        if (player != trackedPlayer) {
            trackedPlayer = player;
            serverSprint.reset();
            lastHurtTime = 0;
            swingNanos.clear();
        }
        serverSprint.observeSent(((LocalPlayerAccessor) player).vanguard$wasSprinting());
        if (player.hurtTime > lastHurtTime) lastHurtNanos = now;
        lastHurtTime = player.hurtTime;
    }

    /**
     * Called for each swing packet from the server. Players send one whenever they attack, hit or
     * miss, and either way their charge is now spent.
     */
    public void onSwingPacket(int entityId) {
        if (swingNanos.size() > 256) swingNanos.clear();
        swingNanos.put(entityId, System.nanoTime());
    }

    /** The server's crit check, as it will see you when this attack arrives. */
    private boolean canCritNow(LocalPlayer player) {
        return player.fallDistance > 0 && !player.onGround() && canCritThisAirtime(player);
    }

    /** Whether anything rules out a crit for the rest of this jump. */
    private boolean canCritThisAirtime(LocalPlayer player) {
        return !player.onClimbable()
            && !player.isInWater()
            && !player.isPassenger()
            && !player.hasEffect(MobEffects.BLINDNESS)
            && !player.hasEffect(MobEffects.LEVITATION)
            && !player.getAbilities().flying
            && !serverSprint.serverSprinting();
    }

    /**
     * Priority crits: hold a ready hit while a crit is coming up this airtime (a jump, or the upward
     * knockback of being hit), unless that would cost the hit altogether.
     */
    private boolean shouldWaitForCrit(Minecraft mc, LocalPlayer player, LivingEntity target, Vec3 eye, double hitRange) {
        if (!canCritThisAirtime(player)) return false;

        int ticks;
        if (player.onGround()) {
            // Standing still: just hit. About to jump: the crit is only a jump away.
            if (!mc.options.keyJump.isDown()) return false;
            ticks = 1 + FallTiming.ticksUntilFalling(FallTiming.JUMP_VELOCITY, player.getGravity());
        } else {
            ticks = FallTiming.ticksUntilFalling(player.getDeltaMovement().y, player.getGravity());
        }
        if (ticks > MAX_CRIT_WAIT_TICKS) return false;

        if (critWaitStartTick < 0) critWaitStartTick = player.tickCount;
        if (player.tickCount - critWaitStartTick > MAX_CRIT_WAIT_TICKS) return false;

        // Don't wait if they'll be out of reach by the time the fall starts.
        return predictedDistance(player, target, eye, ticks) <= hitRange;
    }

    /** Distance from your eyes to the target's hitbox after both keep moving for {@code ticks}. */
    private static double predictedDistance(LocalPlayer player, LivingEntity target, Vec3 eye, int ticks) {
        Vec3 targetStep = target.position().subtract(target.xo, target.yo, target.zo);
        Vec3 ownStep = player.position().subtract(player.xo, player.yo, player.zo);
        Vec3 futureEye = eye.add(ownStep.x * ticks, 0, ownStep.z * ticks);
        AABB futureBox = target.getBoundingBox().move(targetStep.x * ticks, 0, targetStep.z * ticks);
        return Math.sqrt(futureBox.distanceToSqr(futureEye));
    }

    /**
     * Hit select: in a ground trade, let the opponent swing first, then counter right away. Their
     * swing spends their charge whether it lands or not. And a sprint hit of yours cuts the knockback
     * speed the server still holds for you by 40%, so the next knockback you take is smaller. Never
     * holds longer than a moment.
     */
    private boolean shouldHoldForHitSelect(LocalPlayer player, LivingEntity target, float partialTick, long now) {
        if (!(target instanceof Player) || !player.onGround()) return false;
        // Only worth it when they're close enough to hit you back.
        AABB ownBox = player.getBoundingBox();
        if (ownBox.distanceToSqr(target.getEyePosition(partialTick)) > OPPONENT_REACH * OPPONENT_REACH) return false;
        // They just hit you, or swung and missed: their attack is spent, so counter now.
        if (now - lastHurtNanos < HIT_SELECT_WINDOW_NANOS) return false;
        if (now - swingNanos.getOrDefault(target.getId(), 0L) < HIT_SELECT_WINDOW_NANOS) return false;
        if (holdStartNanos == 0) holdStartNanos = now;
        return now - holdStartNanos < HIT_SELECT_MAX_HOLD_NANOS;
    }

    private boolean isValidTarget(LocalPlayer player, LivingEntity target) {
        if (target == player || !target.isAlive() || target.isSpectator() || target instanceof ArmorStand) return false;
        if (target instanceof Player ? !players.isOn() : !mobs.isOn()) return false;
        return !player.isAlliedTo(target);
    }

    /** Your reach with this item, as the game checks it. */
    private static double reach(LocalPlayer player, ItemStack held) {
        AttackRange weaponRange = held.get(DataComponents.ATTACK_RANGE);
        return weaponRange != null ? weaponRange.effectiveMaxRange(player) + weaponRange.hitboxMargin() : player.entityInteractionRange();
    }

    /**
     * Whether the target's shield would stop this hit. A shield that's being broken (the axe hit is on
     * its way; the server handles it before this hit) or confirmed broken, by anyone, counts as down.
     */
    private static boolean shieldBlocks(Minecraft mc, LocalPlayer player, LivingEntity target, ItemStack held) {
        if (!Shields.wouldBlock(target, player.position()) || Shields.disablesShields(held)) return false;
        return !Vanguard.get().shieldTracker().isDownOrBreaking(target.getId(), ShieldTracker.answerTicks(mc));
    }

    private static boolean isWeapon(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS)
            || stack.is(Items.MACE) || stack.is(Items.TRIDENT);
    }
}
