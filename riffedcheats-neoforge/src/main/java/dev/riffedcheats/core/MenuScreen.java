package dev.riffedcheats.core;

import dev.riffedcheats.RiffedCheats;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Scales the fixed-size menu so it covers the same share of the window (about 62% of its height,
 * like your 1440p screenshot) at every resolution and every GUI scale.
 */
public class MenuScreen extends Screen {
    private final MenuView classic = new MenuView();
    private final ModernView modern = new ModernView();
    private float s = 1f;
    private int ox, oy;

    public MenuScreen() { super(Component.literal("RiffedCheats")); }

    @Override protected void init() { layout(); Theme.fontReady = Gfx.fontReady(); }

    /** The layout picked in Settings > Appearance, with the theme and font set up for it. */
    private MenuUi view() {
        Theme.apply();
        Gfx.smooth = Theme.smoothFont();
        return Theme.modern() ? modern : classic;
    }
    private static void done() { Gfx.smooth = false; Gfx.alpha = 1f; }

    private void layout() {
        var win = Minecraft.getInstance().getWindow();
        double gs = win.getGuiScale();
        double sc = 0.625 * win.getHeight() / gs / MenuView.DH;
        sc = Math.min(sc, 0.95 * win.getWidth() / gs / MenuView.DW);
        s = (float) sc;
        ox = Math.round((width - MenuView.DW * s) / 2f);
        oy = Math.round((height - MenuView.DH * s) / 2f);
    }

    private int lx(double mx) { return (int) Math.floor((mx - ox) / s); }
    private int ly(double my) { return (int) Math.floor((my - oy) / s); }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        MenuUi v = view();
        try {
            if (Theme.backdrop() == 2) renderBlurredBackground(pt);
            g.fill(0, 0, width, height, Theme.modern() ? 0x38000000 : 0x50000000);
            Gfx gfx = new Gfx(g);
            gfx.push();
            gfx.translate(ox, oy);
            gfx.scale(s);
            v.render(gfx, lx(mx), ly(my));
            gfx.pop();
        } finally { done(); }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        boolean close;
        try { close = view().click(lx(mx), ly(my), button); } finally { done(); }
        if (close) onClose();
        return true;
    }

    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) { try { view().drag(lx(mx), ly(my)); } finally { done(); } return true; }
    @Override public boolean mouseReleased(double mx, double my, int b) { try { view().release(); } finally { done(); } return true; }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) { try { view().scroll(lx(mx), ly(my), sy); } finally { done(); } return true; }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean printable = key >= 32 && key <= 96;
        MenuUi view = view();
        boolean used;
        try {
            if (RiffedCheats.TOGGLE.matches(key, scan) && !(view.capturesText() && printable)) { onClose(); return true; }
            used = view.key(key, scan, mods);
        } finally { done(); }
        if (used) return true;
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        boolean used;
        try { used = view().typed(c); } finally { done(); }
        return used || super.charTyped(c, mods);
    }
}
