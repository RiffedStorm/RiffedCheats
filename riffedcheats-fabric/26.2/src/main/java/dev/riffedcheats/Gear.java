package dev.riffedcheats;

/** God armor / god tools command builders. target = "@s" or a player name. */
public final class Gear {
    private static final String U = ",unbreakable={}";
    private static final String PROT = "protection:4,fire_protection:4,blast_protection:4,projectile_protection:4,";

    private static String eq(String t, String slot, String item, String e) {
        return "item replace entity " + t + " armor." + slot + " with " + item + "[" + Compat.ench(e) + U + "]";
    }
    private static String give(String t, String item, String e) {
        return "give " + t + " " + item + "[" + Compat.ench(e) + U + "]";
    }

    public static String[] armor(String t) {
        return new String[] {
            eq(t, "head", "netherite_helmet", PROT + "respiration:3,aqua_affinity:1,unbreaking:3,mending:1"),
            eq(t, "chest", "netherite_chestplate", PROT + "unbreaking:3,mending:1,thorns:3"),
            eq(t, "legs", "netherite_leggings", PROT + "swift_sneak:3,unbreaking:3,mending:1"),
            eq(t, "feet", "netherite_boots", PROT + "feather_falling:4,depth_strider:3,soul_speed:3,unbreaking:3,mending:1"),
        };
    }

    public static String[] tools(String t) {
        String base = "unbreaking:3,mending:1";
        return new String[] {
            give(t, "netherite_sword", "sharpness:5,looting:3,sweeping_edge:3,fire_aspect:2,knockback:2," + base),
            give(t, "netherite_pickaxe", "efficiency:5,fortune:3," + base),
            give(t, "netherite_axe", "efficiency:5,sharpness:5,fortune:3," + base),
            give(t, "netherite_shovel", "efficiency:5,fortune:3," + base),
            give(t, "netherite_hoe", "efficiency:5,fortune:3," + base),
            give(t, "bow", "power:5,punch:2,flame:1,infinity:1," + base),
            give(t, "trident", "impaling:5,loyalty:3,channeling:1," + base),
            give(t, "elytra", base),
            "give " + t + " arrow 64",
        };
    }

    public static String[] silkPickaxe(String t) {
        return new String[] { give(t, "netherite_pickaxe", "efficiency:5,silk_touch:1,unbreaking:3,mending:1") };
    }
}
