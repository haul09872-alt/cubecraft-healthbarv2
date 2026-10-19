package dev.cchb.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.cchb.CcHealthBarMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** JSON config: .minecraft/config/cubecraft-healthbar.json. Edit by hand or with /cchb set. */
public final class ModConfig {
    public enum HealthSource { AUTO, SCOREBOARD, ENTITY }
    public enum Visibility { AUTO, SCOREBOARD, COMBAT_TIMER, ALWAYS }

    // ---- general ----
    public boolean enabled = true;
    /** Suppress CubeCraft's own below-name indicator (only possible when it is a scoreboard row). */
    public boolean hideOriginal = true;

    // ---- geometry (nametag pixels: 1 px = 0.025 blocks, like vanilla nametag text) ----
    public float width = 40f;
    public float height = 5f;
    public float borderPx = 0.5f;
    /** Estimated gap between the nametag row and the reference bar's top edge (tune from the video). */
    public float referenceTopPx = 1.0f;
    /** Move the bar toward the nametag by this fraction of its own height (0.10-0.15 requested). */
    public float closerFraction = 0.125f;
    /** Extra manual offset in px. Positive = lower, negative = higher. */
    public float verticalOffset = 0f;
    /** Never let the bar come closer than this many px to the nametag text. */
    public float minClearancePx = 1.0f;
    public float scale = 1.0f;
    public float maxDistance = 64f;

    // ---- colours (RGB hex) ----
    public float backgroundOpacity = 0.55f;
    public String colorFull = "#55FF55";
    public String colorMid = "#FFFF55";
    public String colorLow = "#FF5555";
    public String colorTrack = "#000000";
    public String colorBorder = "#000000";
    public String colorAbsorption = "#FFD700";
    public String colorText = "#FFFFFF";

    // ---- extras ----
    public boolean showNumeric = false;
    public boolean showAbsorption = true;

    // ---- animation ----
    public boolean smoothAnimation = true;
    /** Exponential time constant. ~63% of the gap closes in this time, ~95% in 3x. */
    public float smoothingTimeMs = 100f;

    // ---- data / visibility ----
    /** AUTO | SCOREBOARD | ENTITY */
    public String healthSource = "AUTO";
    /** AUTO | SCOREBOARD | COMBAT_TIMER | ALWAYS (ALWAYS is for debugging only) */
    public String visibility = "AUTO";
    /** Optional: name of the below-name objective to treat as CubeCraft's health indicator. */
    public String scoreboardObjectiveName = "";
    /** Max health used to turn a scoreboard value into a ratio. */
    public float scoreboardMaxHealth = 20f;
    /** COMBAT_TIMER approximation window. 7000 comes from a 2020 CubeCraft proposal; NOT confirmed. */
    public int combatWindowMs = 7000;

    // ---- server gate / debug ----
    public List<String> extraServerHosts = new ArrayList<>();
    /** Dev testing only: run on any multiplayer server. Leave false for normal play. */
    public boolean devAllowAnyServer = false;
    public boolean debug = false;

    // ---- compiled values (not serialised) ----
    public transient int cFull, cMid, cLow, cTrack, cBorder, cAbs, cText;
    public transient HealthSource sourceMode = HealthSource.AUTO;
    public transient Visibility visibilityMode = Visibility.AUTO;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig instance = new ModConfig();

    static { instance.compile(); }

