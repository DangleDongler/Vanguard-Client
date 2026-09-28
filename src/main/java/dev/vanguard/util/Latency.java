package dev.vanguard.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

/** Your connection's delay, for modules that wait on the server's answer. */
public final class Latency {
    private Latency() {
    }

    /** Your ping, in ticks. */
    public static int ticks(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) return 0;
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : (info.getLatency() + 49) / 50;
    }

    /** How long to wait for the server's answer to something we sent: a round trip plus a little. */
    public static int answerTicks(Minecraft mc) {
        return Math.max(6, 2 * ticks(mc) + 4);
    }
}
