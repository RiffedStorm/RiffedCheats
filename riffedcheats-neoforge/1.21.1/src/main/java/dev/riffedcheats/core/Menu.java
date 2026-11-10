package dev.riffedcheats.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Every category / tab / setting in the menu. Add new features here (UI) and in Features.java (logic). */
public final class Menu {
    public record Tab(String name, List<Setting> settings, Page page) {}
    public record Category(String name, List<Tab> tabs) {}
    public record Eff(Setting on, Setting lvl, String id, int fixed) {
        public int amp() { return Math.max(0, (lvl != null ? lvl.intValue() : fixed) - 1); }
    }

    public static final List<Setting> ALL = new ArrayList<>();
    public static final List<Category> CATEGORIES = new ArrayList<>();
    public static final List<Eff> EFFECTS = new ArrayList<>();

    private static Setting t(String n, boolean d) { Setting s = Setting.toggle(n, d); ALL.add(s); return s; }
    private static Setting s(String n, float min, float max, float step, float d) { Setting s = Setting.slider(n, min, max, step, d); ALL.add(s); return s; }
    private static Setting b(String n, Runnable r) { return Setting.button(n, r); }
    private static Setting cmd(String label, String... c) { return b(label, () -> Cmd.run(c)); }
    private static Setting lab(String n) { return Setting.label(n); }
    /** Row of buttons. p = name, action, name, action... where an action is a Runnable or a command string. */
    private static Setting row(String label, Object... p) {
        String[] names = new String[p.length / 2];
        Runnable[] acts = new Runnable[p.length / 2];
        for (int i = 0; i < names.length; i++) {
            names[i] = (String) p[i * 2];
            Object a = p[i * 2 + 1];
            acts[i] = a instanceof Runnable r ? r : () -> Cmd.run((String) a);
        }
        return Setting.buttons(label, names, acts);
    }
    private static Runnable act(Runnable r) { return r; }
    private static Setting rule(String label, String key) { return Setting.rule(label, Compat.gamerule(key)); }
    private static Tab tab(String n, Setting... s) {
        List<Setting> out = new ArrayList<>();
        for (Setting x : s) {
            if (x == null) continue;
            if (x.kind == Setting.Kind.LABEL && x.name.length() > 56) {      // long hints become several short lines
                StringBuilder line = new StringBuilder();
                for (String w : x.name.split(" ")) {
                    if (line.length() > 0 && line.length() + 1 + w.length() > 56) { out.add(Setting.label(line.toString())); line.setLength(0); }
                    if (line.length() > 0) line.append(' ');
                    line.append(w);
                }
                if (line.length() > 0) out.add(Setting.label(line.toString()));
            } else out.add(x);
        }
        return new Tab(n, out, null);
    }
    private static Tab page(String n, Page p) { return new Tab(n, List.of(), p); }
    private static void cat(String n, Tab... t) { CATEGORIES.add(new Category(n, List.of(t))); }
    private static Setting eff(String label, String id, int min, int max, int def, int fixed) {
        Setting on = t(label, false);
        Setting lvl = fixed > 0 ? null : s(label + " Level", min, max, 1, def);
        EFFECTS.add(new Eff(on, lvl, id, fixed));
        return on;
    }
    private static Setting effLvl(Setting on) { for (Eff e : EFFECTS) if (e.on() == on) return e.lvl(); return null; }

    // ---------------- Self ----------------
    public static final Setting GOD = eff("God Mode", "minecraft:resistance", 1, 1, 1, 5);
    public static final Setting HEAL = eff("Auto Heal", "minecraft:regeneration", 1, 1, 1, 10);
    public static final Setting HUNGER = eff("Infinite Hunger", "minecraft:saturation", 1, 1, 1, 1);
    public static final Setting NOFALL = t("No Fall Damage", false);

    public static final Setting FLIGHT = t("Flight", false);
    public static final Setting FLY_SPEED = s("Fly Speed", 1, 10, 0.5f, 2);
    public static final Setting NOCLIP = t("Noclip (fly through blocks)", false).needsServer();
    public static final Setting SPEED = eff("Walk Speed", "minecraft:speed", 1, 100, 3, 0);
    public static final Setting JUMP = eff("High Jump", "minecraft:jump_boost", 1, 100, 3, 0);
    public static final Setting STEP = t("Step Assist", false);
    public static final Setting STEP_H = s("Step Height", 1, 10, 0.5f, 2);
    public static final Setting LOWGRAV = t("Low Gravity", false);
    public static final Setting GRAV_MULT = s("Gravity Multiplier", 0.05f, 1, 0.05f, 0.3f);
    public static final Setting INFJUMP = t("Infinite Jump (air jumps)", false);
    public static final Setting AUTOSPRINT = t("Auto Sprint", false);

