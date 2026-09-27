package dev.vanguard.setting;

import com.google.gson.JsonElement;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A named, persisted value owned by a module. Subclasses define how the value is
 * sanitized and (de)serialized; the GUI picks a widget based on the concrete type.
 */
public abstract class Setting<T> {
    private final String name;
    private final String description;
    private final T defaultValue;
    protected T value;

    private BooleanSupplier visibility = () -> true;
    private Consumer<T> changeListener = v -> {};

    protected Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public T get() {
        return value;
    }

    public void set(T newValue) {
        T sanitized = sanitize(newValue);
        if (Objects.equals(sanitized, value)) return;
        value = sanitized;
        changeListener.accept(sanitized);
    }

    public void reset() {
        set(defaultValue);
    }

    protected T sanitize(T newValue) {
        return newValue;
    }

    public boolean isVisible() {
        return visibility.getAsBoolean();
    }

    /** Hides this setting in the GUI unless {@code condition} holds, e.g. a sub-option of a toggle. */
    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
        this.visibility = condition;
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S onChange(Consumer<T> listener) {
        this.changeListener = listener;
        return (S) this;
    }

    public abstract JsonElement toJson();

    /** Applies a previously saved value. Implementations must tolerate malformed input. */
    public abstract void fromJson(JsonElement json);
}
