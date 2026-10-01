package dev.riffedcheats;

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
    private final MenuView view = new MenuView();
    private float s = 1f;
    private int ox, oy;

    public MenuScreen() { super(Component.literal("RiffedCheats")); }

    @Override protected void init() { layout(); }

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
        g.fill(0, 0, width, height, 0x50000000);
        Gfx gfx = new Gfx(g);
        gfx.push();
        gfx.translate(ox, oy);
        gfx.scale(s);
        view.render(gfx, lx(mx), ly(my));
        gfx.pop();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (view.click(lx(e.x()), ly(e.y()), e.button())) onClose();
        return true;
    }

    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { view.drag(lx(e.x()), ly(e.y())); return true; }
    @Override public boolean mouseReleased(MouseButtonEvent e) { view.release(); return true; }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) { view.scroll(lx(mx), ly(my), sy); return true; }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        boolean printable = key >= 32 && key <= 96;
        if (RiffedCheats.TOGGLE.matches(e) && !(view.capturesText() && printable)) { onClose(); return true; }
        if (view.key(key, e.scancode(), e.modifiers())) return true;
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        char c = (char) e.codepoint();
        return view.typed(c) || super.charTyped(e);
    }
}
