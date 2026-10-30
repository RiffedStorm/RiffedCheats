package dev.riffedcheats.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Saved teleport locations, kept per world / server and per dimension in config/riffedcheats-teleports.txt
 * (one tab-separated line each), plus your last death point. Teleports use "execute in", so they work across dimensions.
 */
public final class Teleports {
    public record Loc(String world, String dim, String name, double x, double y, double z, float yaw, float pitch) {
        public String coords() { return (int) Math.floor(x) + ", " + (int) Math.floor(y) + ", " + (int) Math.floor(z); }
    }

    private static List<Loc> all;
    private static Loc death;          // recorded the moment you die (exact position, this session)
    private static boolean wasDead;

    private static java.nio.file.Path file() { return Compat.configDir().resolve("riffedcheats-teleports.txt"); }

    private static List<Loc> all() {
        if (all != null) return all;
        all = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
                String[] f = line.split("\t");
                if (f.length < 8) continue;
                try {
                    all.add(new Loc(f[0], f[1], f[2], Double.parseDouble(f[3]), Double.parseDouble(f[4]), Double.parseDouble(f[5]),
                            Float.parseFloat(f[6]), Float.parseFloat(f[7])));
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException ignored) {}
        return all;
    }

    private static void save() {
        List<String> out = new ArrayList<>();
        for (Loc l : all())
            out.add(String.join("\t", l.world(), l.dim(), l.name(), num(l.x()), num(l.y()), num(l.z()), num(l.yaw()), num(l.pitch())));
        try { Files.write(file(), out, StandardCharsets.UTF_8); } catch (IOException ignored) {}
    }

    private static String num(double v) { return String.format(Locale.ROOT, "%.2f", v); }
    private static String clean(String n) {
        n = n.replaceAll("[\\t\\r\\n]", " ").trim();
        return n.length() > 32 ? n.substring(0, 32) : n;
    }

    /** Saved spots of the current world in one dimension. */
    public static List<Loc> list(String dim) {
        String w = Compat.worldId();
        List<Loc> out = new ArrayList<>();
        for (Loc l : all()) if (l.world().equals(w) && l.dim().equals(dim)) out.add(l);
        return out;
    }

    /** Dimension tabs: the vanilla three, the one you are in, and any other with saved spots in this world. */
    public static List<String> dims() {
        Set<String> s = new LinkedHashSet<>(List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"));
        s.add(Compat.dimension());
        String w = Compat.worldId();
        for (Loc l : all()) if (l.world().equals(w)) s.add(l.dim());
        return new ArrayList<>(s);
    }

    public static void addHere(String name) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        String dim = Compat.dimension();
        if (clean(name).isEmpty()) name = "Location " + (list(dim).size() + 1);
        all().add(new Loc(Compat.worldId(), dim, clean(name), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()));
        save();
    }

    public static void remove(Loc l) { all().remove(l); save(); }

    /** Moves a spot to position index within its world + dimension list (the order you see and save). */
    public static void move(Loc l, int index) {
        List<Loc> a = all();
        if (!a.remove(l)) return;
        List<Loc> same = new ArrayList<>();
        for (Loc o : a) if (o.world().equals(l.world()) && o.dim().equals(l.dim())) same.add(o);
        index = Math.max(0, Math.min(index, same.size()));
        if (index < same.size()) a.add(a.indexOf(same.get(index)), l);           // in front of the one now at that place
        else if (same.isEmpty()) a.add(l);
        else a.add(a.indexOf(same.get(same.size() - 1)) + 1, l);                 // after the last one
        save();
    }

    public static void rename(Loc l, String name) {
        int i = all().indexOf(l);
        if (i < 0 || clean(name).isEmpty()) return;
        all().set(i, new Loc(l.world(), l.dim(), clean(name), l.x(), l.y(), l.z(), l.yaw(), l.pitch()));
        save();
    }

    public static void tp(Loc l) {
        Cmd.run("execute in " + l.dim() + " run tp @s " + num(l.x()) + " " + num(l.y()) + " " + num(l.z())
                + " " + num(l.yaw()) + " " + num(l.pitch()));
    }

    /** Where you died last: our own exact record from this session, otherwise the position vanilla keeps for the recovery compass. */
    public static Loc lastDeath() {
        if (death != null && death.world().equals(Compat.worldId())) return death;
        return Compat.lastDeath();
    }

    public static void tpLastDeath() { Loc d = lastDeath(); if (d != null) tp(d); }

    static void tick(LocalPlayer p) {
        boolean dead = p.isDeadOrDying();
        if (dead && !wasDead)
            death = new Loc(Compat.worldId(), Compat.dimension(), "Last Death", p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
        wasDead = dead;
    }

    public static String dimName(String id) {
        return switch (id) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "The End";
            default -> {
                String p = id.substring(id.indexOf(':') + 1).replace('_', ' ');
                StringBuilder sb = new StringBuilder();
                for (String s : p.split(" ")) if (!s.isEmpty()) sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).append(' ');
                yield sb.toString().trim();
            }
        };
    }
}
