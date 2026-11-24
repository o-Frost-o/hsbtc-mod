package com.hypixel.translation.interception;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.config.InterceptionMode;
import com.hypixel.translation.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;

import java.util.Locale;

/**
 * Manages the routing decision for interception across all UI, HUD, and packet categories.
 * Queries ModConfig to determine if DISPLAY or PACKET level interception is active.
 */
public class InterceptionManager {

    private static volatile InterceptionManager INSTANCE = new InterceptionManager();

    public static InterceptionManager getInstance() {
        return INSTANCE;
    }

    public static void setInstance(InterceptionManager manager) {
        INSTANCE = manager;
    }

    private final ModConfig config;

    public InterceptionManager() {
        this(ModConfig.getInstance());
    }

    public InterceptionManager(ModConfig config) {
        this.config = (config != null) ? config : ModConfig.getInstance();
    }

    public ModConfig getConfig() {
        return config;
    }

    public InterceptionMode getMode(ComponentCategory category) {
        return config.getInterceptionMode(category);
    }

    public void setMode(ComponentCategory category, InterceptionMode mode) {
        config.setInterceptionMode(category, mode);
    }

    public static boolean isSkyBlock() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return false;
        var sidebar = client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        return sidebar != null && sidebar.getDisplayName().getString().toUpperCase(Locale.ROOT).contains("SKYBLOCK");
    }

    public boolean shouldInterceptDisplay(ComponentCategory category) {
        return getMode(category) == InterceptionMode.DISPLAY;
    }

    public boolean shouldInterceptPacket(ComponentCategory category) {
        return getMode(category) == InterceptionMode.PACKET;
    }
}
