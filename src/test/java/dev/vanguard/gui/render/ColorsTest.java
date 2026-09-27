package dev.vanguard.gui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorsTest {
    @Test
    void hsvRoundTrip() {
        int[] samples = {0xFF7B61FF, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFF123456, 0xFFFFFFFF, 0xFF000000, 0xFF808080};
        for (int color : samples) {
            float[] hsv = Colors.toHsv(color);
            assertEquals(color, Colors.hsv(hsv[0], hsv[1], hsv[2], 255), Colors.hex(color, true));
        }
    }

    @Test
    void hueWraps() {
        assertEquals(Colors.hsv(0.25f, 1, 1, 255), Colors.hsv(1.25f, 1, 1, 255));
    }

    @Test
    void fadeScalesAlphaOnly() {
        assertEquals(0x407B61FF, Colors.fade(0x807B61FF, 0.5f));
        assertEquals(0x807B61FF, Colors.fade(0x807B61FF, 1f));
        assertEquals(0x007B61FF, Colors.fade(0x807B61FF, -1f));
    }

    @Test
    void lerpEndpoints() {
        assertEquals(0xFF000000, Colors.lerp(0xFF000000, 0xFFFFFFFF, 0f));
        assertEquals(0xFFFFFFFF, Colors.lerp(0xFF000000, 0xFFFFFFFF, 1f));
        assertEquals(0xFF808080, Colors.lerp(0xFF000000, 0xFFFFFFFF, 0.5f));
    }
}
