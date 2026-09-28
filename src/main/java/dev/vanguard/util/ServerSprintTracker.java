package dev.vanguard.util;

/**
 * Tracks whether the <em>server</em> thinks the player is sprinting, which decides whether a hit
 * can crit. It isn't always what the client shows.
 *
 * <p>The client tells the server when sprinting starts and stops. But when a sprint hit lands, the
 * server stops the player's sprint on its own and doesn't tell the client. If the sprint key is still
 * held, the client keeps sprinting and never sends another update, so the server keeps treating the
 * player as not sprinting. This is the "sprint-crit state": you run at sprint speed, yet every
 * falling hit crits. It lasts until the client's sprint actually toggles again.
 */
public final class ServerSprintTracker {
    private boolean serverSprinting;
    private boolean lastSent;
    private boolean known;

    /**
     * Feeds the sprint state the client last sent to the server. A change means a new
     * start/stop update went out, which the server now agrees with.
     */
    public void observeSent(boolean sentSprinting) {
        if (!known || sentSprinting != lastSent) {
            serverSprinting = sentSprinting;
            lastSent = sentSprinting;
            known = true;
        }
    }

    /**
     * Called for every attack sent. A hit charged over 90% while the server thinks you're sprinting
     * is a sprint (knockback) hit, and landing it makes the server stop your sprint.
     */
    public void onAttack(float attackCharge) {
        if (serverSprinting && attackCharge > 0.9f) serverSprinting = false;
    }

    public boolean serverSprinting() {
        return serverSprinting;
    }

    /** Forget everything, e.g. after respawning or changing worlds. */
    public void reset() {
        known = false;
        serverSprinting = false;
    }
}
