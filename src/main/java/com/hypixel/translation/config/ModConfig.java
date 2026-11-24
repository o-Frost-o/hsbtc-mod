package com.hypixel.translation.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central configuration holder for Hypixel Skyblock Translation Mod.
 * Manages per-category 3-state interception modes (DISPLAY, PACKET, DISABLED),
 * tooltip/chat preview options, and untranslated capture settings.
 */
public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile ModConfig INSTANCE = new ModConfig();

    public static ModConfig getInstance() {
        return INSTANCE;
    }

    public static void setInstance(ModConfig config) {
        INSTANCE = config;
    }

    private final Map<ComponentCategory, InterceptionMode> categoryModes = new ConcurrentHashMap<>();
    private final AtomicBoolean enableAltShiftPreview = new AtomicBoolean(true);
    private final AtomicBoolean showEnglishSubtitle = new AtomicBoolean(false);
    private final AtomicBoolean untranslatedCapture = new AtomicBoolean(true);

    private Path configPath = Paths.get("config", "hypixel_skyblock_translation", "config.json");

    public ModConfig() {
        // Initialize all 9 categories to default DISPLAY mode
        for (ComponentCategory cat : ComponentCategory.values()) {
            categoryModes.put(cat, InterceptionMode.DISPLAY);
        }
    }

    public InterceptionMode getInterceptionMode(ComponentCategory category) {
        if (category == null) return InterceptionMode.DISPLAY;
        return categoryModes.getOrDefault(category, InterceptionMode.DISPLAY);
    }

    public void setInterceptionMode(ComponentCategory category, InterceptionMode mode) {
        if (category != null && mode != null) {
            categoryModes.put(category, mode);
        }
    }

    public Map<ComponentCategory, InterceptionMode> getCategoryModes() {
        return categoryModes;
    }

    public boolean isEnableAltShiftPreview() {
        return enableAltShiftPreview.get();
    }

    public void setEnableAltShiftPreview(boolean enable) {
        enableAltShiftPreview.set(enable);
    }

    public boolean isShowEnglishSubtitle() {
        return showEnglishSubtitle.get();
    }

    public void setShowEnglishSubtitle(boolean show) {
        showEnglishSubtitle.set(show);
    }

    public boolean isUntranslatedCapture() {
        return untranslatedCapture.get();
    }

    public void setUntranslatedCapture(boolean capture) {
        untranslatedCapture.set(capture);
    }

    public Path getConfigPath() {
        return configPath;
    }

    public void setConfigPath(Path path) {
        this.configPath = path;
    }

    /**
     * Loads configuration from the configured file path or default if missing.
     */
    public synchronized void load() {
        loadFrom(this.configPath);
    }

    /**
     * Loads configuration from a specified Path.
     */
    public synchronized void loadFrom(Path path) {
        if (path == null) return;
        this.configPath = path;
        File file = path.toFile();
        if (!file.exists()) {
            saveTo(path);
            return;
        }

        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            if (root.has("enableAltShiftPreview")) {
                this.enableAltShiftPreview.set(root.get("enableAltShiftPreview").getAsBoolean());
            }
            if (root.has("showEnglishSubtitle")) {
                this.showEnglishSubtitle.set(root.get("showEnglishSubtitle").getAsBoolean());
            }
            if (root.has("untranslatedCapture")) {
                this.untranslatedCapture.set(root.get("untranslatedCapture").getAsBoolean());
            }

            if (root.has("categories") && root.get("categories").isJsonObject()) {
                JsonObject cats = root.getAsJsonObject("categories");
                for (ComponentCategory cat : ComponentCategory.values()) {
                    if (cats.has(cat.name())) {
                        try {
                            InterceptionMode mode = InterceptionMode.valueOf(cats.get(cat.name()).getAsString());
                            this.categoryModes.put(cat, mode);
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ModConfig] Failed to load config from " + path + ": " + e.getMessage());
        }
    }

    /**
     * Saves configuration to the configured file path.
     */
    public synchronized void save() {
        saveTo(this.configPath);
    }

    /**
     * Saves configuration to a specified Path.
     */
    public synchronized void saveTo(Path path) {
        if (path == null) return;
        this.configPath = path;
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            JsonObject root = new JsonObject();
            root.addProperty("enableAltShiftPreview", this.enableAltShiftPreview.get());
            root.addProperty("showEnglishSubtitle", this.showEnglishSubtitle.get());
            root.addProperty("untranslatedCapture", this.untranslatedCapture.get());

            JsonObject cats = new JsonObject();
            for (ComponentCategory cat : ComponentCategory.values()) {
                cats.addProperty(cat.name(), getInterceptionMode(cat).name());
            }
            root.add("categories", cats);

            try (Writer writer = new FileWriter(path.toFile(), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            System.err.println("[ModConfig] Failed to save config to " + path + ": " + e.getMessage());
        }
    }
}