    public static final Setting REACH = t("Extended Reach", false);
    public static final Setting BLOCK_REACH = s("Block Reach", 4.5f, 20, 0.5f, 8);
    public static final Setting ENTITY_REACH = s("Entity Reach", 3, 10, 0.5f, 6);

    public static final Setting HASTE = eff("Haste (fast mining)", "minecraft:haste", 1, 100, 3, 0);
    public static final Setting STRENGTH = eff("Strength", "minecraft:strength", 1, 100, 3, 0);
    public static final Setting WATERB = eff("Water Breathing", "minecraft:water_breathing", 1, 1, 1, 1);
    public static final Setting FIRERES = eff("Fire Resistance", "minecraft:fire_resistance", 1, 1, 1, 1);
    public static final Setting INVIS = eff("Invisibility", "minecraft:invisibility", 1, 1, 1, 1);
    public static final Setting SLOWFALL = eff("Slow Falling", "minecraft:slow_falling", 1, 1, 1, 1);
    public static final Setting HBOOST = eff("Health Boost", "minecraft:health_boost", 1, 100, 5, 0);
    public static final Setting ABSORB = eff("Absorption", "minecraft:absorption", 1, 100, 5, 0);
    public static final Setting LUCK = eff("Luck", "minecraft:luck", 1, 100, 3, 0);
    public static final Setting CONDUIT = eff("Conduit Power", "minecraft:conduit_power", 1, 1, 1, 1);
    public static final Setting DOLPHIN = eff("Dolphin's Grace", "minecraft:dolphins_grace", 1, 1, 1, 1);

    public static final Setting XP_FREEZE = t("Freeze XP Level", false);
    public static final Setting XP_LEVEL = s("Target Level", 0, 2000, 1, 30);

    // ---------------- Combat ----------------
    public static final Setting AURA = t("Kill Aura", false);
    public static final Setting AURA_RANGE = s("Aura Range", 1, 12, 0.5f, 4);
    public static final Setting AURA_DELAY = s("Aura Delay (ticks)", 1, 20, 1, 4);
    public static final Setting AURA_HOSTILE = t("Hostile Mobs Only", true);
    public static final Setting AURA_PLAYERS = t("Target Players", false);
    public static final Setting AURA_WALLS = t("Hit Through Walls", true);
    public static final Setting AURA_SPARE = t("Spare Named Mobs + Pets", true);
    public static final Setting CRIT = t("Always Crit (works on ground)", false);
    public static final Setting NODELAY = t("No Attack Delay", false);
    public static final Setting ANTIKB = t("Anti-Knockback", false);
    public static final Setting INSTANT_BLOCK = t("Instant Shield Block", false).needsServer();
    public static final Setting NO_SHIELD_CD = t("No Shield Cooldown", false).needsServer();

    public static final Setting FULLBRIGHT = t("Fullbright", false);
    // Freecam is disabled for now (crashed on dedicated servers): not in the menu, not saved, always off.
    // The code in Features.java stays dormant until it is fixed.
    public static final Setting FREECAM = Setting.toggle("Freecam", false);
    public static final Setting FREECAM_SPEED = Setting.slider("Freecam Speed", 0.5f, 8, 0.5f, 2);

    // ---------------- Items / settings ----------------
    public static final Setting UNBREAKABLE = t("Infinite Durability", false);
    public static final Setting OVERLEVEL = t("Over-Max Enchant Levels", false);
    /** Mobs > Villagers switch: summoned villagers' normal trades never run out (kept up by Features.keepTradesUnlimited). */
    public static final Setting VILL_UNLIMITED = t("Villagers: Unlimited Uses", false);
    public static final Setting TIME_CUSTOM = s("Custom Time (ticks)", 0, 24000, 100, 6000);
    public static final Setting AUTOSAVE = t("Autosave", true);
    public static final Setting UPDATE_CHECK = t("Check for Updates", true);
    public static final Setting MULTI_CONFIG = t("Multiple Configs", false);
    /** 0 = Auto, 1 = Client Side, 2 = Server Side (Settings > Server). */
    public static final Setting CONN = s("Connection Mode", 0, 2, 1, 0);

