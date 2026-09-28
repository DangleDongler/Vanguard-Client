package dev.vanguard.module.modules.combat;

import dev.vanguard.Vanguard;
import dev.vanguard.mixin.MinecraftInvoker;
import dev.vanguard.module.Category;
import dev.vanguard.module.Module;
import dev.vanguard.setting.BoolSetting;
import dev.vanguard.util.Crosshair;
import dev.vanguard.util.ShieldTracker;
import dev.vanguard.util.Shields;
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
 * <p><b>One axe swing per raised shield.</b> The server handles hits in order, so once the axe hit is
 * sent, anything after it lands on a lowered shield, even before the client sees it lower. The
 * {@link ShieldTracker} remembers the break from the moment it's sent, and every other attack uses
 * your weapon, whoever triggers it (your clicks, TriggerBot, this module). If an attack comes while
 * you're still holding the axe we switched to, it switches back first. It only tries again if the
 * server says the break failed (a block sound) or never answers within a round trip, and it stops
 * altogether if the server ignores two in a row.
 *
 * <p>The axe is selected right before the attack is sent, the same order the game uses when you
 * press a hotbar key and click in the same tick, and your slot is restored on the next tick.
 */
public final class ShieldBreaker extends Module {
    public final BoolSetting automatic = bool("Automatic", "Breaks a raised shield by itself as soon as your crosshair is on it. Off: only when you attack.", true);
    public final BoolSetting swapBack = bool("Swap Back", "Switch back to what you were holding right after the hit.", true);

    private int swapBackSlot = -1;
    private int axeSlot = -1;
    private int swapBackAtTick;
    private LocalPlayer trackedPlayer;

    public ShieldBreaker() {
        super("ShieldBreaker", "Breaks raised shields with an axe from your hotbar, then switches back.", Category.COMBAT);
    }

    @Override
    protected void onDisable() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) restoreSlot(player);
        clearSwap();
    }

    /**
     * Called right before every attack, including your own clicks and other modules. If this attack
     * should break the target's shield and you aren't holding an axe, switches to one first. Any
     * other attack is made with your weapon, never with the axe we switched to.
     */
    public void beforeAttack(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || !isEnabled()) return;
        ShieldTracker shields = Vanguard.get().shieldTracker();
        Player target = mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player p ? p : null;

        if (target == null || !needsBreaking(player, target, shields, ShieldTracker.answerTicks(mc))) {
            if (swapBack.isOn()) restoreSlot(player);
            return;
        }

        Inventory inventory = player.getInventory();
        if (!Shields.disablesShields(player.getMainHandItem())) {
            int slot = findAxeSlot(inventory);
            if (slot < 0) return;
            if (swapBackSlot < 0) swapBackSlot = inventory.getSelectedSlot();
            axeSlot = slot;
            // The attack that follows sends the slot change first, then the hit, so the server sees the axe.
            inventory.setSelectedSlot(slot);
            swapBackAtTick = player.tickCount + 1;
        }
        shields.onBreakSent(target.getId());
    }

    /** Called every frame from the combat hook: breaks a raised shield under the crosshair. */
    public void onFrame(LocalPlayer player) {
        if (!isEnabled() || !automatic.isOn()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.screen != null || mc.gameMode == null || mc.getOverlay() != null) return;
        if (player.isSpectator() || player.isUsingItem() || player.isHandsBusy()) return;
        if (!Shields.disablesShields(player.getMainHandItem()) && findAxeSlot(player.getInventory()) < 0) return;

        ShieldTracker shields = Vanguard.get().shieldTracker();
        int answerTicks = ShieldTracker.answerTicks(mc);
        // An axe hit uses your normal reach, whatever you're holding now.
        EntityHitResult hit = Crosshair.pick(mc, player, player.entityInteractionRange(),
            living -> living instanceof Player target && needsBreaking(player, target, shields, answerTicks), true);
        if (hit == null) return;
        Crosshair.aimAt(mc, hit);
        ((MinecraftInvoker) mc).vanguard$startAttack();
    }

    /** Called at the end of every client tick: switches back once the hit has gone out. */
    public void onClientTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player != trackedPlayer) {
            trackedPlayer = player;
            clearSwap();
            return;
        }
        if (swapBackSlot >= 0 && player.tickCount >= swapBackAtTick) {
            if (swapBack.isOn()) restoreSlot(player);
            clearSwap();
        }
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
