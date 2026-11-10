package dev.riffedcheats.core;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/** The presets Wallhack and Aimbot share: Hostile Mobs, Friendly Mobs, Players, and your own list of mobs. */
final class Targets {
    enum Kind { HOSTILE, FRIENDLY, PLAYER, OTHER }

    static Kind kind(Entity e) {
        if (e instanceof Player) return Kind.PLAYER;
        if (!(e instanceof LivingEntity) || e instanceof ArmorStand) return Kind.OTHER;
        return e instanceof Enemy ? Kind.HOSTILE : Kind.FRIENDLY;
    }

    static Set<String> list(String key) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : Menu.str(key).split(",")) if (!s.isBlank()) out.add(s);
        return out;
    }

    static String typeId(Entity e) { return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(); }

    /** Selected by the presets, or by the custom list (empty = not in use). */
    static boolean selected(Entity e, boolean hostile, boolean friendly, boolean players, Set<String> custom) {
        boolean preset = switch (kind(e)) {
            case HOSTILE -> hostile;
            case FRIENDLY -> friendly;
            case PLAYER -> players;
            default -> false;
        };
        return preset || (!custom.isEmpty() && custom.contains(typeId(e)));
    }

    /** Things neither feature should ever pick, plus the shared "leave these alone" options. */
    static boolean allowed(Entity e, LocalPlayer p, boolean skipTeam, boolean spare, boolean skipInvisible) {
        if (e == p || !e.isAlive() || e.isSpectator()) return false;
        if (e instanceof LivingEntity l && l.isDeadOrDying()) return false;
        if (skipTeam && p.isAlliedTo(e)) return false;
        if (spare && !(e instanceof Player) && (e.hasCustomName() || Compat.isPet(e))) return false;
        return !skipInvisible || !e.isInvisibleTo(p);
    }
}
