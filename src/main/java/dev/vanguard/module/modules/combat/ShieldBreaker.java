package dev.vanguard.module.modules.combat;

import dev.vanguard.Vanguard;
import dev.vanguard.mixin.MinecraftInvoker;
import dev.vanguard.mixin.MultiPlayerGameModeAccessor;
import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.setting.NumberSetting;
import dev.vanguard.util.Crosshair;
import dev.vanguard.util.Latency;
import dev.vanguard.util.ShieldTracker;
import dev.vanguard.util.Shields;
import dev.vanguard.util.SwapSchedule;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Breaks raised shields with an axe from your hotbar, then switches back.
 *
 * <p>How shields work in 1.21.11 (see {@link Shields}): a hit from an axe that the shield blocks
 * puts all of the holder's shields on a 5 second cooldown. It needs no charge and has no randomness.
 * The swap only happens when the server would really block you: their shield has been up for its
 * 5 tick delay, and you're within its 90 degree cover. Before that, or from behind, your normal hit
 * already lands, so it never wastes a hit on the axe.
 *
 * <p><b>Pacing:</b> with Automatic, it waits the Reaction Time after a shield can block, and the
 * Attack Delay after your last hit, before it starts. Then it switches to the axe, holds it for the
 * Swap Delay, hits, and switches back after the Swap Back Delay. When you click a raised shield
 * yourself, your click becomes the switch, and the hit follows after the swap delay.
 *
 * <p><b>One axe swing per raised shield.</b> The server handles hits in order, so once the axe hit is
 * sent, anything after it lands on a lowered shield, even before the client sees it lower. The
 * {@link ShieldTracker} remembers the break from the moment it's sent, and every other attack uses
 * your weapon, whoever triggers it (your clicks, TriggerBot, this module). If an attack comes while
 * you're still holding the axe we switched to, it switches back first. It only tries again if the
 * server says the break failed (a block sound) or never answers within a round trip, and it stops
 * altogether if the server ignores two in a row.
 *
 * <p>Each switch goes out the way the game sends a hotbar key press, at the start of the next tick.
 * The swap delay counts from when the switch was sent, so the server always has the axe in your
 * hand for at least a tick before the hit, which is sent with its arm swing like any click.
 */
public final class ShieldBreaker extends Module {
    public final BoolSetting automatic = bool("Automatic", "Breaks a raised shield by itself when your crosshair is on it. Off: only when you attack.", true);
    public final NumberSetting reactionTime = number("Reaction Time", "How long it waits after a shield comes up before breaking it. Counts from when the shield can block (a quarter second after it's raised).", 100, 0, 1000, 10, "ms")
        .visibleWhen(automatic::isOn);
    public final NumberSetting attackDelay = number("Attack Delay", "Waits at least this long after your last hit (yours or TriggerBot's) before breaking, so it doesn't swing again right away.", 250, 0, 1000, 10, "ms")
        .visibleWhen(automatic::isOn);
    public final NumberSetting swapDelay = number("Swap Delay", "Ticks between switching to the axe and hitting with it. 1 tick = 50 ms. The switch always goes out at least a tick before the hit.", 1, 1, 10, 1, "t");
    public final BoolSetting swapBack = bool("Swap Back", "Switch back to what you were holding after the hit.", true);
    public final NumberSetting swapBackDelay = number("Swap Back Delay", "Ticks between the hit and switching back. 1 tick = 50 ms. At least 2, so the server always counts the switch (the normal game can't switch back any sooner).", 3, 2, 10, 1, "t")
        .visibleWhen(swapBack::isOn);

    /** How far away a raised shield is timed for Reaction Time. */
    private static final double TRACK_RANGE = 8.0;

    private final SwapSchedule schedule = new SwapSchedule();
    private int swapBackSlot = -1;
    private int axeSlot = -1;
    private LocalPlayer trackedPlayer;
    private long lastAttackNanos;
    /** When each nearby player's shield became able to block, by entity id. */
    private final Int2LongOpenHashMap blockingSince = new Int2LongOpenHashMap();

