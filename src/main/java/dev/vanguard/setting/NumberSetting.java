package dev.vanguard.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;

public final class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;
    private final int decimals;
    private final String suffix;

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step, String suffix) {
        super(name, description, defaultValue);
        if (min >= max) throw new IllegalArgumentException("min must be < max for " + name);
        if (step <= 0) throw new IllegalArgumentException("step must be > 0 for " + name);
        this.min = min;
        this.max = max;
        this.step = step;
        this.decimals = Math.max(0, BigDecimal.valueOf(step).stripTrailingZeros().scale());
        this.suffix = suffix;
        this.value = sanitize(defaultValue);
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    public int intValue() {
        return (int) Math.round(value);
    }

    public float floatValue() {
        return value.floatValue();
    }

    /** Position of the value within [min, max], 0..1. */
    public double progress() {
        return (value - min) / (max - min);
    }

    public void setProgress(double progress) {
        set(min + Math.clamp(progress, 0, 1) * (max - min));
    }

    public void increment(int direction) {
        set(value + direction * step);
    }

    public String format() {
        return (decimals == 0 ? Long.toString(Math.round(value)) : String.format("%." + decimals + "f", value)) + suffix;
    }

    @Override
    protected Double sanitize(Double newValue) {
        if (newValue == null || newValue.isNaN()) return value;
        double snapped = min + Math.round((newValue - min) / step) * step;
        // Round away floating point drift so saved values stay readable (0.30000000000000004 -> 0.3).
        snapped = BigDecimal.valueOf(snapped).setScale(decimals, java.math.RoundingMode.HALF_UP).doubleValue();
        return Math.clamp(snapped, min, max);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) set(json.getAsDouble());
    }
}
