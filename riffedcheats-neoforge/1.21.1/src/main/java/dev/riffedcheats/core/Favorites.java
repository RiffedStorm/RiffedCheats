package dev.riffedcheats.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Starred enchantments, items and mobs, in the order you dragged them. Kept in config/riffedcheats-favorites.txt. */
public final class Favorites {
    public static final String ENCH = "ench", ITEM = "item", MOB = "mob";

    private static Map<String, List<String>> map;
    /** Goes up on every change, so lists know when to sort again. */
    public static int version;

    private static Path file() { return Compat.configDir().resolve("riffedcheats-favorites.txt"); }

    private static List<String> list(String kind) {
        if (map == null) {
            map = new HashMap<>();
            try {
                for (String line : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
                    String[] f = line.split("\t");
                    if (f.length == 2 && !map.computeIfAbsent(f[0], k -> new ArrayList<>()).contains(f[1])) map.get(f[0]).add(f[1]);
                }
            } catch (IOException ignored) {}
        }
        return map.computeIfAbsent(kind, k -> new ArrayList<>());
    }

    public static boolean has(String kind, String id) { return list(kind).contains(id); }

    public static void toggle(String kind, String id) {
        if (!list(kind).remove(id)) list(kind).add(id);
        version++;
        save();
    }

    /** Puts id where other is now. Saved when the drag ends. */
    public static void moveTo(String kind, String id, String other) {
        List<String> l = list(kind);
        int to = l.indexOf(other);
        if (to < 0 || !l.remove(id)) return;
        l.add(to, id);
        version++;
    }

    public static void save() {
        if (map == null) return;
        List<String> out = new ArrayList<>();
        map.forEach((k, v) -> { for (String id : v) out.add(k + "\t" + id); });
        try { Files.write(file(), out, StandardCharsets.UTF_8); } catch (IOException ignored) {}
    }

    /** Favorites first, in your order; the rest stay as they were. */
    public static <T> List<T> arrange(String kind, List<T> items, Function<T, String> id) {
        List<String> fav = list(kind);
        if (fav.isEmpty()) return items;
        Map<String, T> byId = new HashMap<>();
        List<T> rest = new ArrayList<>();
        for (T t : items) { if (fav.contains(id.apply(t))) byId.put(id.apply(t), t); else rest.add(t); }
        List<T> out = new ArrayList<>(items.size());
        for (String f : fav) if (byId.containsKey(f)) out.add(byId.get(f));
        out.addAll(rest);
        return out;
    }
}