    // ---------------- Mobs > Kill Field ----------------
    public static final Setting KILL_ON = t("Kill Field", false);
    public static final Setting KILL_RADIUS = s("Kill Field Radius", 4, 64, 4, 64);
    public static final Setting KILL_NAMED = t("Kill Field: Spare Named", true);
    public static final Setting KILL_PETS = t("Kill Field: Spare Pets", true);

    private static Setting opt(String n, boolean d, String id) { return t(n, d).option(id); }
    private static Setting num(String n, float min, float max, float step, float d, String id) { return s(n, min, max, step, d).option(id); }
    private static Setting pick(String n, int d, String id, String... o) { Setting c = Setting.choice(n, d, o).option(id); ALL.add(c); return c; }

    // ---------------- Wallhack ----------------
    public static final Setting ESP_ON = t("Wallhack", false);
    public static final Setting ESP_HOSTILE = opt("Hostile Mobs", true, "esp.hostile"), ESP_FRIENDLY = opt("Friendly Mobs", false, "esp.friendly"), ESP_PLAYERS = opt("Players", true, "esp.players");
    public static final Setting ESP_BOX = opt("2D Boxes", true, "esp.box"), ESP_HEALTH = opt("Health Bars", true, "esp.health");
    public static final Setting ESP_NAMES = opt("Names", true, "esp.names"), ESP_DIST = opt("Distance", false, "esp.dist");
    public static final Setting ESP_RANGE = num("Range", 8, 256, 8, 64, "esp.range");
    public static final Setting ESP_CANHIT = opt("Only If Aimbot Can Hit", false, "esp.canhit");
    public static final Setting ESP_TRACERS = opt("Tracer Lines", false, "esp.tracers"), ESP_HP_TEXT = opt("Health Numbers", false, "esp.hptext");
    public static final Setting ESP_INVIS = opt("Show Invisible", true, "esp.invis"), ESP_TEAM = opt("Hide Teammates", false, "esp.team");
    public static final Setting ESP_SPARE = opt("Hide Named Mobs + Pets", false, "esp.spare");
    public static final Setting ESP_THICK = num("Box Thickness", 1, 3, 1, 1, "esp.thick");
    public static final Setting ESP_CUSTOM = opt("Use Custom Mobs", false, "esp.custom");

    // ---------------- Aimbot ----------------
    public static final Setting AIM_ON = t("Aimbot", false);
    public static final Setting AIM_HOSTILE = opt("Hostile Mobs", true, "aim.hostile"), AIM_FRIENDLY = opt("Friendly Mobs", false, "aim.friendly"), AIM_PLAYERS = opt("Players", false, "aim.players");
    public static final Setting AIM_KEYMODE = pick("Key", 0, "aim.keymode", "Hold", "Toggle", "Always On", "On Attack");
    public static final Setting AIM_BONE = pick("Aim At", 1, "aim.bone", "Head", "Body", "Feet");
    public static final Setting AIM_RANGE = num("Melee Range", 4, 128, 2, 8, "aim.range");
    public static final Setting AIM_RANGE_BOW = num("Bow + Crossbow Range", 4, 256, 2, 64, "aim.rangebow");
    public static final Setting AIM_FOV = num("Field of View", 5, 360, 5, 90, "aim.fov");
    public static final Setting AIM_SMOOTH = num("Smoothness %", 0, 95, 5, 30, "aim.smooth");
    public static final Setting AIM_PRIORITY = pick("Melee Priority", 0, "aim.priority", "Crosshair", "Closest", "Low Health");
    public static final Setting AIM_PRIORITY_BOW = pick("Bow Priority", 0, "aim.prioritybow", "Crosshair", "Closest", "Low Health");
    public static final Setting AIM_WALLS = opt("Aim Through Walls", false, "aim.walls"), AIM_STICKY = opt("Stay On Target", true, "aim.sticky");
    public static final Setting AIM_AUTOHIT = opt("Auto Attack", false, "aim.autohit"), AIM_BOW = opt("Bow + Trident Prediction", true, "aim.bow");
    public static final Setting AIM_LEAD = opt("Lead Moving Targets", true, "aim.lead"), AIM_TEAM = opt("Ignore Teammates", true, "aim.team");
    public static final Setting AIM_SPARE = opt("Spare Named Mobs + Pets", true, "aim.spare"), AIM_INVIS = opt("Ignore Invisible", false, "aim.invis");
    public static final Setting AIM_SHOW_FOV = opt("Show Field of View Circle", false, "aim.showfov");
    public static final Setting AIM_CUSTOM = opt("Use Custom Mobs", false, "aim.custom");
    public static final Setting AIM_STATUS = opt("Show On / Off Text", true, "aim.status");
    public static final Setting AIM_STATUS_POS = pick("Text Position", 0, "aim.statuspos", "Top Left", "Top Right", "Crosshair");