    public static ModConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("cubecraft-healthbar.json");
    }

    public static void load() {
        ModConfig loaded = null;
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p)) {
                loaded = GSON.fromJson(r, ModConfig.class);
            } catch (Exception e) {
                CcHealthBarMod.LOGGER.warn("Could not read config, using defaults: {}", e.toString());
            }
        }
        if (loaded == null) loaded = new ModConfig();
        loaded.compile();
        instance = loaded;
        loaded.save();
    }

    public void save() {
        try (Writer w = Files.newBufferedWriter(path())) {
            GSON.toJson(this, w);
        } catch (IOException e) {
            CcHealthBarMod.LOGGER.warn("Could not save config: {}", e.toString());
        }
    }

    /** Clamp everything and pre-parse colours/enums so rendering does no parsing. */
    public void compile() {
        width = clamp(width, 4f, 200f);
        height = clamp(height, 1f, 40f);
        borderPx = clamp(borderPx, 0f, Math.min(width, height) * 0.4f);
        referenceTopPx = clamp(referenceTopPx, -5f, 20f);
        closerFraction = clamp(closerFraction, 0f, 1f);
        verticalOffset = clamp(verticalOffset, -10f, 20f);
        minClearancePx = clamp(minClearancePx, 0f, 5f);
        scale = clamp(scale, 0.25f, 4f);
        maxDistance = clamp(maxDistance, 4f, 256f);
        backgroundOpacity = clamp(backgroundOpacity, 0f, 1f);
        smoothingTimeMs = clamp(smoothingTimeMs, 1f, 2000f);
        scoreboardMaxHealth = clamp(scoreboardMaxHealth, 1f, 10000f);
        combatWindowMs = Math.max(500, Math.min(60000, combatWindowMs));
        if (scoreboardObjectiveName == null) scoreboardObjectiveName = "";
        if (extraServerHosts == null) extraServerHosts = new ArrayList<>();

        cFull = rgb(colorFull, 0x55FF55);
        cMid = rgb(colorMid, 0xFFFF55);
        cLow = rgb(colorLow, 0xFF5555);
        cTrack = rgb(colorTrack, 0x000000);
        cBorder = rgb(colorBorder, 0x000000);
        cAbs = rgb(colorAbsorption, 0xFFD700);
        cText = rgb(colorText, 0xFFFFFF);

        sourceMode = parseEnum(HealthSource.class, healthSource, HealthSource.AUTO);
        visibilityMode = parseEnum(Visibility.class, visibility, Visibility.AUTO);
    }

    private static float clamp(float v, float lo, float hi) {
        if (Float.isNaN(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }

    private static int rgb(String s, int fallback) {
        if (s == null) return fallback;
        String h = s.trim();
        if (h.startsWith("#")) h = h.substring(1);
        try {
            return Integer.parseInt(h, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> cls, String s, E fallback) {
        if (s == null) return fallback;
        try {
            return Enum.valueOf(cls, s.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    // ---- /cchb set support -------------------------------------------------------------

    public static List<String> keys() {
        List<String> out = new ArrayList<>();
        for (Field f : ModConfig.class.getFields()) {
            if (editable(f)) out.add(f.getName());
        }
        return out;
    }

    private static boolean editable(Field f) {
        int m = f.getModifiers();
        if (Modifier.isStatic(m) || Modifier.isTransient(m)) return false;
        Class<?> t = f.getType();
        return t == boolean.class || t == float.class || t == int.class || t == String.class;
    }

    public static String describe() {
        StringBuilder sb = new StringBuilder("Settings (use /cchb set <name> <value>):");
        for (Field f : ModConfig.class.getFields()) {
            if (!editable(f)) continue;
            try {
                sb.append('\n').append(f.getName()).append(" = ").append(f.get(instance));
            } catch (IllegalAccessException ignored) { }
        }
        sb.append("\n(extraServerHosts: edit the JSON file)");
        return sb.toString();
    }

    public static String set(String key, String raw) {
        Field f;
        try {
            f = ModConfig.class.getField(key);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("Unknown setting '" + key + "'. See /cchb list");
        }
        if (!editable(f)) throw new IllegalArgumentException("'" + key + "' can only be edited in the JSON file.");
        ModConfig c = instance;
        Class<?> t = f.getType();
        try {
            String v = raw.trim();
            if (t == boolean.class) f.setBoolean(c, parseBool(v));
            else if (t == float.class) f.setFloat(c, Float.parseFloat(v));
            else if (t == int.class) f.setInt(c, Integer.parseInt(v));
            else f.set(c, v);
            c.compile();
            c.save();
            return key + " = " + f.get(c);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + raw + "' is not a valid number.");
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Cannot set '" + key + "'.");
        }
    }

    private static boolean parseBool(String v) {
        String s = v.toLowerCase(Locale.ROOT);
        if (s.equals("true") || s.equals("on") || s.equals("yes") || s.equals("1")) return true;
        if (s.equals("false") || s.equals("off") || s.equals("no") || s.equals("0")) return false;
        throw new IllegalArgumentException("Expected true/false, got '" + v + "'.");
    }
}
