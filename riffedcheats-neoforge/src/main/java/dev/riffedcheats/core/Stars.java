package dev.riffedcheats.core;

import java.util.List;

/** The clickable star in a list row. Click: favorite on / off. Hold a gold star and drag: move that favorite up or down. */
public final class Stars {
    public static final int W = 11;
    private static final int GOLD = 0xFFFFC94A;
    // pixel spans (from, to) per line of the 7x7 star
    private static final int[][] SHAPE = { { 3, 3 }, { 3, 3 }, { 0, 6 }, { 1, 5 }, { 1, 5 }, { 0, 2, 4, 6 }, { 0, 0, 6, 6 } };

    private final String kind;
    private String grabbed;
    private int startY;
    private boolean moved;

    public Stars(String kind) { this.kind = kind; }

    /** The 11 x rowH hit area starts at sx; the row's top is ry. */
    public void draw(Gfx g, String id, int sx, int ry, int rowH, int mx, int my) {
        boolean on = Favorites.has(kind, id);
        int c = on ? GOLD : Ui.in(mx, my, sx, ry, W, rowH) ? Ui.TEXT : Ui.DIM & 0x90FFFFFF;
        int px = sx + 2, py = ry + (rowH - 7) / 2;
        for (int l = 0; l < SHAPE.length; l++)
            for (int k = 0; k + 1 < SHAPE[l].length; k += 2) g.fill(px + SHAPE[l][k], py + l, px + SHAPE[l][k + 1] + 1, py + l + 1, c);
    }

    /** @return true when the press was on this row's star */
    public boolean press(String id, int sx, int ry, int rowH, int mx, int my) {
        if (!Ui.in(mx, my, sx, ry, W, rowH)) return false;
        grabbed = id;
        startY = my;
        moved = false;
        return true;
    }

    public boolean active() { return grabbed != null; }

    /** ids = what the list shows now, top = y of its first visible row. */
    public boolean drag(int my, List<String> ids, int top, int rowH, int scroll) {
        if (grabbed == null) return false;
        if (Math.abs(my - startY) > 2) moved = true;
        if (moved && Favorites.has(kind, grabbed) && !ids.isEmpty()) {
            int i = Math.max(0, Math.min(ids.size() - 1, Math.floorDiv(my - top, rowH) + scroll));
            String other = ids.get(i);
            if (!other.equals(grabbed) && Favorites.has(kind, other)) Favorites.moveTo(kind, grabbed, other);
        }
        return true;
    }

    public void release() {
        if (grabbed == null) return;
        if (!moved) Favorites.toggle(kind, grabbed); else Favorites.save();
        grabbed = null;
    }
}
