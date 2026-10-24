package dev.riffedcheats.core;

/**
 * Thin scrollbar drawn beside a list (only when the list is longer than what fits). Drag the thumb, or click the
 * track to jump there. One instance per list; it remembers where it was drawn for the click / drag checks.
 */
public final class Scrollbar {
    public static final int W = 3;
    private int x, y, h, total, rows;
    private boolean shown, dragging;

    /** total = list size, rows = visible rows, pos = first visible index. */
    public void draw(Gfx g, int x, int y, int h, int total, int rows, int pos, int mx, int my) {
        this.x = x; this.y = y; this.h = h; this.total = total; this.rows = rows;
        shown = total > rows && h > 8;
        if (!shown) { dragging = false; return; }
        g.fill(x, y, x + W, y + h, Ui.DARK);
        int th = thumb(), ty = y + (h - th) * Math.max(0, Math.min(pos, total - rows)) / (total - rows);
        boolean hot = dragging || hit(mx, my);
        g.fill(x, ty, x + W, ty + th, hot ? Ui.TEXT : Ui.ACCENT);
    }

    private int thumb() { return Math.max(12, Math.min(h, h * rows / Math.max(1, total))); }
    private boolean hit(int mx, int my) { return shown && Ui.in(mx, my, x - 1, y, W + 3, h); }

    /** Mouse pressed: starts dragging when it is on the bar. */
    public boolean press(int mx, int my) {
        dragging = hit(mx, my);
        return dragging;
    }

    public boolean dragging() { return dragging; }
    public void release() { dragging = false; }

    /** List position for the mouse height while dragging (the thumb's middle follows the mouse). */
    public int at(int my) {
        int th = thumb();
        float f = (my - y - th / 2f) / Math.max(1f, h - th);
        return Math.max(0, Math.min(total - rows, Math.round(f * (total - rows))));
    }
}
