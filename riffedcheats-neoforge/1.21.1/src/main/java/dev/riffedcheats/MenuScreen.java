package dev.riffedcheats;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x50000000);
        Gfx gfx = new Gfx(g);
        gfx.push();
        gfx.translate(ox, oy);
        gfx.scale(s);
        view.render(gfx, lx(mx), ly(my));
        gfx.pop();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (view.click(lx(mx), ly(my), button)) onClose();
        return true;
    }

    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) { view.drag(lx(mx), ly(my)); return true; }
    @Override public boolean mouseReleased(double mx, double my, int b) { view.release(); return true; }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) { view.scroll(lx(mx), ly(my), sy); return true; }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean printable = key >= 32 && key <= 96;
        if (RiffedCheats.TOGGLE.matches(key, scan) && !(view.capturesText() && printable)) { onClose(); return true; }
        if (view.key(key, scan, mods)) return true;
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) { return view.typed(c) || super.charTyped(c, mods); }
}