    // ---------------- Items > Helpers ----------------
    public static final Setting AUTO_REPAIR = t("Auto Repair Gear", false).needsServer();
    public static final Setting MAGNET = t("Item + XP Magnet", false).needsServer();
    public static final Setting MAGNET_RANGE = s("Magnet Range", 2, 32, 1, 10).needsServer();

    // ---------------- ProtoManly's Weather (only when the mod is installed) ----------------
    public static final Setting PM_SC_STAGE = s("Supercell Stage", 0, 2, 1, 1), PM_SC_ENERGY = s("Supercell Energy", 1, 99, 1, 50);
    public static final Setting PM_SQ_STAGE = s("Squall Stage", 0, 3, 1, 1), PM_SQ_ENERGY = s("Squall Energy", 1, 99, 1, 50);
    public static final Setting PM_TOR_WIND = s("Tornado Windspeed", 0, 400, 5, 120), PM_TOR_WIDTH = s("Tornado Width", 5, 1200, 5, 60);
    public static final Setting PM_CYC_WIND = s("Hurricane Windspeed", 0, 250, 5, 100), PM_CYC_WIDTH = s("Hurricane Width", 3000, 16000, 100, 6000);
    public static final Setting PM_FW_WIND = s("Fire Whirl Windspeed", 0, 100, 1, 40), PM_FW_WIDTH = s("Fire Whirl Width", 1, 10, 1, 3);
    public static final Setting PM_STRIKE = s("Lightning Power", 1, 20, 1, 5);

