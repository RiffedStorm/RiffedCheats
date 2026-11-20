package dev.riffedcheats.core;

/**
 * Colors of the menu. Classic + Default is exactly the look the menu always had; Dark and OLED only change colors.
 * Modern gets its own palette built from one accent color. Applied before every menu frame and input, so changes
 * in Settings > Appearance show at once.
 */
public final class Theme {
    public static final String[] ACCENT_NAMES = { "Diamond", "Emerald", "Amber", "Redstone", "Amethyst", "Gold", "Blue" };
    static final int[] ACCENTS = { 0x3FD8E6, 0x3BD47A, 0xF0A23A, 0xF0504A, 0xA77BF0, 0xF0CF3A, 0x6C8CF0 };

    /** Modern palette (ARGB). */
    static int bg, side, card, raised, raised2, border, accent, accentSoft, accentFaint, text, dim;
    /** Set when the menu opens: the smooth font's files are loaded (they need a game restart after an update). */
    public static boolean fontReady;

    public static boolean modern() { return Menu.UI_LAYOUT.intValue() == 1; }
    public static boolean smoothFont() { return modern() && Menu.UI_FONT.on && fontReady; }
    /** 0 = solid, 1 = see-through, 2 = frosted blur. */
    public static int backdrop() { return modern() ? Menu.UI_BACKDROP.intValue() : 0; }

    static int alpha(int rgb, int a) { return Math.max(0, Math.min(255, a)) << 24 | (rgb & 0xFFFFFF); }

    static int mix(int a, int b, float t) {
        int r = (int) ((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t), g = (int) ((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t),
            bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    private static void classic(int bgc, int panel, int btn, int sel, int hover, int acc, int txt, int dm, int dark) {
        Ui.BG = bgc; Ui.PANEL = panel; Ui.BTN = btn; Ui.SEL = sel; Ui.HOVER = hover; Ui.ACCENT = acc; Ui.TEXT = txt; Ui.DIM = dm; Ui.DARK = dark;
        Ui.GREEN = 0xFF4CAF7A; Ui.RED = 0xFFE05C5C; Ui.REDBTN = 0xFF5A2630; Ui.REDHOV = 0xFF7A3040;
    }

    public static void apply() {
        int t = Menu.UI_THEME.intValue();
        if (!modern()) {
            switch (t) {
                case 1 -> classic(0xF2121214, 0xFF19191C, 0xFF2A2A2F, 0xFF44444E, 0xFF35353B, 0xFF8AA0F0, 0xFFECECEF, 0xFF9A9AA3, 0xFF0D0D0F);
                case 2 -> classic(0xFF000000, 0xFF050505, 0xFF161616, 0xFF343434, 0xFF242424, 0xFF8AA8FF, 0xFFFFFFFF, 0xFFA8A8A8, 0xFF000000);
                default -> classic(0xF0101426, 0xFF171B34, 0xFF26305A, 0xFF3B4C8C, 0xFF2F3B70, 0xFF6C8CF0, 0xFFE8EAF6, 0xFF8E94B8, 0xFF10132A);
            }
            return;
        }
        int[] p = switch (t) {      // bg, side, card, raised, raised2, text, dim
            case 1 -> new int[] { 0x131315, 0x0F0F11, 0x1A1A1D, 0x26262B, 0x313137, 0xECECEF, 0x9A9AA3 };
            case 2 -> new int[] { 0x000000, 0x000000, 0x090909, 0x171717, 0x242424, 0xFFFFFF, 0xA4A4A4 };
            default -> new int[] { 0x0F141A, 0x0B0F14, 0x151C24, 0x1F2832, 0x2A3542, 0xE6EDF3, 0x8B98A5 };
        };
        int a = Menu.UI_BACKDROP.intValue() == 0 ? 255 : Math.round(Menu.UI_BACKDROP_ALPHA.value * 2.55f);
        int acc = ACCENTS[Math.max(0, Math.min(ACCENTS.length - 1, Menu.UI_ACCENT.intValue()))];
        bg = alpha(p[0], a);
        side = alpha(p[1], a);
        card = alpha(p[2], Math.min(255, a + 30));
        raised = 0xFF000000 | p[3];
        raised2 = 0xFF000000 | p[4];
        border = t == 2 ? 0x22FFFFFF : 0x16FFFFFF;
        accent = 0xFF000000 | acc;
        accentSoft = alpha(acc, 0x66);
        accentFaint = alpha(acc, 0x26);
        text = 0xFF000000 | p[5];
        dim = 0xFF000000 | p[6];
        Ui.BG = bg; Ui.PANEL = card; Ui.BTN = raised; Ui.HOVER = raised2; Ui.SEL = mix(raised, accent, 0.4f); Ui.ACCENT = accent;
        Ui.TEXT = text; Ui.DIM = dim; Ui.DARK = 0xFF000000 | (t == 2 ? 0x000000 : p[1]);
        Ui.GREEN = 0xFF3CC47C; Ui.RED = 0xFFF0605C; Ui.REDBTN = 0xFF4E2329; Ui.REDHOV = 0xFF6E2C36;
    }
}
