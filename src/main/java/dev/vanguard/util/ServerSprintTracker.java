package dev.vanguard.util;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * Tracks whether the <em>server</em> thinks you're sprinting. That decides what your next hit does:
 * a charged hit while the server sees you sprinting is a sprint hit (extra knockback), and a hit can
 * only crit while it doesn't. It isn't always what your screen shows.
 *
 * <p>The client tells the server when sprinting starts and stops. But when a sprint hit lands, the
 * server stops your sprint on its own and doesn't tell the client. If you keep holding forward, the
 * client keeps sprinting and never sends another update, so the server keeps treating you as not
 * sprinting. This is the "sprint-crit state": you run at sprint speed, but your hits have no sprint
 * knockback, and every falling hit crits. It lasts until the client's sprint actually stops and
 * starts again, which is what a sprint reset (W-tap, S-tap) does.
 *
 * <p>A sprint hit only stops the sprint if it lands. One that a shield blocks, or that hits during
 * the target's damage immunity, leaves it on. The server's sounds for your hit tell which: it plays
 * the knockback sound for every sprint hit, then the "no damage" sound if it didn't land. So the
 * tracker assumes a sprint hit lands, and corrects itself if the server says otherwise.
 */
public final class ServerSprintTracker {
    /** A hit over 90% charge is a sprint hit if the server thinks you're sprinting. */
    public static final float SPRINT_HIT_CHARGE = 0.9f;
    /** How close to where you attacked from the server's sounds for your hit play. */
    private static final double OWN_SOUND_DISTANCE = 1.5;

    private boolean serverSprinting;
    private boolean lastSent;
    private boolean known;
    private Object player;
    private int now;

    // The last charged hit we sent, until the server has answered it.
    private boolean awaitingAnswer;
    private int answerDeadline;
    private Vec3 attackPosition = Vec3.ZERO;
    private boolean heardKnockback;
    private boolean sentSinceAttack;

    /** Forgets everything when your player changes (respawn, new world). */
    public void onPlayer(Object currentPlayer) {
        if (currentPlayer != player) {
            player = currentPlayer;
            reset();
        }
    }

    /** Advances the clock. Call once per client tick. */
    public void tick() {
        now++;
        if (awaitingAnswer && now > answerDeadline) awaitingAnswer = false;
    }

    /**
     * Feeds the sprint state the client last sent to the server. A change means a new start/stop
     * update went out, which the server now agrees with.
     */
    public void observeSent(boolean sentSprinting) {
        if (!known || sentSprinting != lastSent) {
            if (known) sentSinceAttack = true;
            serverSprinting = sentSprinting;
            lastSent = sentSprinting;
            known = true;
        }
    }

    /**
     * Called for every attack sent. A charged hit while the server thinks you're sprinting is a sprint
     * hit, and landing it makes the server stop your sprint.
     *
     * @param position    where you are as you attack, to recognise the server's sounds for this hit
     * @param answerTicks how long to wait for them: a round trip plus a little
     */
    public void onAttack(float attackCharge, Vec3 position, int answerTicks) {
        if (attackCharge <= SPRINT_HIT_CHARGE) return;
        serverSprinting = false;
        awaitingAnswer = true;
        answerDeadline = now + answerTicks;
        attackPosition = position;
        heardKnockback = false;
        sentSinceAttack = false;
    }

    /** What the server's sounds for your own hit said. */
    public enum HitSound { KNOCKBACK, NO_DAMAGE, LANDED }

    /** The server played one of the attack sounds for a charged hit of ours. */
    public void onOwnHitSound(HitSound sound) {
        if (!awaitingAnswer) return;
        switch (sound) {
            case KNOCKBACK -> heardKnockback = true;
            case NO_DAMAGE -> {
                // A sprint hit that didn't land leaves the sprint on, unless we've changed it since.
                if (heardKnockback && !sentSinceAttack) serverSprinting = true;
                awaitingAnswer = false;
            }
            case LANDED -> awaitingAnswer = false;
        }
    }

    public boolean serverSprinting() {
        return serverSprinting;
    }

    /** Forget everything, e.g. after respawning or changing worlds. */
    public void reset() {
        known = false;
        serverSprinting = false;
        awaitingAnswer = false;
    }

    // --- Glue to the game ---------------------------------------------------------------------

    /** Called for every positioned sound the server sends. */
    public void onSound(Holder<SoundEvent> sound, double x, double y, double z) {
        if (!awaitingAnswer) return;
        HitSound kind = hitSound(sound.value());
        if (kind == null) return;
        if (attackPosition.distanceToSqr(x, y, z) > OWN_SOUND_DISTANCE * OWN_SOUND_DISTANCE) return;
        onOwnHitSound(kind);
    }

    private static HitSound hitSound(SoundEvent sound) {
        if (sound == SoundEvents.PLAYER_ATTACK_KNOCKBACK) return HitSound.KNOCKBACK;
        if (sound == SoundEvents.PLAYER_ATTACK_NODAMAGE) return HitSound.NO_DAMAGE;
        if (sound == SoundEvents.PLAYER_ATTACK_STRONG || sound == SoundEvents.PLAYER_ATTACK_WEAK
            || sound == SoundEvents.PLAYER_ATTACK_CRIT || sound == SoundEvents.PLAYER_ATTACK_SWEEP) {
            return HitSound.LANDED;
        }
        return null;
    }
}
