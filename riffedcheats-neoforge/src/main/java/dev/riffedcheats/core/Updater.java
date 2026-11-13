package dev.riffedcheats.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.riffedcheats.Loader;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipFile;
import net.minecraft.client.Minecraft;

/**
 * Checks GitHub for a newer release, downloads the jar for this Minecraft version + loader into the mods folder,
 * removes the old jar and swaps the new core in while the game keeps running (Loader.swap).
 */
public final class Updater {
    public enum State { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, RESTART, FAILED }

    private static final String REPO = "RiffedStorm/RiffedCheats";
    private static final String LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";

    public static volatile State state = State.IDLE;
    public static volatile String latest, status = "Not checked yet.";
    private static volatile String assetUrl, assetName;
    private static boolean autoChecked;
    private static String current;

    /** Set by the previous core right before it handed over to this one. */
    public static final String UPDATED_TO = System.getProperty("riffedcheats.updatedTo");
    private static final long STARTED = System.currentTimeMillis();
    static {
        if (UPDATED_TO != null) {
            System.clearProperty("riffedcheats.updatedTo");
            latest = UPDATED_TO;
            state = State.UP_TO_DATE;
            status = "Updated to " + UPDATED_TO + " without a restart.";
            autoChecked = true;
        }
    }

    /** True for a few seconds after a hot update, so the menu header can say so. */
    public static boolean justUpdated() { return UPDATED_TO != null && System.currentTimeMillis() - STARTED < 20000; }

    /** Version of the running core (from riffedcheats-core.properties inside its own jar). */
    public static String current() {
        if (current != null) return current;
        String v = "?";
        try (InputStream in = CoreImpl.class.getClassLoader().getResourceAsStream("riffedcheats-core.properties")) {
            if (in != null) { Properties p = new Properties(); p.load(in); v = p.getProperty("version", "?"); }
        } catch (Exception ignored) {}
        return current = v;
    }

    /** 1.2.10 > 1.2.9; missing parts count as 0. */
    static boolean newer(String a, String b) {
        String[] x = a.replaceAll("[^0-9.]", "").split("\\."), y = b.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length && !x[i].isEmpty() ? Integer.parseInt(x[i]) : 0, q = i < y.length && !y[i].isEmpty() ? Integer.parseInt(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    /** java.base only (java.net.http is not always part of the game's module layer). Follows GitHub's download redirects. */
    private static HttpURLConnection open(String url, int readTimeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(10000);
        c.setReadTimeout(readTimeoutMs);
        c.setRequestProperty("User-Agent", "RiffedCheats-Updater");
        c.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream");
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        return c;
    }

    /** Once per game session, when the menu opens (if enabled in Settings > Updates). */
    public static void autoCheck() {
        if (autoChecked || !Menu.UPDATE_CHECK.on) return;
        autoChecked = true;
        check();
    }

    public static void check() {
        if (state == State.CHECKING || state == State.DOWNLOADING) return;
        state = State.CHECKING;
        status = "Checking GitHub...";
        CompletableFuture.runAsync(() -> {
            try {
                String body;
                try (InputStream in = open(LATEST, 15000).getInputStream()) { body = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
                JsonObject o = JsonParser.parseString(body).getAsJsonObject();
                String tag = o.get("tag_name").getAsString(), url = null, name = null;
                for (JsonElement a : o.getAsJsonArray("assets")) {
                    String n = a.getAsJsonObject().get("name").getAsString();
                    if (n.startsWith(Compat.assetPrefix()) && n.endsWith(".jar") && !n.endsWith("-sources.jar")) {
                        name = n;
                        url = a.getAsJsonObject().get("browser_download_url").getAsString();
                    }
                }
                latest = tag;
                assetUrl = url;
                assetName = name;
                if (!newer(tag, current())) { state = State.UP_TO_DATE; status = "You have the latest version."; return; }
                if (url == null) { state = State.FAILED; status = "Release " + tag + " has no jar for " + Compat.buildName() + "."; return; }
                state = State.AVAILABLE;
                status = "Version " + tag + " is available.";
            } catch (Exception e) {
                state = State.FAILED;
                status = "Update check failed: " + e.getMessage();
            }
        });
    }

    /** Already on the latest version, but its jar can be downloaded again and reloaded (repairs a broken jar, tests the reload). */
    public static boolean canReinstall() { return state == State.UP_TO_DATE && assetUrl != null && latest != null && !newer(current(), latest); }

    public static void update() {
        if (state != State.AVAILABLE && !canReinstall()) return;
        state = State.DOWNLOADING;
        status = "Downloading " + assetName + "...";
        String url = assetUrl, name = assetName;
        CompletableFuture.runAsync(() -> {
            Path mods = Compat.modsDir(), part = mods.resolve(name + ".part"), dest = mods.resolve(name);
            try {
                try (InputStream in = open(url, 120000).getInputStream()) { Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING); }
                try (ZipFile z = new ZipFile(part.toFile())) {   // make sure it really is this mod
                    if (z.getEntry("dev/riffedcheats/core/CoreImpl.class") == null) throw new Exception("the download is not a RiffedCheats jar");
                }
                Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING);
                Minecraft.getInstance().execute(() -> finish(dest));
            } catch (Exception e) {
                try { Files.deleteIfExists(part); } catch (Exception ignored) {}
                state = State.FAILED;
                status = "Download failed: " + e.getMessage();
            }
        });
    }

    /** Client thread: remove the old jar (so the next start loads only the new one), then hot swap. */
    private static void finish(Path dest) {
        Path old = Loader.jar();
        String leftover = old == null || old.equals(dest) ? null : removeOld(old);
        if (old == null) {
            state = State.RESTART;
            status = "Downloaded " + latest + ". This is a dev build, so restart Minecraft to use it.";
            return;
        }
        System.setProperty("riffedcheats.updatedTo", latest);
        String r = Loader.swap(dest);
        if (r == null) { Loader.core().openMenu(); return; }   // the new core takes it from here
        System.clearProperty("riffedcheats.updatedTo");
        state = State.RESTART;
        status = (r.equals("restart") ? "Downloaded " + latest + ". This update changes the loader part of the mod, restart Minecraft to finish it."
                : "Downloaded " + latest + ", but it could not be swapped in (" + r + "). Restart Minecraft to use it.")
                + (leftover != null ? " " + leftover : "");
    }

    /** @return a note for the user when the old jar could not be removed right away (Windows keeps loaded jars locked) */
    private static String removeOld(Path old) {
        try { Files.deleteIfExists(old); return null; } catch (Exception ignored) {}
        try { Files.move(old, old.resolveSibling(old.getFileName() + ".disabled"), StandardCopyOption.REPLACE_EXISTING); return null; } catch (Exception ignored) {}
        old.toFile().deleteOnExit();
        return "If " + old.getFileName() + " is still in your mods folder after closing the game, delete it.";
    }
}
