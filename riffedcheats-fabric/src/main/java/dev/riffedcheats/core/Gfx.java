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
    /** From the stub's HUD hook, which only knows it as an Object. */
    public static Gfx of(Object g) { return new Gfx((GuiGraphicsExtractor) g); }

    private static Font font() { return Minecraft.getInstance().font; }

    /** Text in the Modern layout's smooth font (Inter) instead of the pixel font. */
    public static boolean smooth;
    /** Fade of everything drawn (Modern open / page animations); 1 = normal. */
    public static float alpha = 1f;

    private static final net.minecraft.network.chat.FontDescription UI = new net.minecraft.network.chat.FontDescription.Resource(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("riffedcheats", "ui")),
            UI_BOLD = new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.fromNamespaceAndPath("riffedcheats", "ui_bold"));

    private static Component styled(String s, boolean bold) { return Component.literal(s).withStyle(st -> st.withFont(bold ? UI_BOLD : UI)); }

    /** The smooth font's files are loaded (a fresh jar's assets only arrive with a game restart). */
    public static boolean fontReady() {
        try {
            return Minecraft.getInstance().getResourceManager()
                    .getResource(net.minecraft.resources.Identifier.fromNamespaceAndPath("riffedcheats", "font/ui.json")).isPresent();
        } catch (Throwable t) { return false; }
    }

    private static int fade(int argb) {
        if (alpha >= 1f) return argb;
        int a = argb >>> 24;
        if (a == 0) a = 255;          // text colors without alpha are drawn opaque
        return Math.round(a * Math.max(0, alpha)) << 24 | (argb & 0xFFFFFF);
    }

    public static int textWidth(String s) { return smooth ? font().width(styled(s, false)) : font().width(s); }
    public static int boldWidth(String s) { return smooth ? font().width(styled(s, true)) : font().width(s); }

    public void fill(int x1, int y1, int x2, int y2, int argb) { g.fill(x1, y1, x2, y2, fade(argb)); }
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
    private static Method TEXT_C;       // the Component variant, for the smooth font
    static {
        for (Method m : GuiGraphicsExtractor.class.getMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (!m.getName().equals("text") || t.length < 5 || t.length > 6 || t[0] != Font.class || t[1] != Component.class) continue;
            if (TEXT_C == null || t.length == 6) TEXT_C = m;
        }
    }
    private void draw(String s, int x, int y, int argb, boolean bold) {
        int c = fade(argb);
        if (alpha < 1f && (c >>> 24) < 5) return;
        try {
            if (smooth && TEXT_C != null) {
                Component t = styled(s, bold);
                if (TEXT_C.getParameterCount() == 6) TEXT_C.invoke(g, font(), t, x, y, c, false); else TEXT_C.invoke(g, font(), t, x, y, c);
                return;
            }
            Object str = TEXT_COMPONENT ? Component.literal(s) : s;
            if (TEXT.getParameterCount() == 6) TEXT.invoke(g, font(), str, x, y, c, false);
            else TEXT.invoke(g, font(), str, x, y, c);
        } catch (Throwable ignored) {}
    }
    public void text(String s, int x, int y, int argb) { draw(s, x, y, argb, false); }
    /** Semi-bold in the smooth font, normal text otherwise. */
    public void bold(String s, int x, int y, int argb) { draw(s, x, y, argb, true); }
    public int width(String s) { return textWidth(s); }

    /** Only draw inside this box (menu units) until unclip(); this version applies the menu's scaling itself. */
    public void clip(int x0, int y0, int x1, int y1) { g.enableScissor(x0, y0, x1, y1); }
    public void unclip() { g.disableScissor(); }
    public void item(ItemStack st, int x, int y) { g.item(st, x, y); }
    public void decorations(ItemStack st, int x, int y) { g.itemDecorations(font(), st, x, y); }
    public void itemScaled(ItemStack st, float x, float y, float sc) { push(); translate(x, y); scale(sc); g.item(st, 0, 0); pop(); }
    public void push() { g.pose().pushMatrix(); }
    public void translate(float x, float y) { g.pose().translate(x, y); }
    public void scale(float s) { g.pose().scale(s, s); }
    public void pop() { g.pose().popMatrix(); }
    /** The GUI already collects fills and draws them together on this version. */
    public void batch(Runnable r) { r.run(); }


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
        try {
            g.pose().identity();
            net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(g,
                    Math.round(a.x), Math.round(a.y), Math.round(b.x), Math.round(b.y), Math.round(size * s), 0.0625f, mouse.x, mouse.y, e);
        } finally { g.pose().popMatrix(); }   // the menu's scaling must come back even if the entity fails to draw
    }


    /** The player's face (with hat layer) like in the Tab list; nothing if they are not in the player list. */
    public void head(String name, int x, int y, int size) {
        var c = Minecraft.getInstance().getConnection();
        var info = c == null ? null : c.getPlayerInfo(name);
        if (info != null) net.minecraft.client.gui.components.PlayerFaceExtractor.extractRenderState(g, info.getSkin(), x, y, size);
    }
}
