package dev.riffedcheats.core;

/** A menu layout (Classic = MenuView, Modern = ModernView), drawn in MenuView's DW x DH design space. */
public interface MenuUi {
    void render(Gfx g, int mx, int my);
    /** @return true when the menu should close */
    boolean click(int mx, int my, int btn);
    void drag(int mx, int my);
    void release();
    void scroll(int mx, int my, double dy);
    boolean key(int key, int scan, int mods);
    boolean typed(char c);
    boolean capturesText();
}
