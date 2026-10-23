package dev.riffedcheats.core;

import dev.riffedcheats.Core;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** The hot-swappable part of the mod: everything the menu and its features do. Loaded by dev.riffedcheats.Loader. */
public final class CoreImpl implements Core {
    @Override public void start() { Menu.load(); }
    @Override public void shutdown() { Compat.closeMenu(); Features.shutdown(); }
    @Override public void tick() { Features.tick(); }
    @Override public boolean onMessage(Component c) { return Cmd.onMessage(c); }
    @Override public void preAttack() { Features.preAttack(Minecraft.getInstance().player); }
    @Override public void frame() { Features.frameHook = true; Features.frame(); }
    @Override public boolean freecamOn() { return Features.freecamOn(); }
    @Override public void openMenu() { Compat.openMenu(); }
}
