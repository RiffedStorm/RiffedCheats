package dev.riffedcheats;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Tiny reflection helpers. Used only for Minecraft 26.x APIs whose exact shape could not be verified
 * at build time, so a rename costs you one feature instead of the whole build.
 */
final class Refl {
    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == boolean.class) return Boolean.class;
        if (c == double.class) return Double.class;
        if (c == float.class) return Float.class;
        if (c == long.class) return Long.class;
        return c;
    }

    static Object call(Object target, String name, Object... args) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            Class<?>[] pt = m.getParameterTypes();
            boolean ok = true;
            for (int i = 0; i < pt.length; i++) if (args[i] == null || !box(pt[i]).isInstance(args[i])) { ok = false; break; }
            if (ok) { m.setAccessible(true); return m.invoke(target, args); }
        }
        throw new NoSuchMethodException(name);
    }

    static Object callAny(Object target, String... names) throws Exception {
        Exception last = null;
        for (String n : names) { try { return call(target, n); } catch (Exception e) { last = e; } }
        throw last == null ? new NoSuchMethodException() : last;
    }

    static void setField(Object target, String name, Object v) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); f.set(target, v); return; }
            catch (NoSuchFieldException e) { c = c.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    static Object getField(Object target, String name) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(target); }
            catch (NoSuchFieldException e) { c = c.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
}
