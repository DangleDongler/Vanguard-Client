package dev.vanguard.module.modules.combat;

import dev.vanguard.mixin.MinecraftInvoker;
import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.util.Crosshair;
import dev.vanguard.util.Shields;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Breaks raised shields with an axe from your hotbar, then switches back.
 *
 * <p>How shields work in 1.21.11 (see {@link Shields}): a hit from an axe that the shield blocks
 * puts all of the holder's shields on a 5 second cooldown. It needs no charge and has no randomness.
 * Every hit resets your own charge anyway, so the fastest way to a full-strength hit on an
 * unshielded opponent is to break the shield the moment it goes up, then hit when your weapon is
 * charged again: 12 ticks later with a sword, well inside the 100 tick window.
 *
 * <p>The swap only happens when the server would really block you: their shield has been up for its
 * 5 tick delay, and you're within its 90 degree cover. Before that, or from behind, your normal hit
 * already lands, so it never wastes a hit on the axe.
 *
 * <p>The axe is selected right before the attack is sent, the same order the game uses when you
 * press a hotbar key and click in the same tick, and your slot is restored about a tick later.
 */
public final class ShieldBreaker extends Module {
    public final BoolSetting automatic = bool("Automatic", "Breaks a raised shield by itself as soon as your crosshair is on it. Off: only when you attack.", true);
    public final BoolSetting swapBack = bool("Swap Back", "Switch back to what you were holding right after the hit.", true);

    /**
     * After trying to break a shield, wait this long before trying the same player again. The
     * server's answer (their shield lowering) takes a round trip to arrive, and until then the
     * shield still looks raised.
     */
    private static final int RETRY_TICKS = 10;

    /** Last tick an axe hit was sent at each player, by entity id. */
    private final Int2IntOpenHashMap lastAttemptTick = new Int2IntOpenHashMap();

    private int swapBackSlot = -1;
    private int axeSlot = -1;
    private int swapBackAtTick;
    private LocalPlayer trackedPlayer;

    public ShieldBreaker() {
        super("ShieldBreaker", "Breaks raised shields with an axe from your hotbar, then switches back.", Category.COMBAT);
    }

    @Override
    protected void onDisable() {
        finishSwapBack(Minecraft.getInstance().player, true);
        lastAttemptTick.clear();
    }

    /**
     * Called right before every attack, including your own clicks and other modules. If the target's
     * shield would block it and you aren't holding an axe, switches to one first so this very attack
     * breaks the shield.
     */
    public void beforeAttack(Minecraft mc) {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || !(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof Player target)) return;
        if (!isBreakable(player, target)) return;

        lastAttemptTick.put(target.getId(), player.tickCount);
        Inventory inventory = player.getInventory();
        if (Shields.disablesShields(player.getMainHandItem())) return;

        int slot = findAxeSlot(inventory);
        if (slot < 0) return;
        if (swapBackSlot < 0) swapBackSlot = inventory.getSelectedSlot();
        axeSlot = slot;
        // The attack that follows sends the slot change first, then the hit, so the server sees the axe.
        inventory.setSelectedSlot(slot);
        swapBackAtTick = player.tickCount + 1;
    }

    /** Called every frame from the combat hook: breaks a raised shield under the crosshair. */
    public void onFrame(LocalPlayer player) {
        if (!isEnabled() || !automatic.isOn()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.screen != null || mc.gameMode == null || mc.getOverlay() != null) return;
        if (player.isSpectator() || player.isUsingItem() || player.isHandsBusy()) return;
        if (!Shields.disablesShields(player.getMainHandItem()) && findAxeSlot(player.getInventory()) < 0) return;

        // An axe hit uses your normal reach, whatever you're holding now.
        EntityHitResult hit = Crosshair.pick(mc, player, player.entityInteractionRange(),
            living -> living instanceof Player target && isBreakable(player, target) && !recentlyTried(player, target), true);
        if (hit == null) return;
        Crosshair.aimAt(mc, hit);
        ((MinecraftInvoker) mc).vanguard$startAttack();
    }

    /** Called at the end of every client tick: switches back once the hit has gone out. */
    public void onClientTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player != trackedPlayer) {
            trackedPlayer = player;
            swapBackSlot = -1;
            lastAttemptTick.clear();
            return;
        }
        if (swapBackSlot >= 0 && player.tickCount >= swapBackAtTick) finishSwapBack(player, swapBack.isOn());
    }

    private void finishSwapBack(LocalPlayer player, boolean restore) {
        // Only switch back if you haven't changed slots yourself in the meantime.
        if (restore && player != null && player.getInventory().getSelectedSlot() == axeSlot) {
            player.getInventory().setSelectedSlot(swapBackSlot);
        }
        swapBackSlot = -1;
        axeSlot = -1;
    }

    /** A raised shield the server would block you with, and that an axe can disable. */
    private static boolean isBreakable(LocalPlayer player, Player target) {
        if (target == player || !target.isAlive() || target.isSpectator() || player.isAlliedTo(target)) return false;
        return Shields.wouldBlock(target, player.position()) && Shields.canBeDisabled(target);
    }

    private boolean recentlyTried(LocalPlayer player, Player target) {
        int last = lastAttemptTick.getOrDefault(target.getId(), Integer.MIN_VALUE);
        return last != Integer.MIN_VALUE && player.tickCount - last < RETRY_TICKS;
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
