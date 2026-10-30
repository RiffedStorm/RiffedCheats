package dev.riffedcheats.core;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Saved teleport locations per dimension (tabs on top), save-here with a name, last death, go / rename / delete, drag to sort. */
public final class TeleportPage implements Page {
    private static final int ROWH = 18, LIST = 80, BTN = 44;

    private final TextField name = new TextField();
    private String dim;                 // selected dimension tab
    private Teleports.Loc armed;        // delete needs a second click
    private int scroll;
    private final Scrollbar bar = new Scrollbar();
    private int x, y, w, h;
    private Teleports.Loc dragged;      // row being dragged to a new place
    private int grabOff, dragMy;        // where in the row it was grabbed, current mouse height
    private long lastAutoScroll;

    public TeleportPage() { name.max = 32; }

    @Override public boolean wantsText() { return true; }

    private int rows() { return Math.max(1, (h - LIST - 12) / ROWH); }
    private int goX() { return x + w - 3 * BTN - 8; }
    private int renX() { return x + w - 2 * BTN - 4; }
    private int delX() { return x + w - BTN; }

    @Override
    public void render(Gfx g, int mx, int my, int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        name.focused = true;
        String here = Compat.dimension();
        if (dim == null) dim = here;

        // dimension tabs
        int cx = x;
        for (String d : Teleports.dims()) {
            String n = Teleports.dimName(d) + (d.equals(here) ? " (here)" : "");
            int cw = g.width(n) + 14;
            if (cx + cw > x + w) break;
            Ui.rr(g, cx, y, cw, 16, d.equals(dim) ? Ui.SEL : Ui.in(mx, my, cx, y, cw, 16) ? Ui.HOVER : Ui.BTN);
            g.text(n, cx + 7, y + 4, Ui.TEXT);
            cx += cw + 4;
        }

        // name + save
        name.draw(g, x, y + 22, w - 104, 18, "Name (optional), Enter = save");
        Ui.button(g, "Save Here", x + w - 100, y + 22, 100, 18, Ui.in(mx, my, x + w - 100, y + 22, 100, 18), false);

        // last death
        Teleports.Loc d = Teleports.lastDeath();
        String dl = d == null ? "No death recorded yet" : "To Last Death: " + Teleports.dimName(d.dim()) + "  " + d.coords();
        Ui.button(g, dl, x, y + 44, w, 18, d != null && Ui.in(mx, my, x, y + 44, w, 18), false);

        // saved list
        List<Teleports.Loc> list = Teleports.list(dim);
        g.text("Saved in " + Teleports.dimName(dim) + " (" + list.size() + ")", x, y + 68, Ui.DIM);
        if (list.size() > 1) { String t = "Drag a row to sort"; g.text(t, x + w - g.width(t), y + 68, Ui.DIM); }
        int rows = rows();
        if (dragged != null) autoScroll(list.size(), rows);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows)));
        bar.draw(g, x + w + 1, y + LIST, rows * ROWH - 1, list.size(), rows, scroll, mx, my);
        if (list.isEmpty()) g.text("Nothing saved here yet.", x + 4, y + LIST + 5, Ui.DIM);
        List<Teleports.Loc> shown = list;
        if (dragged != null && list.contains(dragged)) {   // open a gap where the dragged row would land
            shown = new java.util.ArrayList<>(list);
            shown.remove(dragged);
            shown.add(target(list.size()), dragged);
        }
        for (int i = 0; i < rows && scroll + i < shown.size(); i++) {
            Teleports.Loc l = shown.get(scroll + i);
            int ry = y + LIST + i * ROWH;
            if (l == dragged) Ui.rr(g, x, ry, w, ROWH - 1, Ui.DARK);
            else drawRow(g, l, ry, mx, my, false);
        }
        if (dragged != null) {   // the row itself follows the mouse, kept inside the list
            int ry = Math.max(y + LIST, Math.min(y + LIST + (rows - 1) * ROWH, dragMy - grabOff));
            drawRow(g, dragged, ry, -1, -1, true);
        }

        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null)
            g.text("You: " + Teleports.dimName(here) + "  " + (int) Math.floor(p.getX()) + ", " + (int) Math.floor(p.getY()) + ", "
                    + (int) Math.floor(p.getZ()) + "     Rename uses the typed name.", x, y + h - 9, Ui.DIM);
    }

    private void drawRow(Gfx g, Teleports.Loc l, int ry, int mx, int my, boolean lifted) {
        Ui.rr(g, x, ry, w, ROWH - 1, lifted ? Ui.SEL : Ui.in(mx, my, x, ry, w, ROWH - 1) ? Ui.HOVER : Ui.BTN);
        for (int k = 0; k < 3; k++) g.fill(x + 5, ry + 5 + k * 3, x + 11, ry + 6 + k * 3, Ui.DIM);   // grip
        String c = l.coords();
        int cxr = goX() - 8 - g.width(c);
        Ui.label(g, l.name(), x + 16, ry + 5, cxr - x - 24, Ui.TEXT);
        g.text(c, cxr, ry + 5, Ui.DIM);
        Ui.button(g, "Go", goX(), ry, BTN, ROWH - 1, Ui.in(mx, my, goX(), ry, BTN, ROWH - 1), false);
        Ui.button(g, "Rename", renX(), ry, BTN, ROWH - 1, Ui.in(mx, my, renX(), ry, BTN, ROWH - 1), false);
        Ui.button(g, l.equals(armed) ? "Sure?" : "Delete", delX(), ry, BTN, ROWH - 1, Ui.in(mx, my, delX(), ry, BTN, ROWH - 1), true);
    }

    /** Index in the list where the dragged row would be dropped. */
    private int target(int size) {
        int top = dragMy - grabOff + ROWH / 2 - (y + LIST);
        return Math.max(0, Math.min(size - 1, Math.floorDiv(top, ROWH) + scroll));
    }

    /** Holding a dragged row at the top / bottom edge scrolls the list. */
    private void autoScroll(int size, int rows) {
        long now = System.currentTimeMillis();
        if (now - lastAutoScroll < 120) return;
        if (dragMy < y + LIST + 6 && scroll > 0) { scroll--; lastAutoScroll = now; }
        else if (dragMy > y + LIST + rows * ROWH - 6 && scroll < size - rows) { scroll++; lastAutoScroll = now; }
    }

    private void saveHere() {
        Teleports.addHere(name.text);
        name.text = "";
        dim = Compat.dimension();
    }

    @Override
    public boolean click(int mx, int my, int btn) {
        if (bar.press(mx, my)) { scroll = bar.at(my); return true; }
        Teleports.Loc wasArmed = armed;
        armed = null;
        String here = Compat.dimension();
        int cx = x;
        for (String d : Teleports.dims()) {
            int cw = Gfx.textWidth(Teleports.dimName(d) + (d.equals(here) ? " (here)" : "")) + 14;
            if (cx + cw > x + w) break;
            if (Ui.in(mx, my, cx, y, cw, 16)) { dim = d; scroll = 0; return true; }
            cx += cw + 4;
        }
        if (Ui.in(mx, my, x + w - 100, y + 22, 100, 18)) { saveHere(); return true; }
        if (Ui.in(mx, my, x, y + 44, w, 18)) { Teleports.tpLastDeath(); return true; }

        List<Teleports.Loc> list = Teleports.list(dim);
        int rows = rows();
        for (int i = 0; i < rows && scroll + i < list.size(); i++) {
            Teleports.Loc l = list.get(scroll + i);
            int ry = y + LIST + i * ROWH;
            if (Ui.in(mx, my, goX(), ry, BTN, ROWH - 1)) { Teleports.tp(l); return true; }
            if (Ui.in(mx, my, renX(), ry, BTN, ROWH - 1)) { Teleports.rename(l, name.text); name.text = ""; return true; }
            if (Ui.in(mx, my, delX(), ry, BTN, ROWH - 1)) {
                if (l.equals(wasArmed)) Teleports.remove(l); else armed = l;
                return true;
            }
            if (Ui.in(mx, my, x, ry, goX() - x, ROWH - 1) && list.size() > 1) {   // grab the row (anywhere left of the buttons)
                dragged = l;
                grabOff = my - ry;
                dragMy = my;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean scroll(int mx, int my, double dy) { scroll -= (int) Math.signum(dy); return true; }

    @Override
    public boolean drag(int mx, int my) {
        if (dragged != null) { dragMy = my; return true; }
        if (!bar.dragging()) return false;
        scroll = bar.at(my);
        return true;
    }

    @Override
    public void release() {
        bar.release();
        if (dragged == null) return;
        List<Teleports.Loc> list = Teleports.list(dim);
        if (list.contains(dragged)) Teleports.move(dragged, target(list.size()));
        dragged = null;
    }

    @Override
    public boolean key(int key, int scan, int mods) {
        if (key == 257 || key == 335) { saveHere(); return true; } // Enter / keypad Enter
        return name.key(key, mods);
    }

    @Override public boolean typed(char c) { return name.typed(c); }
}
