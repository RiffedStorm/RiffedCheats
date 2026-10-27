package dev.riffedcheats;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads the core (menu + features, package dev.riffedcheats.core) from the mod jar through its own class loader,
 * so an update can drop it and load the new jar's core without restarting the game. This class, Core
 * and the entry point stay as they were loaded at startup.
 */
public final class Loader {
    private static final String CORE = "dev.riffedcheats.core.";

    /** Bump when Core / Loader / the entry point change in a way an old copy cannot serve. */
    public static int api() { return 1; }

    private static volatile Core core;
    private static Path jar;   // jar the running core came from; null = dev build (no hot swap)
    private static final Core GUARD = new Guard();

    /** The running core, behind a guard that falls back to the game's own copy if the separate class loader ever fails. */
    public static Core core() { return GUARD; }
    public static Path jar() { return jar; }

    /** Child-first for the core package: those classes always come from this loader's jar, never from the game's copy. */
    private static final class CoreLoader extends URLClassLoader {
        CoreLoader(Path p) throws Exception { super(new URL[] { p.toUri().toURL() }, Loader.class.getClassLoader()); }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(CORE)) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) c = findClass(name);
                if (resolve) resolveClass(c);
                return c;
            }
        }

        @Override
        public URL getResource(String name) {
            if (name.startsWith("riffedcheats-core")) { URL u = findResource(name); if (u != null) return u; }
            return super.getResource(name);
        }
    }

    /**
     * Safety net: a LinkageError (class access / missing class) from a core loaded by CoreLoader switches to the copy
     * the game itself loaded from the mod jar, and hot updates are off until the next start.
     */
    private static final class Guard implements Core {
        private static <T> T run(java.util.function.Supplier<T> f) {
            try { return f.get(); }
            catch (LinkageError e) {
                if (jar == null) throw e;
                System.err.println("[RiffedCheats] Hot-loaded core failed, falling back to the normal one: " + e);
                fallback();
                return f.get();
            }
        }
        @Override public int needsLoader() { return core.needsLoader(); }
        @Override public void start() { run(() -> { core.start(); return null; }); }
        @Override public void shutdown() { run(() -> { core.shutdown(); return null; }); }
        @Override public void tick() { run(() -> { core.tick(); return null; }); }
        @Override public boolean onMessage(net.minecraft.network.chat.Component c) { return run(() -> core.onMessage(c)); }
        @Override public void preAttack() { run(() -> { core.preAttack(); return null; }); }
        @Override public void frame() { run(() -> { core.frame(); return null; }); }
        @Override public boolean freecamOn() { return run(() -> core.freecamOn()); }
        @Override public void openMenu() { run(() -> { core.openMenu(); return null; }); }
    }

    private static void fallback() {
        try { core.shutdown(); } catch (Throwable ignored) {}
        try {
            core = (Core) Class.forName(CORE + "CoreImpl").getDeclaredConstructor().newInstance();
            jar = null;
            core.start();
        } catch (Exception e) { throw new RuntimeException("RiffedCheats core missing", e); }
    }

    private static Core load(Path p) throws Exception {
        return (Core) new CoreLoader(p).loadClass(CORE + "CoreImpl").getDeclaredConstructor().newInstance();
    }

    /** Called once by the entry point with the mod's own jar. */
    static void init(Path modJar) {
        try {
            if (modJar != null && Files.isRegularFile(modJar)) {
                core = load(modJar);
                jar = modJar;
                core.start();
                return;
            }
        } catch (Throwable t) {
            System.err.println("[RiffedCheats] Could not load the core from " + modJar + ", hot updates disabled: " + t);
        }
        fallback();
    }

    /**
     * Replaces the running core with the one in newJar (client thread).
     * @return null when it worked, "restart" when the new version needs a newer loader part, otherwise the error
     */
    public static String swap(Path newJar) {
        Core next;
        try { next = load(newJar); } catch (Throwable t) { return t.toString(); }
        if (next.needsLoader() > api()) return "restart";
        Core old = core;
        try { old.shutdown(); } catch (Throwable ignored) {}
        try {
            next.start();
            core = next;
            jar = newJar;
            return null;
        } catch (Throwable t) {
            try { old.start(); } catch (Throwable ignored) {}
            return t.toString();
        }
    }
}