    static {
        cat("Self",
            tab("Main", GOD, HEAL, HUNGER, NOFALL, FULLBRIGHT,
                row("Gamemode", "Survival", "gamemode survival @s", "Creative", "gamemode creative @s",
                    "Adventure", "gamemode adventure @s", "Spectator", "gamemode spectator @s"),
                b("Set My Spawnpoint Here", Features::setSpawnHere),
                row("Quick", "Heal + Feed", act(() -> Cmd.run("effect give @s minecraft:instant_health 1 10 true", "effect give @s minecraft:saturation 1 10 true")),
                    "Clear My Effects", "effect clear @s"),
                cmd("Clear My Inventory", "clear @s"),
                cmd("Kill Myself", "kill @s").red(),
                lab("God: real invulnerability in Server Mode, Resistance V in Client Mode.")),
            tab("Movement", FLIGHT, FLY_SPEED, NOCLIP, SPEED, effLvl(SPEED), JUMP, effLvl(JUMP), STEP, STEP_H, LOWGRAV, GRAV_MULT, INFJUMP, AUTOSPRINT,
                lab("In Client Mode, Flight on a dedicated server needs allow-flight=true.")),
            tab("Reach", REACH, BLOCK_REACH, ENTITY_REACH,
                lab("Reach/Step/Gravity/Anti-KB/No Delay/No Fall change server attributes."),
                lab("Turn them off here to reset them. Use Settings > Reset after a /deop.")),
            tab("Effects", HASTE, effLvl(HASTE), STRENGTH, effLvl(STRENGTH), WATERB, FIRERES, INVIS, SLOWFALL, HBOOST, effLvl(HBOOST),
                ABSORB, effLvl(ABSORB), LUCK, effLvl(LUCK), CONDUIT, DOLPHIN),
            tab("XP / Level", XP_FREEZE, XP_LEVEL,
                b("Apply Level Now", () -> Cmd.run("xp set @s " + XP_LEVEL.intValue() + " levels")),
                b("Target Level +1", () -> bump(1)), b("Target Level +10", () -> bump(10)), b("Target Level +100", () -> bump(100)),
                b("Target Level -1", () -> bump(-1)), b("Target Level -10", () -> bump(-10)), b("Target Level -100", () -> bump(-100)),
                cmd("Give +1000 Levels", "xp add @s 1000 levels"), cmd("Set Level 0", "xp set @s 0 levels"),
                lab("Freeze only sends a command when the level actually changes.")),
            page("Inventory", new InventoryPage(null, null)));

        cat("Combat",
            tab("Aura", AURA, AURA_RANGE, AURA_DELAY, AURA_HOSTILE, AURA_PLAYERS, AURA_WALLS, AURA_SPARE),
            tab("Attack", CRIT, NODELAY, ANTIKB,
                lab("Always Crit sends tiny hop packets before each hit, no jumping needed."),
                lab("No Attack Delay removes the cooldown bar: spam hits at full damage."),
                lab("Anti-Knockback also covers explosions.")),
            tab("Shield", INSTANT_BLOCK, NO_SHIELD_CD,
                lab("Instant Shield Block: the shield works the moment you raise it."),
                lab("No Shield Cooldown: axes can no longer disable your shield.")));

        cat("Wallhack",
            tab("Main", ESP_ON, ESP_HOSTILE, ESP_FRIENDLY, ESP_PLAYERS, ESP_BOX, ESP_HEALTH, ESP_NAMES, ESP_DIST, ESP_RANGE, ESP_CANHIT,
                lab("Only If Aimbot Can Hit uses Aimbot's presets, range and line of sight.")),
            tab("Advanced", ESP_TRACERS, ESP_HP_TEXT, ESP_INVIS, ESP_TEAM, ESP_SPARE, ESP_THICK,
                lab("Red = hostile, green = friendly, blue = player, purple = other, white = Aimbot's target.")),
            page("Custom Mobs", new MobListPage("esp_custom", ESP_CUSTOM)));

        cat("Aimbot",
            tab("Main", AIM_ON, AIM_HOSTILE, AIM_FRIENDLY, AIM_PLAYERS, AIM_KEYMODE, AIM_BONE, AIM_RANGE, AIM_RANGE_BOW, AIM_FOV, AIM_SMOOTH,
                lab("Default key: V. Change it in Options > Controls > Key Binds > RiffedCheats.")),
            tab("Advanced", AIM_PRIORITY, AIM_PRIORITY_BOW, AIM_WALLS, AIM_STICKY, AIM_AUTOHIT, AIM_BOW, AIM_LEAD, AIM_TEAM, AIM_SPARE, AIM_INVIS, AIM_SHOW_FOV, AIM_STATUS, AIM_STATUS_POS),
            page("Custom Mobs", new MobListPage("aim_custom", AIM_CUSTOM)));

        cat("World",
            tab("Time",
                row("Skip to", "Day", act(() -> Features.setTime(1000)), "Noon", act(() -> Features.setTime(6000)),
                    "Sunset", act(() -> Features.setTime(12000))),
                row("", "Night", act(() -> Features.setTime(13000)), "Midnight", act(() -> Features.setTime(18000)),
                    "Sunrise", act(() -> Features.setTime(23000))),
                TIME_CUSTOM, b("Skip to Custom Time", () -> Features.setTime(TIME_CUSTOM.intValue())),
                cmd("Skip Forward 1 Day", "time add 24000"),
                lab("Time only moves forward, so the day counter never resets.")),
            Compat.modLoaded("pmweather") ? pmWeather() : tab("Weather",
                row("Weather", "Clear", "weather clear", "Rain", "weather rain", "Thunder", "weather thunder"),
                row("Difficulty", "Peaceful", "difficulty peaceful", "Easy", "difficulty easy", "Normal", "difficulty normal", "Hard", "difficulty hard")),
            tab("Game Rules",
                rule("Keep Inventory", "keep_inventory"), rule("Mob Griefing", "mob_griefing"),
                rule("Daylight Cycle", "daylight"), rule("Weather Cycle", "weather"), rule("Mob Spawning", "mobspawn"),
                rule("Phantoms", "phantoms"), rule("Patrols", "patrols"), rule("Wandering Traders", "traders"),
                rule("Immediate Respawn", "immediate_respawn"), rule("Natural Regeneration", "regen"),
                rule("Fall Damage", "fall_damage"), rule("Fire Damage", "fire_damage"),
                rule("Mob Loot Drops", "mob_drops"), rule("Block Drops", "block_drops"),
                rule("Death Messages", "death_messages"), rule("Command Feedback", "feedback"),
                lab("The buttons show the current server value (green = on, red = off). Click to flip.")));

        cat("Teleport",
            page("Locations", new TeleportPage()),
            tab("Quick",
                b("To World Spawn", Features::tpSpawn),
                b("To Last Death", Teleports::tpLastDeath),
                row("Move", "Up 10", "tp @s ~ ~10 ~", "Up 50", "tp @s ~ ~50 ~", "Down 10", "tp @s ~ ~-10 ~", "Sky (Y 300)", "tp @s ~ 300 ~"),
                b("Set My Spawnpoint Here", Features::setSpawnHere),
                lab("Saved locations are kept per world / server and per dimension, and work across dimensions.")));

        cat("Items",
            page("Spawner", new ItemsPage()),
            tab("God Gear",
                cmd("Equip God Armor (Netherite, all Prot IV)", Gear.armor("@s")),
                cmd("Give God Tools (full set)", Gear.tools("@s")),
                cmd("God Sword", Gear.tools("@s")[0]), cmd("God Pickaxe", Gear.tools("@s")[1]), cmd("God Axe", Gear.tools("@s")[2]),
                cmd("God Shovel", Gear.tools("@s")[3]), cmd("God Hoe", Gear.tools("@s")[4]), cmd("God Bow + Arrows", Gear.tools("@s")[5], Gear.tools("@s")[8]),
                cmd("God Trident", Gear.tools("@s")[6]), cmd("God Elytra", Gear.tools("@s")[7]), cmd("Silk Touch Pickaxe", Gear.silkPickaxe("@s")),
                lab("Everything is unbreakable and fully enchanted.")),
            tab("Quick Items",
                cmd("Enchanted Golden Apples x64", "give @s minecraft:enchanted_golden_apple 64"), cmd("Totem of Undying", "give @s minecraft:totem_of_undying"),
                cmd("Firework Rockets x64", "give @s minecraft:firework_rocket 64"), cmd("Diamonds x64", "give @s minecraft:diamond 64"),
                cmd("Netherite Ingots x64", "give @s minecraft:netherite_ingot 64"), cmd("Ender Pearls x16", "give @s minecraft:ender_pearl 16"),
                cmd("XP Bottles x64", "give @s minecraft:experience_bottle 64"), cmd("TNT x64", "give @s minecraft:tnt 64"),
                cmd("Shulker Box", "give @s minecraft:shulker_box"), cmd("Command Block", "give @s minecraft:command_block")),
            tab("Helpers", AUTO_REPAIR, MAGNET, MAGNET_RANGE,
                lab("Auto Repair keeps everything you carry at full durability."),
                lab("The Magnet pulls dropped items and XP orbs to you.")));

        cat("Mobs",
            page("Summon", new MobsPage()),
            page("Villagers", new VillagerPage()),
            page("Kill Field", new KillFieldPage()),
            tab("Cleanup",
                cmd("Kill Mobs Within 64m (not players/items)", "kill @e[type=!minecraft:player,type=!minecraft:item,distance=..64]"),
                cmd("Remove Dropped Items Within 64m", "kill @e[type=minecraft:item,distance=..64]"),
                cmd("Remove XP Orbs Within 64m", "kill @e[type=minecraft:experience_orb,distance=..64]")));

        cat("Players", page("Player List", new PlayersPage()));
        cat("Console", page("Command Executor", new ConsolePage()));

        cat("Settings",
            tab("Menu", AUTOSAVE, b("Save Config Now", Menu::save),
                b("Reset All Sliders to Default", Menu::resetSliders),
                b("Disable All Features", Menu::disableAll),
                b("Reset Server Attributes + Effects", Features::resetServerState),
                lab("Autosave is ON by default. Turn it off to keep changes temporary."),
                lab("Your settings are kept in memory when you lose OP and come back when you regain it.")),
            page("Configs", new ConfigsPage()),
            page("Server", new ServerPage()),
            page("Updates", new UpdatesPage()));
    }

