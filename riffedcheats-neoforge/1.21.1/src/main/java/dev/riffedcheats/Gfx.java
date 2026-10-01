package dev.riffedcheats;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Thin wrapper so the shared UI code does not care about the Minecraft version. */
public final class Gfx {
    private final GuiGraphics g;
    public Gfx(GuiGraphics g) { this.g = g; }

    private static Font font() { return Minecraft.getInstance().font; }
    public static int textWidth(String s) { return font().width(s); }

    public void fill(int x1, int y1, int x2, int y2, int argb) { g.fill(x1, y1, x2, y2, argb); }
    public void text(String s, int x, int y, int argb) { g.drawString(font(), s, x, y, argb, false); }
    public int width(String s) { return font().width(s); }
    public void item(ItemStack st, int x, int y) { g.renderItem(st, x, y); }
    public void itemScaled(ItemStack st, float x, float y, float sc) { push(); translate(x, y); scale(sc); g.renderItem(st, 0, 0); pop(); }
    public void push() { g.pose().pushPose(); }
    public void translate(float x, float y) { g.pose().translate(x, y, 0); }
    public void scale(float s) { g.pose().scale(s, s, 1f); }
    public void pop() { g.pose().popPose(); }
}
