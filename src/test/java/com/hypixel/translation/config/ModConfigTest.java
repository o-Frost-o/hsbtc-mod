package com.hypixel.translation.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("config")
@Tag("component")
public class ModConfigTest {

    @TempDir
    public Path tempDir;

    @Test
    @DisplayName("Verify default 9 categories initialize to DISPLAY mode")
    void testDefaultCategoryModes() {
        ModConfig config = new ModConfig();
        for (ComponentCategory cat : ComponentCategory.values()) {
            assertEquals(InterceptionMode.DISPLAY, config.getInterceptionMode(cat),
                    "Category " + cat + " must default to DISPLAY mode");
        }
    }

    @Test
    @DisplayName("Verify default configuration settings flags")
    void testDefaultSettingsFlags() {
        ModConfig config = new ModConfig();
        assertTrue(config.isEnableAltShiftPreview());
        assertFalse(config.isShowEnglishSubtitle());
        assertTrue(config.isUntranslatedCapture());
    }

    @Test
    @DisplayName("Verify JSON save and load roundtrip fidelity")
    void testSaveAndLoadRoundtrip() {
        Path cfgPath = tempDir.resolve("config.json");
        ModConfig config = new ModConfig();
        config.setInterceptionMode(ComponentCategory.CHAT, InterceptionMode.PACKET);
        config.setInterceptionMode(ComponentCategory.SCOREBOARD, InterceptionMode.DISABLED);
        config.setEnableAltShiftPreview(false);
        config.setShowEnglishSubtitle(true);
        config.setUntranslatedCapture(false);

        config.saveTo(cfgPath);
        assertTrue(cfgPath.toFile().exists());

        ModConfig loaded = new ModConfig();
        loaded.loadFrom(cfgPath);

        assertEquals(InterceptionMode.PACKET, loaded.getInterceptionMode(ComponentCategory.CHAT));
        assertEquals(InterceptionMode.DISABLED, loaded.getInterceptionMode(ComponentCategory.SCOREBOARD));
        assertEquals(InterceptionMode.DISPLAY, loaded.getInterceptionMode(ComponentCategory.TOOLTIPS));
        assertFalse(loaded.isEnableAltShiftPreview());
        assertTrue(loaded.isShowEnglishSubtitle());
        assertFalse(loaded.isUntranslatedCapture());
    }
}
