package dev.riffedcheats;

/** Minimal single-line text input (caret always at the end). */
public final class TextField {
    public String text = "";
    public boolean focused = true;
    public int max = 256;

    public boolean typed(char c) {
        if (!focused || c < 32 || c == 127) return false;
        if (text.length() < max) text += c;
        return true;
    }

    /** GLFW key codes. mods: 2 = ctrl. */
    public boolean key(int key, int mods) {
        if (!focused) return false;
        if (key == 259) { // backspace
            if ((mods & 2) != 0) text = "";
            else if (!text.isEmpty()) text = text.substring(0, text.length() - 1);
            return true;
        }
        if (key == 86 && (mods & 2) != 0) { // ctrl+V
            String c = Compat.clipboard().replaceAll("[\\r\\n]+", " ");
            text = (text + c).substring(0, Math.min(max, text.length() + c.length()));
            return true;
        }
        return false;
    }

    public void draw(Gfx g, int x, int y, int w, int h, String placeholder) {
        Ui.rr(g, x, y, w, h, Ui.DARK);
        String s = text;
        while (s.length() > 1 && g.width(s) > w - 10) s = s.substring(1);
        if (text.isEmpty()) g.text(placeholder, x + 5, y + (h - 8) / 2, Ui.DIM);
        else g.text(s, x + 5, y + (h - 8) / 2, Ui.TEXT);
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = x + 5 + g.width(s);
            g.fill(cx, y + 3, cx + 1, y + h - 3, Ui.TEXT);
        }
    }
}
