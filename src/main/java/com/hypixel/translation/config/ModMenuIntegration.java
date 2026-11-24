package com.hypixel.translation.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.network.chat.Component;

/**
 * ModMenu integration entrypoint.
 * Safely delegates screen creation to ClothConfigScreenHelper when Cloth Config is present,
 * or displays a vanilla AlertScreen fallback when Cloth Config is missing.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            if (FabricLoader.getInstance().isModLoaded("cloth-config")) {
                return ClothConfigScreenHelper.buildScreen(parent);
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.font == null) {
                return new ClothConfigScreenHelper.HeadlessFallbackScreen(Component.literal("Hypixel Skyblock Translation"));
            }
            return new AlertScreen(
                    () -> Minecraft.getInstance().setScreenAndShow(parent),
                    Component.literal("Hypixel SkyBlock 繁體中文模組"),
                    Component.literal("需要安裝 Cloth Config 模組以開啟設定介面。\n您也可以在遊戲內輸入 /hsbtr ui 開啟線上翻譯網站。")
            );
        };
    }
}
