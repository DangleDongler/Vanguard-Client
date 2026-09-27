package dev.vanguard.setting;

import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {
    private enum Swap { OFF, NORMAL, SMART_SILENT }

    @Test
    void numberSnapsToStepAndClamps() {
        NumberSetting range = new NumberSetting("Range", "", 4.5, 1, 6, 0.1, "m");
        range.set(4.4400001);
        assertEquals(4.4, range.get());
        range.set(0.2);
        assertEquals(1.0, range.get());
        range.set(99.0);
        assertEquals(6.0, range.get());
        range.set(Double.NaN);
        assertEquals(6.0, range.get(), "NaN must not replace the value");
    }

    @Test
    void numberSnapsRelativeToMin() {
        NumberSetting odd = new NumberSetting("Odd", "", 1, 1, 9, 2, "");
        odd.set(4.2);
        assertEquals(5.0, odd.get());
    }

    @Test
    void numberFormatsWithStepPrecision() {
        assertEquals("4.5m", new NumberSetting("A", "", 4.5, 1, 6, 0.1, "m").format());
        assertEquals("0.25x", new NumberSetting("B", "", 0.25, 0, 2, 0.05, "x").format());
        assertEquals("16", new NumberSetting("C", "", 16, 1, 20, 1, "").format());
    }

    @Test
    void numberProgressRoundTrips() {
        NumberSetting setting = new NumberSetting("P", "", 0, 0, 100, 1, "%");
        setting.setProgress(0.5);
        assertEquals(50.0, setting.get());
        assertEquals(0.5, setting.progress(), 1e-9);
        setting.setProgress(2);
        assertEquals(100.0, setting.get());
    }

    @Test
    void numberRejectsInvalidRange() {
        assertThrows(IllegalArgumentException.class, () -> new NumberSetting("X", "", 1, 5, 5, 1, ""));
        assertThrows(IllegalArgumentException.class, () -> new NumberSetting("X", "", 1, 0, 5, 0, ""));
    }

    @Test
    void enumCyclesBothWays() {
        EnumSetting<Swap> swap = new EnumSetting<>("Swap", "", Swap.OFF);
        swap.cycle(-1);
        assertEquals(Swap.SMART_SILENT, swap.get());
        swap.cycle(1);
        assertEquals(Swap.OFF, swap.get());
    }

    @Test
    void enumDisplayNameIsTitleCase() {
        assertEquals("Smart Silent", EnumSetting.displayName(Swap.SMART_SILENT));
        assertEquals("Off", EnumSetting.displayName(Swap.OFF));
    }

    @Test
    void enumIgnoresUnknownSavedValues() {
        EnumSetting<Swap> swap = new EnumSetting<>("Swap", "", Swap.NORMAL);
        swap.fromJson(new JsonPrimitive("REMOVED_MODE"));
        assertEquals(Swap.NORMAL, swap.get());
        swap.fromJson(new JsonPrimitive("OFF"));
        assertEquals(Swap.OFF, swap.get());
    }

    @Test
    void colorWithoutAlphaStaysOpaque() {
        ColorSetting accent = new ColorSetting("Accent", "", 0x807B61FF, false);
        assertEquals(0xFF7B61FF, accent.argb());
        accent.set(0x00123456);
        assertEquals(0xFF123456, accent.argb());
    }

    @Test
    void colorJsonRoundTrip() {
        ColorSetting color = new ColorSetting("Color", "", 0x807B61FF, true);
        ColorSetting copy = new ColorSetting("Color", "", 0, true);
        copy.fromJson(color.toJson());
        assertEquals(0x807B61FF, copy.argb());
        copy.fromJson(new JsonPrimitive("not a color"));
        assertEquals(0x807B61FF, copy.argb());
    }

    @Test
    void changeListenerFiresOnlyOnRealChanges() {
        List<Boolean> seen = new ArrayList<>();
        BoolSetting bool = new BoolSetting("B", "", false).onChange(seen::add);
        bool.set(false);
        bool.toggle();
        bool.set(true);
        assertEquals(List.of(true), seen);
    }

    @Test
    void visibilityFollowsCondition() {
        BoolSetting parent = new BoolSetting("Render", "", false);
        ColorSetting child = new ColorSetting("Color", "", 0xFFFFFFFF, true).visibleWhen(parent::isOn);
        assertFalse(child.isVisible());
        parent.toggle();
        assertTrue(child.isVisible());
    }
}
