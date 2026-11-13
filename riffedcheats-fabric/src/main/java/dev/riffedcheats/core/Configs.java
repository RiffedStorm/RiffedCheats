package dev.riffedcheats.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Named copies of the menu settings in config/riffedcheats-configs/, one of which can load by itself at start. */
public final class Configs {
    private static final String EXT = ".properties";

    private static Path dir() { return Compat.configDir().resolve("riffedcheats-configs"); }
    private static Path file(String name) { return dir().resolve(name + EXT); }
    private static Path autoFile() { return dir().resolve("autoload.txt"); }

    public static String clean(String n) {
        n = n.replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
        return n.length() > 32 ? n.substring(0, 32).trim() : n;
    }

    public static List<String> names() {
        List<String> out = new ArrayList<>();
        try (DirectoryStream<Path> s = Files.newDirectoryStream(dir(), "*" + EXT)) {
            for (Path p : s) { String f = p.getFileName().toString(); out.add(f.substring(0, f.length() - EXT.length())); }
        } catch (IOException ignored) {}
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public static boolean save(String name) {
        try {
            Files.createDirectories(dir());
            try (var w = Files.newBufferedWriter(file(name), StandardCharsets.UTF_8)) { Menu.snapshot(true).store(w, "RiffedCheats config: " + name); }
            return true;
        } catch (IOException e) { return false; }
    }

    public static boolean load(String name) {
        Properties p = new Properties();
        try (var r = Files.newBufferedReader(file(name), StandardCharsets.UTF_8)) { p.load(r); } catch (IOException e) { return false; }
        Menu.apply(p, true);
        Menu.changed();
        return true;
    }

    public static void delete(String name) {
        try { Files.deleteIfExists(file(name)); } catch (IOException ignored) {}
        if (name.equals(autoload())) setAutoload(null);
    }

    /** Name of the config that loads at start, null if none. */
    public static String autoload() {
        try {
            String n = Files.readString(autoFile(), StandardCharsets.UTF_8).trim();
            return n.isEmpty() || !Files.isRegularFile(file(n)) ? null : n;
        } catch (IOException e) { return null; }
    }

    public static void setAutoload(String name) {
        try {
            if (name == null) { Files.deleteIfExists(autoFile()); return; }
            Files.createDirectories(dir());
            Files.writeString(autoFile(), name, StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }
}