    private static Tab pmWeather() {
        return tab("Weather",
            row("Difficulty", "Peaceful", "difficulty peaceful", "Easy", "difficulty easy", "Normal", "difficulty normal", "Hard", "difficulty hard"),
            row("Natural Storm", "Supercell", "pmweather spawn supercell natural", "Squall", "pmweather spawn squall natural",
                "Hurricane", "pmweather spawn cyclone natural"),
            cmd("Clear All Storms", "pmweather clear all"),
            PM_SC_STAGE, PM_SC_ENERGY, b("Spawn Supercell", () -> Cmd.run("pmweather spawn supercell " + PM_SC_STAGE.intValue() + " " + PM_SC_ENERGY.intValue())),
            PM_SQ_STAGE, PM_SQ_ENERGY, b("Spawn Squall", () -> Cmd.run("pmweather spawn squall " + PM_SQ_STAGE.intValue() + " " + PM_SQ_ENERGY.intValue())),
            PM_TOR_WIND, PM_TOR_WIDTH, b("Spawn Tornado", () -> Cmd.run("pmweather spawn tornado " + PM_TOR_WIND.intValue() + " " + PM_TOR_WIDTH.intValue())),
            PM_CYC_WIND, PM_CYC_WIDTH, b("Spawn Hurricane", () -> Cmd.run("pmweather spawn cyclone " + PM_CYC_WIND.intValue() + " " + PM_CYC_WIDTH.intValue())),
            PM_FW_WIND, PM_FW_WIDTH, b("Spawn Fire Whirl", () -> Cmd.run("pmweather spawn firewhirl " + PM_FW_WIND.intValue() + " " + PM_FW_WIDTH.intValue())),
            PM_STRIKE, b("Lightning Strike On Me", () -> Cmd.run("pmweather effect strike ~ ~ ~ " + PM_STRIKE.intValue())),
            lab("ProtoManly's Weather detected, so its storms replace the vanilla weather buttons. Storms spawn around you."));
    }

