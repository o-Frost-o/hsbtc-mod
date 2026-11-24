package com.hypixel.translation.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("config")
@Tag("component")
public class ModMenuIntegrationTest {

    @Test
    @DisplayName("Verify ModMenuIntegration implements ModMenuApi and returns valid screen factory")
    void testModMenuIntegrationFactory() {
        ModMenuIntegration integration = new ModMenuIntegration();
        assertInstanceOf(ModMenuApi.class, integration);

        ConfigScreenFactory<?> factory = integration.getModConfigScreenFactory();
        assertNotNull(factory, "ConfigScreenFactory must not be null");
    }

    @Test
    @DisplayName("Verify ClothConfigScreenHelper builds valid configuration screen")
    void testClothConfigScreenHelper() {
        ModConfig.getInstance().setEnableAltShiftPreview(true);
        ModConfig.getInstance().setShowEnglishSubtitle(false);
        ModConfig.getInstance().setUntranslatedCapture(true);

        Screen screen = ClothConfigScreenHelper.buildScreen(null);
        assertNotNull(screen, "ClothConfigScreenHelper.buildScreen must return a non-null Screen");
        assertEquals("Hypixel SkyBlock 繁體中文模組設定", screen.getTitle().getString());
    }

    @Test
    @DisplayName("Verify ModMenu screen factory create(null) executes safely in headless environment without NPE")
    void testModMenuScreenFactoryHeadlessSafety() {
        ModMenuIntegration integration = new ModMenuIntegration();
        ConfigScreenFactory<?> factory = integration.getModConfigScreenFactory();
        assertNotNull(factory);

        Screen screen = factory.create(null);
        assertNotNull(screen, "Created screen must not be null in headless environment");
        assertNotNull(screen.getTitle(), "Screen title must not be null");
    }
}
