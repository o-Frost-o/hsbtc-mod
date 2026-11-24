package com.hypixel.translation.command;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.config.InterceptionMode;
import com.hypixel.translation.config.ModConfig;
import com.hypixel.translation.core.TranslationCore;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;

/**
 * In-game client command handler registered under /hsbtr.
 * Provides /hsbtr ui (and /hsbtr web), /hsbtr reload, and /hsbtr status subcommands.
 */
public class ClientCommandsHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("HypixelSkyblockTranslation");
    public static final String WEB_TRANSLATION_URL = "https://o-frost-o.github.io/hsbtc-mod/";

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            registerCommands(dispatcher);
        });
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommands.literal("hsbtr")
                .then(ClientCommands.literal("ui").executes(ctx -> executeOpenUi(ctx.getSource())))
                .then(ClientCommands.literal("web").executes(ctx -> executeOpenUi(ctx.getSource())))
                .then(ClientCommands.literal("reload").executes(ctx -> executeReload(ctx.getSource())))
                .then(ClientCommands.literal("status").executes(ctx -> executeStatus(ctx.getSource())))
                .executes(ctx -> executeStatus(ctx.getSource()))
        );
    }

    public static int executeOpenUi(FabricClientCommandSource source) {
        URI uri = URI.create(WEB_TRANSLATION_URL);

        boolean opened = false;
        try {
            Util.getPlatform().openUri(uri);
            opened = true;
        } catch (Throwable t1) {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(uri);
                    opened = true;
                }
            } catch (Throwable ignored) {}
        }

        MutableComponent link = Component.literal(WEB_TRANSLATION_URL).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(uri))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§e點擊於預設瀏覽器中開啟線上翻譯網站")))
        );

        MutableComponent feedback = Component.literal("§a[HSBTR] " + (opened ? "已在預設瀏覽器開啟線上翻譯網站：" : "請點擊連結開啟線上翻譯網站："))
                .append(link);

        source.sendFeedback(feedback);
        return Command.SINGLE_SUCCESS;
    }

    public static int executeReload(FabricClientCommandSource source) {
        TranslationCore core = TranslationCore.getInstance();
        core.reload();
        int count = core.getReloadCount();

        source.sendFeedback(Component.literal("§a[HSBTR] 繁體中文翻譯詞庫與設定檔熱重載完成！（累計第 §e" + count + " §a次）"));
        return Command.SINGLE_SUCCESS;
    }

    public static int executeStatus(FabricClientCommandSource source) {
        TranslationCore core = TranslationCore.getInstance();
        ModConfig config = ModConfig.getInstance();

        int tokenCount = core.getTokenDictionary().size();
        int itemCount = core.getItemDictionary().size();
        int untranslatedCount = core.getUntranslatedQueue().size();
        int reloadCount = core.getReloadCount();

        source.sendFeedback(Component.literal("§6====== [ Hypixel SkyBlock 繁體中文模組 (HSBTR) ] ======"));
        source.sendFeedback(Component.literal("§7模組版本: §f1.0.0 (Minecraft 26.2 Fabric)"));
        source.sendFeedback(Component.literal("§7線上翻譯網站: §b" + WEB_TRANSLATION_URL));
        source.sendFeedback(Component.literal("§7詞彙對照表條數 (Tokens): §a" + tokenCount + " §7筆"));
        source.sendFeedback(Component.literal("§7道具庫項目數 (Items): §a" + itemCount + " §7筆"));
        source.sendFeedback(Component.literal("§7未翻譯擷取佇列 (Untranslated): §e" + untranslatedCount + " §7筆"));
        source.sendFeedback(Component.literal("§7累計熱重載次數: §b" + reloadCount + " §7次"));

        source.sendFeedback(Component.literal("§6--- 元件文字攔截模式 ---"));
        for (ComponentCategory cat : ComponentCategory.values()) {
            InterceptionMode mode = config.getInterceptionMode(cat);
            String modeStr = switch (mode) {
                case DISPLAY -> "§aDISPLAY (渲染層非破壞性)";
                case PACKET -> "§ePACKET (封包層)";
                case DISABLED -> "§cDISABLED (停用)";
            };
            source.sendFeedback(Component.literal("  §7- " + cat.name() + ": " + modeStr));
        }
        source.sendFeedback(Component.literal("§6=================================================="));

        return Command.SINGLE_SUCCESS;
    }
}
