package dev.vanguard.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

/** A choice between the constants of an enum ("mode" setting). */
public final class EnumSetting<E extends Enum<E>> extends Setting<E> {
    private final E[] constants;

    public EnumSetting(String name, String description, E defaultValue) {
        super(name, description, defaultValue);
        this.constants = defaultValue.getDeclaringClass().getEnumConstants();
    }

    public E[] constants() {
        return constants;
    }

    public boolean is(E constant) {
        return value == constant;
    }

    public void cycle(int direction) {
        int next = Math.floorMod(value.ordinal() + direction, constants.length);
        set(constants[next]);
    }

    /** Human readable name: {@code SMART_SWITCH} becomes "Smart Switch", unless the enum overrides toString. */
    public static String displayName(Enum<?> constant) {
        String raw = constant.toString();
        if (!raw.equals(constant.name())) return raw;
        StringBuilder out = new StringBuilder(raw.length());
        for (String word : raw.split("_")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value.name());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (!json.isJsonPrimitive()) return;
        String saved = json.getAsString();
        for (E constant : constants) {
            if (constant.name().equals(saved)) {
                set(constant);
                return;
            }
        }
    }
}
