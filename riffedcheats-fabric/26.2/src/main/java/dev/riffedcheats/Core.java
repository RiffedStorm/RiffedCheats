package dev.riffedcheats;

import net.minecraft.network.chat.Component;

/**
 * Everything the permanent part of the mod (entry point, key bind, Xray mixin) calls into.
 * The implementation, dev.riffedcheats.core.CoreImpl, lives in its own class loader so an update can
 * replace it while the game is running (see Loader). Only add methods with a default body here.
 */
public interface Core {
    /** Loader API version this core needs; a newer value than Loader.api() means "restart to update". */
    default int needsLoader() { return 1; }
    void start();
    void shutdown();
    void tick();
    /** @return true to hide the message from the chat */
    boolean onMessage(Component c);
    void preAttack();
    void frame();
    boolean freecamOn();
    void openMenu();
}
