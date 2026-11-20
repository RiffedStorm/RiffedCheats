package dev.riffedcheats.core;

/** Shapes for the Modern layout. Curves are drawn at half-unit steps so corners stay smooth on a scaled menu. */
final class Draw {
    private static int inset2(int r2, int j) {      // half units: inset of half-row j (0 = top) in a corner of radius r2
        double d = r2 - j - 0.5;
        return (int) Math.round(r2 - Math.sqrt(Math.max(0, (double) r2 * r2 - d * d)));
    }

    /** Filled rounded rectangle (sent to the GPU as one batch). */
    static void round(Gfx g, int x, int y, int w, int h, int r, int c) {
        if (w <= 0 || h <= 0 || (c >>> 24) == 0) return;
        g.batch(() -> roundRaw(g, x, y, w, h, r, c));
    }

    private static void roundRaw(Gfx g, int x, int y, int w, int h, int r, int c) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { g.fill(x, y, x + w, y + h, c); return; }
        if (h > 2 * r) g.fill(x, y + r, x + w, y + h - r, c);
        int r2 = r * 2, X = x * 2, Y = y * 2, R = (x + w) * 2, B = (y + h) * 2;
        g.push();
        g.scale(0.5f);
        for (int j = 0; j < r2; j++) {
            int in = inset2(r2, j);
            g.fill(X + in, Y + j, R - in, Y + j + 1, c);
            g.fill(X + in, B - j - 1, R - in, B - j, c);
        }
        g.pop();
    }

    /** Half-unit thin outline of a rounded rectangle; all values in half units. */
    static void ring2(Gfx g, int X, int Y, int W, int H, int R2, int c) {
        if ((c >>> 24) == 0 || W <= 2 || H <= 2) return;
        g.batch(() -> ring2Raw(g, X, Y, W, H, R2, c));
    }

    private static void ring2Raw(Gfx g, int X, int Y, int W, int H, int R2, int c) {
        R2 = Math.max(1, Math.min(R2, Math.min(W, H) / 2));
        int Rt = X + W, Bt = Y + H;
        g.push();
        g.scale(0.5f);
        int top = inset2(R2, 0);
        g.fill(X + top, Y, Rt - top, Y + 1, c);
        g.fill(X + top, Bt - 1, Rt - top, Bt, c);
        for (int j = 1; j < R2; j++) {
            int in = inset2(R2, j), prev = Math.max(in + 1, inset2(R2, j - 1));
            g.fill(X + in, Y + j, X + prev, Y + j + 1, c);
            g.fill(Rt - prev, Y + j, Rt - in, Y + j + 1, c);
            g.fill(X + in, Bt - j - 1, X + prev, Bt - j, c);
            g.fill(Rt - prev, Bt - j - 1, Rt - in, Bt - j, c);
        }
        if (H > 2 * R2) {
            g.fill(X, Y + R2, X + 1, Bt - R2, c);
            g.fill(Rt - 1, Y + R2, Rt, Bt - R2, c);
        }
        g.pop();
    }

    static void ring(Gfx g, int x, int y, int w, int h, int r, int c) { ring2(g, x * 2, y * 2, w * 2, h * 2, r * 2, c); }

    /** Soft drop shadow plus an accent glow around a panel. */
    static void glow(Gfx g, int x, int y, int w, int h, int r, int accent) {
        g.batch(() -> {
        for (int i = 10; i >= 1; i--)
            ring2Raw(g, x * 2 - i, y * 2 - i + 4, w * 2 + i * 2, h * 2 + i * 2, r * 2 + i, Theme.alpha(0, (11 - i) * 9));
        for (int i = 6; i >= 1; i--)
            ring2Raw(g, x * 2 - i, y * 2 - i, w * 2 + i * 2, h * 2 + i * 2, r * 2 + i, Theme.alpha(accent, (7 - i) * 7));
        });
    }

    static void circle(Gfx g, int x, int y, int d, int c) { round(g, x, y, d, d, d / 2, c); }

    /** A pixel icon from rows of '#' (one unit per cell), drawn in runs. */
    static void icon(Gfx g, String[] rows, int x, int y, int c) { g.batch(() -> iconRaw(g, rows, x, y, c)); }

    private static void iconRaw(Gfx g, String[] rows, int x, int y, int c) {
        for (int r = 0; r < rows.length; r++) {
            String s = rows[r];
            for (int i = 0; i < s.length(); ) {
                if (s.charAt(i) != '#') { i++; continue; }
                int j = i;
                while (j < s.length() && s.charAt(j) == '#') j++;
                g.fill(x + i, y + r, x + j, y + r + 1, c);
                i = j;
            }
        }
    }
}
