package dev.riffedcheats.core;

import dev.riffedcheats.RiffedCheats;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Theme.apply();
        if (Theme.backdrop() == 2) extractBlurredBackground(g); else super.extractBackground(g, mx, my, delta);
    }

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
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        MenuUi v = view();
        try {
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
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        boolean close;
        try { close = view().click(lx(e.x()), ly(e.y()), e.button()); } finally { done(); }
        if (close) onClose();
        return true;
    }

    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { try { view().drag(lx(e.x()), ly(e.y())); } finally { done(); } return true; }
    @Override public boolean mouseReleased(MouseButtonEvent e) { try { view().release(); } finally { done(); } return true; }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) { try { view().scroll(lx(mx), ly(my), sy); } finally { done(); } return true; }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        boolean printable = key >= 32 && key <= 96;
        MenuUi view = view();
        boolean used;
        try {
            if (RiffedCheats.TOGGLE.matches(e) && !(view.capturesText() && printable)) { onClose(); return true; }
            used = view.key(key, e.scancode(), e.modifiers());
        } finally { done(); }
        if (used) return true;
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        char c = (char) e.codepoint();
        boolean used;
        try { used = view().typed(c); } finally { done(); }
        return used || super.charTyped(e);
    }
}
