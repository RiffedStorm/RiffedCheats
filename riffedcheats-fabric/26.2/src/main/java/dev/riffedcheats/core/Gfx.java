package dev.riffedcheats.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import java.lang.reflect.Method;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Thin wrapper so the shared UI code does not care about the Minecraft version (26.2: GuiGraphicsExtractor). */
public final class Gfx {
    private final GuiGraphicsExtractor g;
    public Gfx(GuiGraphicsExtractor g) { this.g = g; }

    private static Font font() { return Minecraft.getInstance().font; }
    public static int textWidth(String s) { return font().width(s); }

    public void fill(int x1, int y1, int x2, int y2, int argb) { g.fill(x1, y1, x2, y2, argb); }
    // The text method was renamed/reshaped in 26.x, so it is looked up once and cached.
    private static Method TEXT;
    private static boolean TEXT_COMPONENT;
    static {
        for (Method m : GuiGraphicsExtractor.class.getMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (!m.getName().equals("text") || t.length < 5 || t.length > 6 || t[0] != Font.class) continue;
            boolean str = t[1] == String.class, comp = t[1] == Component.class;
            if (!str && !comp) continue;
            if (TEXT == null || (str && TEXT_COMPONENT) || (str == !TEXT_COMPONENT && t.length == 6)) { TEXT = m; TEXT_COMPONENT = comp; }
        }
    }
    public void text(String s, int x, int y, int argb) {
        try {
            Object str = TEXT_COMPONENT ? Component.literal(s) : s;
            if (TEXT.getParameterCount() == 6) TEXT.invoke(g, font(), str, x, y, argb, false);
            else TEXT.invoke(g, font(), str, x, y, argb);
        } catch (Throwable ignored) {}
    }
    public int width(String s) { return font().width(s); }
    public void item(ItemStack st, int x, int y) { g.item(st, x, y); }
    public void decorations(ItemStack st, int x, int y) { g.itemDecorations(font(), st, x, y); }
    public void itemScaled(ItemStack st, float x, float y, float sc) { push(); translate(x, y); scale(sc); g.item(st, 0, 0); pop(); }
    public void push() { g.pose().pushMatrix(); }
    public void translate(float x, float y) { g.pose().translate(x, y); }
    public void scale(float s) { g.pose().scale(s, s); }
    public void pop() { g.pose().popMatrix(); }


    /**
     * Draws a living entity that looks at the mouse, inside the box x0,y0 - x1,y1 (menu units). size = pixels per block.
     * Vanilla's helper works in plain screen coordinates, so the menu's scaling is applied here and the pose reset meanwhile.
     */
    public void entity(net.minecraft.world.entity.LivingEntity e, int x0, int y0, int x1, int y1, float size, float mx, float my) {
        org.joml.Matrix3x2f m = new org.joml.Matrix3x2f(g.pose());
        org.joml.Vector2f a = m.transformPosition(new org.joml.Vector2f(x0, y0)), b = m.transformPosition(new org.joml.Vector2f(x1, y1));
        org.joml.Vector2f mouse = m.transformPosition(new org.joml.Vector2f(mx, my));
        float s = (b.x - a.x) / Math.max(1, x1 - x0);
        g.pose().pushMatrix();
        g.pose().identity();
        net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(g,
                Math.round(a.x), Math.round(a.y), Math.round(b.x), Math.round(b.y), Math.round(size * s), 0.0625f, mouse.x, mouse.y, e);
        g.pose().popMatrix();
    }
}
