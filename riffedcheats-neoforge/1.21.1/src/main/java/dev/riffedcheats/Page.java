package dev.riffedcheats;

/** A custom tab body (item spawner, console, player list). Coordinates are in menu design units. */
public interface Page {
    void render(Gfx g, int mx, int my, int x, int y, int w, int h);
    default boolean click(int mx, int my, int btn) { return false; }
    default void release() {}
    default boolean drag(int mx, int my) { return false; }
    default boolean scroll(int mx, int my, double dy) { return false; }
    default boolean key(int key, int scan, int mods) { return false; }
    default boolean typed(char c) { return false; }
    default boolean wantsText() { return false; }
}
