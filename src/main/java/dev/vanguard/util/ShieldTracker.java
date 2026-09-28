package dev.vanguard.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.phys.Vec3;

/**
 * What we know about other players' shields, from what the server tells us rather than guesses.
 *
 * <p>When a hit disables a shield, the server plays the shield's break sound at the holder, and
 * that sound is only ever played for a disable. It arrives before the update that lowers the shield,
 * so it's the earliest confirmation there is. A hit that the shield stops plays the block sound
 * instead, which means the shield is still up.
 *
 * <p>So each player's shield is either:
 * <ul>
 *   <li><b>breaking</b>: we sent a hit that should disable it and are waiting for the answer. The
 *       server handles our hits in order, so anything we send after it lands on a lowered shield.</li>
 *   <li><b>down</b>: the server confirmed the disable, so it stays down for its cooldown (5 seconds
 *       for an axe), whoever broke it.</li>
 *   <li>otherwise, whatever the client shows.</li>
 * </ul>
 */
public final class ShieldTracker {
    /** Cooldown when we can't see the shield's own numbers: an axe's 5 seconds. */
    private static final int DEFAULT_DOWN_TICKS = Math.round(Weapon.AXE_DISABLES_BLOCKING_FOR_SECONDS * 20);
    /** How far from a player a shield sound can be and still be theirs. */
    private static final double SOUND_MATCH_DISTANCE = 1.5;
    /**
     * Breaks the server ignored in a row (no break sound, no block sound) before giving up until the
     * shield comes down. It happens when the hit can't land at all, e.g. the target is invulnerable.
     */
    private static final int MAX_UNANSWERED = 2;

    private final Int2ObjectMap<State> states = new Int2ObjectOpenHashMap<>();
    private int now;
    private Object level;

    private static final class State {
        int breakSentTick = Integer.MIN_VALUE;
        int downUntilTick = Integer.MIN_VALUE;
        int unanswered;
    }

    /** Forgets everything when you join another world (entity ids are per world). */
    public void onLevel(Object currentLevel) {
        if (currentLevel != level) {
            level = currentLevel;
            states.clear();
        }
    }

    /** Advances the clock. Call once per client tick. */
    public void tick() {
        now++;
        if (now % 200 == 0) states.int2ObjectEntrySet().removeIf(e -> isStale(e.getValue()));
    }

    /** Nothing left worth remembering: not down, and no break sent in the last 10 seconds. */
    private boolean isStale(State state) {
        boolean recentBreak = state.breakSentTick != Integer.MIN_VALUE && now - state.breakSentTick <= 200;
        return state.downUntilTick <= now && !recentBreak;
    }

    public int now() {
        return now;
    }

    /** We just sent a hit that should disable this player's shield. */
    public void onBreakSent(int entityId) {
        State state = state(entityId);
        // A previous break still marked as sent was never answered.
        if (state.breakSentTick != Integer.MIN_VALUE) state.unanswered++;
        state.breakSentTick = now;
    }

    /** This player's shield is lowered (whatever the reason), so the next raise starts fresh. */
    public void onNotBlocking(int entityId) {
        State state = states.get(entityId);
        if (state != null) state.unanswered = 0;
    }

    /** The server ignored our last breaks on this player: stop trying until they lower the shield. */
    public boolean isGivenUp(int entityId) {
        State state = states.get(entityId);
        return state != null && state.unanswered >= MAX_UNANSWERED;
    }

    /** The server confirmed this player's shield is disabled for {@code downForTicks}. */
    public void onBreakConfirmed(int entityId, int downForTicks) {
        State state = state(entityId);
        state.downUntilTick = now + downForTicks;
        state.breakSentTick = Integer.MIN_VALUE;
        state.unanswered = 0;
    }

    /**
     * A hit on this player was blocked, so their shield is up whatever we thought it was.
     *
     * <p>Not while our own break is on its way, though: an axe hit that a shield blocks always
     * disables it, so a block in that window is someone else's hit, landing just before ours.
     * Treating it as "our break failed" would swing the axe a second time.
     */
    public void onBlocked(int entityId, int answerTicks) {
        State state = states.get(entityId);
        if (state == null || isBreaking(entityId, answerTicks)) return;
        state.breakSentTick = Integer.MIN_VALUE;
        state.downUntilTick = Integer.MIN_VALUE;
        state.unanswered = 0;
    }

    /** The server has confirmed this player's shield is on cooldown. */
    public boolean isDown(int entityId) {
        State state = states.get(entityId);
        return state != null && now < state.downUntilTick;
    }

    /** A break is on its way: sent, and not yet confirmed or refused within {@code answerTicks}. */
    public boolean isBreaking(int entityId, int answerTicks) {
        State state = states.get(entityId);
        return state != null && state.breakSentTick != Integer.MIN_VALUE && now - state.breakSentTick <= answerTicks;
    }

    /** Down, or about to be: don't break it again, and hits sent now will land. */
    public boolean isDownOrBreaking(int entityId, int answerTicks) {
        return isDown(entityId) || isBreaking(entityId, answerTicks);
    }

    public void clear() {
        states.clear();
    }

    private State state(int entityId) {
        return states.computeIfAbsent(entityId, id -> new State());
    }

    // --- Glue to the game ---------------------------------------------------------------------

    /** Called for every positioned sound the server sends. */
    public void onSound(Minecraft mc, Holder<SoundEvent> sound, double x, double y, double z) {
        boolean broke = sound.value() == SoundEvents.SHIELD_BREAK.value();
        boolean blocked = sound.value() == SoundEvents.SHIELD_BLOCK.value();
        if (!broke && !blocked || mc.level == null) return;

        Player owner = nearestPlayer(mc, new Vec3(x, y, z));
        if (owner == null) return;
        if (blocked) {
            onBlocked(owner.getId(), Latency.answerTicks(mc));
        } else {
            // The cooldown started on the server a moment ago; count from then.
            onBreakConfirmed(owner.getId(), Math.max(0, downTicks(owner) - Latency.ticks(mc) / 2 - 1));
        }
    }

    private static Player nearestPlayer(Minecraft mc, Vec3 at) {
        Player best = null;
        double bestDistance = SOUND_MATCH_DISTANCE * SOUND_MATCH_DISTANCE;
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            double distance = Crosshair.serverPosition(player).distanceToSqr(at);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    /** How long this player's shield stays down after an axe hit, from its own numbers if visible. */
    private static int downTicks(Player player) {
        for (ItemStack stack : new ItemStack[] {player.getUseItem(), player.getOffhandItem(), player.getMainHandItem()}) {
            BlocksAttacks blocks = stack.get(DataComponents.BLOCKS_ATTACKS);
            if (blocks != null) {
                return Math.round(Weapon.AXE_DISABLES_BLOCKING_FOR_SECONDS * blocks.disableCooldownScale() * 20);
            }
        }
        return DEFAULT_DOWN_TICKS;
    }
}
