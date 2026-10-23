package dev.riffedcheats;

/** One row in the menu: toggle, slider, button, a row of small buttons, or plain label. */
public final class Setting {
    public enum Kind { TOGGLE, SLIDER, BUTTON, BUTTONS, LABEL }

    public final String name;
    public final Kind kind;
    public boolean on;
    public float value;
    public final float min, max, step;
    public final Runnable action;
    public final String[] opts;     // BUTTONS only
    public final Runnable[] acts;   // BUTTONS only

    private Setting(String name, Kind kind, boolean on, float value, float min, float max, float step, Runnable action,
                    String[] opts, Runnable[] acts) {
        this.name = name; this.kind = kind; this.on = on; this.value = value;
        this.min = min; this.max = max; this.step = step; this.action = action;
        this.opts = opts; this.acts = acts;
    }

    /** True only when the toggle is on AND the player currently has permission to use the menu. */
    public boolean active() { return on && Access.allowed; }
    public int intValue() { return Math.round(value); }

    public static Setting toggle(String n, boolean def) { return new Setting(n, Kind.TOGGLE, def, 0, 0, 0, 0, null, null, null); }
    public static Setting slider(String n, float min, float max, float step, float def) { return new Setting(n, Kind.SLIDER, false, def, min, max, step, null, null, null); }
    public static Setting button(String n, Runnable r) { return new Setting(n, Kind.BUTTON, false, 0, 0, 0, 0, r, null, null); }
    /** A label on the left (may be empty) and several buttons side by side, e.g. "Gamemode [Survival] [Creative] ...". */
    public static Setting buttons(String n, String[] opts, Runnable[] acts) { return new Setting(n, Kind.BUTTONS, false, 0, 0, 0, 0, null, opts, acts); }
    public static Setting label(String n) { return new Setting(n, Kind.LABEL, false, 0, 0, 0, 0, null, null, null); }
}
