package com.hypixel.translation;

import com.hypixel.translation.command.ClientCommandsHandler;
import com.hypixel.translation.core.TranslationCore;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HypixelSkyblockTranslationMod implements ModInitializer {
    public static final String MOD_ID = "hypixel_skyblock_translation";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[HypixelSkyblockTranslation] Initializing mod for Minecraft 26.2 (Fabric Loader 0.20.1, Java 25)...");
        TranslationCore.initialize();
        TranslationCore core = TranslationCore.getInstance();

        ScheduledExecutorService captureFlushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HSBTR-Capture-Flush");
            t.setDaemon(true);
            return t;
        });
        captureFlushScheduler.scheduleAtFixedRate(() -> {
            try {
                if (core.getConfig() != null && core.getConfig().isUntranslatedCapture()) {
                    core.flushUntranslatedQueue();
                }
            } catch (Throwable ignored) {}
        }, 60, 60, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                captureFlushScheduler.shutdownNow();
            } catch (Throwable ignored) {}
            try {
                if (core.getConfig() != null && core.getConfig().isUntranslatedCapture()) {
                    core.flushUntranslatedQueue();
                }
            } catch (Throwable t) {
                LOGGER.warn("[HypixelSkyblockTranslation] Error flushing untranslated capture queue: {}", t.getMessage());
            }
        }, "HSBTR-Shutdown"));

        ClientCommandsHandler.register();
        LOGGER.info("[HypixelSkyblockTranslation] Initialization completed.");
    }
}