    public ShieldBreaker() {
        super("ShieldBreaker", "Breaks raised shields with an axe from your hotbar, then switches back.", Category.COMBAT);
    }

    @Override
    protected void onDisable() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) restoreSlot(player);
        clearSwap();
    }

    /** Called for every attack the client sends, including your own clicks and other modules. */
    public void onAttackSent() {
        lastAttackNanos = System.nanoTime();
    }

    /**
     * Called right before every attack, including your own clicks and other modules. Returns true to
     * cancel it: the attack was on a raised shield and the axe was only just picked, so the hit
     * comes after the swap delay instead, never in the same tick as the switch.
     *
     * <p>If this attack should break the target's shield and you aren't holding an axe, switches to
     * one first. Any other attack is made with your weapon, never with the axe we switched to.
     */
    public boolean beforeAttack(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || !isEnabled()) return false;
        ShieldTracker shields = Vanguard.get().shieldTracker();
        Player target = mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player p ? p : null;
        int answerTicks = Latency.answerTicks(mc);
        int tick = player.tickCount;
        boolean breakNeeded = target != null && needsBreaking(player, target, shields, answerTicks);

        switch (schedule.phase()) {
            case SWAPPED -> {
                if (breakNeeded && target.getId() == schedule.targetId()) {
                    // The hit we switched for: wait out the swap delay, then let it through.
                    if (!schedule.hitDue(tick, swapDelay.intValue())) return true;
                    shields.onBreakSent(target.getId());
                    afterHit(tick);
                    return false;
                }
                // Anything else is made with your weapon.
                restoreSlot(player);
            }
            case HIT -> restoreSlot(player);
            case IDLE -> {
            }
        }

        if (!breakNeeded) return false;
        if (Shields.disablesShields(player.getMainHandItem())) {
            // Already holding an axe: this attack is the break.
            shields.onBreakSent(target.getId());
            return false;
        }
        Inventory inventory = player.getInventory();
        int slot = findAxeSlot(inventory);
        if (slot < 0) return false;
        swapBackSlot = inventory.getSelectedSlot();
        axeSlot = slot;
        inventory.setSelectedSlot(slot);
        schedule.swapped(target.getId(), tick);
        return true;
    }

    private void afterHit(int tick) {
        if (swapBack.isOn()) schedule.hit(tick);
        else clearSwap();
    }

    /** Called every frame from the combat hook: breaks a raised shield under the crosshair. */
    public void onFrame(LocalPlayer player) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.screen != null || mc.gameMode == null || mc.getOverlay() != null) return;
        if (player.isSpectator() || player.isUsingItem() || player.isHandsBusy()) return;

        ShieldTracker shields = Vanguard.get().shieldTracker();
        int answerTicks = Latency.answerTicks(mc);
        switch (schedule.phase()) {
            case SWAPPED -> {
                hitWhenDue(mc, player, shields, answerTicks);
                return;
            }
            case HIT -> {
                return;
            }
            case IDLE -> {
            }
        }

        if (!automatic.isOn()) return;
        if (!Shields.disablesShields(player.getMainHandItem()) && findAxeSlot(player.getInventory()) < 0) return;
        long now = System.nanoTime();
        if (millisSince(lastAttackNanos, now) < attackDelay.get()) return;
        long reactionNanos = (long) (reactionTime.get() * 1_000_000L);
        // An axe hit uses your normal reach, whatever you're holding now.
        EntityHitResult hit = Crosshair.pick(mc, player, player.entityInteractionRange(),
            living -> living instanceof Player target && needsBreaking(player, target, shields, answerTicks)
                && reacted(target, now, reactionNanos), true);
        if (hit == null) return;
        Crosshair.aimAt(mc, hit);
        ((MinecraftInvoker) mc).vanguard$startAttack();
    }

    /** Switched to the axe: hit once the swap delay has passed, or give up if there's nothing left to break. */
    private void hitWhenDue(Minecraft mc, LocalPlayer player, ShieldTracker shields, int answerTicks) {
        if (!schedule.hitDue(player.tickCount, swapDelay.intValue())) return;
        if (player.getInventory().getSelectedSlot() != axeSlot) {
            // You switched slots yourself: leave it to you.
            clearSwap();
            return;
        }
        Entity entity = mc.level.getEntity(schedule.targetId());
        if (!(entity instanceof Player pending) || !needsBreaking(player, pending, shields, answerTicks)) {
            restoreSlot(player);
            return;
        }
        EntityHitResult hit = Crosshair.pick(mc, player, player.entityInteractionRange(), living -> living == pending, true);
        if (hit == null) return;
        Crosshair.aimAt(mc, hit);
        ((MinecraftInvoker) mc).vanguard$startAttack();
    }

    /** Called at the end of every client tick: times raised shields and switches back after a hit. */
    public void onClientTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player != trackedPlayer) {
            trackedPlayer = player;
            clearSwap();
            blockingSince.clear();
            return;
        }
        if (player == null) return;
        trackShields(mc, player);

        int tick = player.tickCount;
        if (mc.gameMode != null && ((MultiPlayerGameModeAccessor) mc.gameMode).vanguard$carriedIndex() == axeSlot) {
            schedule.sent(tick);
        }
        if (schedule.swapBackDue(tick, swapBackDelay.intValue())) restoreSlot(player);
        if (schedule.timedOut(tick, swapDelay.intValue())) restoreSlot(player);
    }

    /** Notes when each nearby player's shield became able to block, and forgets lowered ones. */
    private void trackShields(Minecraft mc, LocalPlayer player) {
        long now = System.nanoTime();
        IntSet blocking = new IntOpenHashSet();
        for (Player other : mc.level.players()) {
            if (other == player || !other.isBlocking() || other.distanceToSqr(player) > TRACK_RANGE * TRACK_RANGE) continue;
            blocking.add(other.getId());
            blockingSince.putIfAbsent(other.getId(), now);
        }
        blockingSince.keySet().removeIf(id -> !blocking.contains(id));
    }

    /** The shield has been able to block for at least the reaction time. */
    private boolean reacted(Player target, long now, long reactionNanos) {
        if (reactionNanos <= 0) return true;
        long since = blockingSince.getOrDefault(target.getId(), Long.MAX_VALUE);
        return since != Long.MAX_VALUE && now - since >= reactionNanos;
    }

    private static double millisSince(long thenNanos, long nowNanos) {
        return thenNanos == 0 ? Double.MAX_VALUE : (nowNanos - thenNanos) / 1.0e6;
    }

    /**
     * Whether this attack on {@code target} should be the one that breaks their shield: it's raised
     * and would block you, and no break is already on its way or confirmed.
     */
    private static boolean needsBreaking(LocalPlayer player, Player target, ShieldTracker shields, int answerTicks) {
        if (target == player || !target.isAlive() || target.isSpectator() || player.isAlliedTo(target)) return false;
        if (!target.isBlocking()) {
            shields.onNotBlocking(target.getId());
            return false;
        }
        if (!Shields.wouldBlock(target, player.position()) || !Shields.canBeDisabled(target)) return false;
        int id = target.getId();
        return !shields.isDownOrBreaking(id, answerTicks) && !shields.isGivenUp(id);
    }

    /** Back to the slot you had before we switched to the axe, unless you've changed it yourself. */
    private void restoreSlot(LocalPlayer player) {
        if (swapBackSlot >= 0 && player.getInventory().getSelectedSlot() == axeSlot) {
            player.getInventory().setSelectedSlot(swapBackSlot);
        }
        clearSwap();
    }

    private void clearSwap() {
        swapBackSlot = -1;
        axeSlot = -1;
        schedule.clear();
    }

    /**
     * The hotbar slot of the best shield-disabling item: the one with the most uses left, so a
     * nearly broken axe is only used when it's the last one. -1 if there's none.
     */
    private static int findAxeSlot(Inventory inventory) {
        int best = -1;
        int bestUses = -1;
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!Shields.disablesShields(stack)) continue;
            int uses = stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
            if (uses > bestUses) {
                bestUses = uses;
                best = slot;
            }
        }
        return best;
    }
}
