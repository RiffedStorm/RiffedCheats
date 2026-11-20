package dev.riffedcheats.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Thin wrapper so the shared UI code does not care about the Minecraft version. */
public final class Gfx {
    private final GuiGraphics g;
    public Gfx(GuiGraphics g) { this.g = g; }
    /** From the stub's HUD hook, which only knows it as an Object. */
    public static Gfx of(Object g) { return new Gfx((GuiGraphics) g); }

    private static Font font() { return Minecraft.getInstance().font; }

    /** Text in the Modern layout's smooth font (Inter) instead of the pixel font. */
    public static boolean smooth;
    /** Fade of everything drawn (Modern open / page animations); 1 = normal. */
    public static float alpha = 1f;

    private static final net.minecraft.resources.ResourceLocation UI = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("riffedcheats", "ui"),
            UI_BOLD = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("riffedcheats", "ui_bold");

    private static net.minecraft.network.chat.Component styled(String s, boolean bold) {
        return net.minecraft.network.chat.Component.literal(s).withStyle(st -> st.withFont(bold ? UI_BOLD : UI));
    }

    /** The smooth font's files are loaded (a fresh jar's assets only arrive with a game restart). */
    public static boolean fontReady() {
        try {
            return Minecraft.getInstance().getResourceManager()
                    .getResource(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("riffedcheats", "font/ui.json")).isPresent();
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
    public void text(String s, int x, int y, int argb) {
        int c = fade(argb);
        if (alpha < 1f && (c >>> 24) < 5) return;
        if (smooth) g.drawString(font(), styled(s, false), x, y, c, false); else g.drawString(font(), s, x, y, c, false);
    }
    /** Semi-bold in the smooth font, normal text otherwise. */
    public void bold(String s, int x, int y, int argb) {
        int c = fade(argb);
        if (alpha < 1f && (c >>> 24) < 5) return;
        if (smooth) g.drawString(font(), styled(s, true), x, y, c, false); else g.drawString(font(), s, x, y, c, false);
    }
    public int width(String s) { return textWidth(s); }

    /** Only draw inside this box (menu units) until unclip(). The scissor here works in GUI pixels, so the box is transformed first. */
    public void clip(int x0, int y0, int x1, int y1) {
        org.joml.Matrix4f m = g.pose().last().pose();
        org.joml.Vector3f a = m.transformPosition(new org.joml.Vector3f(x0, y0, 0)), b = m.transformPosition(new org.joml.Vector3f(x1, y1, 0));
        g.enableScissor((int) Math.floor(a.x), (int) Math.floor(a.y), (int) Math.ceil(b.x), (int) Math.ceil(b.y));
    }
    public void unclip() { g.disableScissor(); }
    public void item(ItemStack st, int x, int y) { g.renderItem(st, x, y); }
    public void decorations(ItemStack st, int x, int y) { g.renderItemDecorations(font(), st, x, y); }
    public void itemScaled(ItemStack st, float x, float y, float sc) { push(); translate(x, y); scale(sc); g.renderItem(st, 0, 0); pop(); }
    public void push() { g.pose().pushPose(); }
    public void translate(float x, float y) { g.pose().translate(x, y, 0); }
    public void scale(float s) { g.pose().scale(s, s, 1f); }
    public void pop() { g.pose().popPose(); }
    /** Many fills sent to the GPU together instead of one by one. */
    @SuppressWarnings("deprecation")
    public void batch(Runnable r) { g.drawManaged(r); }


    /**
     * Draws a living entity that looks at the mouse, inside the box x0,y0 - x1,y1 (menu units). size = pixels per block.
     * Vanilla's helper works in plain screen coordinates, so the menu's scaling is applied here and the pose reset meanwhile.
     */
    public void entity(net.minecraft.world.entity.LivingEntity e, int x0, int y0, int x1, int y1, float size, float mx, float my) {
        org.joml.Matrix4f m = g.pose().last().pose();
        org.joml.Vector3f a = m.transformPosition(new org.joml.Vector3f(x0, y0, 0)), b = m.transformPosition(new org.joml.Vector3f(x1, y1, 0));
        org.joml.Vector3f mouse = m.transformPosition(new org.joml.Vector3f(mx, my, 0));
        float s = (b.x - a.x) / Math.max(1, x1 - x0);
        g.pose().pushPose();
        try {
            g.pose().last().pose().identity();
            net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(g,
                    Math.round(a.x), Math.round(a.y), Math.round(b.x), Math.round(b.y), Math.round(size * s), 0.0625f, mouse.x, mouse.y, e);
        } finally { g.pose().popPose(); }   // the menu's scaling must come back even if the entity fails to draw
    }


    /** The player's face (with hat layer) like in the Tab list; nothing if they are not in the player list. */
    public void head(String name, int x, int y, int size) {
        var c = Minecraft.getInstance().getConnection();
        var info = c == null ? null : c.getPlayerInfo(name);
        if (info != null) net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, info.getSkin(), x, y, size);
    }
}
