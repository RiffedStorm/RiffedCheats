package dev.riffedcheats;

/** One row in the menu: toggle, slider, button or plain label. */
public final class Setting {
    public enum Kind { TOGGLE, SLIDER, BUTTON, LABEL }

    public final String name;
    public final Kind kind;
    public boolean on;
    public float value;
    public final float min, max, step;
    public final Runnable action;

    private Setting(String name, Kind kind, boolean on, float value, float min, float max, float step, Runnable action) {
        this.name = name; this.kind = kind; this.on = on; this.value = value;
        this.min = min; this.max = max; this.step = step; this.action = action;
    }

    /** True only when the toggle is on AND the player currently has permission to use the menu. */
    public boolean active() { return on && Access.allowed; }
    public int intValue() { return Math.round(value); }

    public static Setting toggle(String n, boolean def) { return new Setting(n, Kind.TOGGLE, def, 0, 0, 0, 0, null); }
    public static Setting slider(String n, float min, float max, float step, float def) { return new Setting(n, Kind.SLIDER, false, def, min, max, step, null); }
    public static Setting button(String n, Runnable r) { return new Setting(n, Kind.BUTTON, false, 0, 0, 0, 0, r); }
    public static Setting label(String n) { return new Setting(n, Kind.LABEL, false, 0, 0, 0, 0, null); }
}