    public static void resetSliders() {
        for (Setting st : ALL) if (st.kind == Setting.Kind.SLIDER && st != CONN) st.value = st.def;
        changed();
    }

    private static void bump(int d) { XP_LEVEL.value = Math.max(0, Math.min(100000, XP_LEVEL.value + d)); changed(); }

    public static void disableAll() {
        for (Setting st : ALL) if (st.kind == Setting.Kind.TOGGLE && !st.keep && st != AUTOSAVE && st != UPDATE_CHECK && st != UNBREAKABLE && st != OVERLEVEL && st != VILL_UNLIMITED
                && st != MULTI_CONFIG && st != KILL_NAMED && st != KILL_PETS && st != AURA_SPARE && st != AURA_WALLS) st.on = false;
        changed();
    }

    // ---------------- persistence ----------------
    /** Called after any user change. Saves immediately when Autosave is on. */
    public static void changed() { if (AUTOSAVE.on) save(); }

    private static Path file() { return Compat.configDir().resolve("riffedcheats.properties"); }

    private static final Map<String, String> STRINGS = new HashMap<>();
    private static final String STR = "str.";

    /** A saved text value (for example the Kill Field's mob list). */
    public static String str(String k) { return STRINGS.getOrDefault(k, ""); }
    public static void setStr(String k, String v) { STRINGS.put(k, v); changed(); }

    /** Settings about the menu itself, which a named config must not change. */
    private static boolean notInConfig(Setting st) { return st == MULTI_CONFIG || st == CONN || st == AUTOSAVE || st == UPDATE_CHECK; }

    static Properties snapshot(boolean config) {
        Properties p = new Properties();
        for (Setting st : ALL) {
            if (config && notInConfig(st)) continue;
            p.setProperty(st.id(), st.kind == Setting.Kind.TOGGLE ? Boolean.toString(st.on) : Float.toString(st.value));
        }
        STRINGS.forEach((k, v) -> p.setProperty(STR + k, v));
        return p;
    }

    static void apply(Properties p, boolean config) {
        for (Setting st : ALL) {
            if (config && notInConfig(st)) continue;
            String v = p.getProperty(st.id());
            if (v == null) continue;
            try {
                if (st.kind == Setting.Kind.TOGGLE) st.on = Boolean.parseBoolean(v); else st.value = Float.parseFloat(v);
            } catch (NumberFormatException ignored) {}
        }
        for (String k : p.stringPropertyNames()) if (k.startsWith(STR)) STRINGS.put(k.substring(STR.length()), p.getProperty(k));
    }

    public static void save() {
        try (var w = Files.newBufferedWriter(file())) { snapshot(false).store(w, "RiffedCheats"); } catch (IOException ignored) {}
    }

    /** First launch (no file): defaults apply, Autosave is ON. */
    public static void load() {
        Properties p = new Properties();
        try (var r = Files.newBufferedReader(file())) { p.load(r); } catch (IOException e) { return; }
        apply(p, false);
        if (MULTI_CONFIG.on) { String a = Configs.autoload(); if (a != null) Configs.load(a); }
    }
}
