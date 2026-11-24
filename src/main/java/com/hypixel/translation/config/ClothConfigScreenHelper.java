package com.hypixel.translation.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import java.util.Optional;

/**
 * Isolated helper class that builds the Cloth Config settings screen.
 * Isolating this class prevents NoClassDefFoundError if cloth-config is missing at runtime.
 */
public class ClothConfigScreenHelper {

    public static class HeadlessFallbackScreen extends Screen {
        public HeadlessFallbackScreen(Component title) {
            super(null, null, title);
        }
    }

    public static Screen buildScreen(Screen parent) {
        ModConfig config = ModConfig.getInstance();

        Component title = Component.literal("Hypixel SkyBlock 繁體中文模組設定");
        // Safe guard for headless / unit test environment where Minecraft instance or font is uninitialized
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return new HeadlessFallbackScreen(title);
        }

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(title);

        builder.setSavingRunnable(config::save);

        ConfigEntryBuilder eb = builder.entryBuilder();

        // Category 1: 一般設定 (General Settings)
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("一般設定"));

        general.addEntry(eb.startBooleanToggle(
                Component.literal("Alt/Shift 熱鍵切換英文原文"),
                config.isEnableAltShiftPreview())
                .setDefaultValue(true)
                .setTooltip(Component.literal("按住 Alt 或 Shift 鍵時暫時顯示英文原文 Tooltip；在聊天室按住 Alt 可查看 NPC 對話英文原文"))
                .setSaveConsumer(config::setEnableAltShiftPreview)
                .build());

        general.addEntry(eb.startBooleanToggle(
                Component.literal("Tooltip 底部附註英文原名"),
                config.isShowEnglishSubtitle())
                .setDefaultValue(false)
                .setTooltip(Component.literal("在道具 Tooltip 底部以灰色附註英文原始名稱"))
                .setSaveConsumer(config::setShowEnglishSubtitle)
                .build());

        general.addEntry(eb.startBooleanToggle(
                Component.literal("未翻譯文本自動擷取"),
                config.isUntranslatedCapture())
                .setDefaultValue(true)
                .setTooltip(Component.literal("自動收集遊戲中未收錄之文本以利後續翻譯"))
                .setSaveConsumer(config::setUntranslatedCapture)
                .build());

        // Category 2: 元件攔截模式 (Component Interception Modes)
        ConfigCategory interception = builder.getOrCreateCategory(Component.literal("元件攔截模式"));
        interception.setDescription(() -> Optional.of(new FormattedText[]{
                Component.literal("可針對各 UI 元件切換 DISPLAY（渲染層攔截，與 Skyblocker 完全相容）、PACKET（封包層）或 DISABLED（停用）")
        }));

        for (ComponentCategory cat : ComponentCategory.values()) {
            interception.addEntry(eb.startEnumSelector(
                    Component.literal(getCategoryDisplayName(cat)),
                    InterceptionMode.class,
                    config.getInterceptionMode(cat))
                    .setDefaultValue(InterceptionMode.DISPLAY)
                    .setTooltip(Component.literal("選擇 " + cat.name() + " 的文字攔截模式"))
                    .setEnumNameProvider(mode -> Component.literal(mode.name()))
                    .setSaveConsumer(mode -> config.setInterceptionMode(cat, mode))
                    .build());
        }

        return builder.build();
    }

    private static String getCategoryDisplayName(ComponentCategory cat) {
        return switch (cat) {
            case TOOLTIPS -> "道具提示框 (Tooltips)";
            case CONTAINER_UI -> "容器介面選單 (Container UI)";
            case SCOREBOARD -> "右側計分板 (Scoreboard)";
            case TAB_LIST -> "玩家列表 (Tab List)";
            case CHAT -> "聊天對話 (Chat)";
            case ACTION_BAR -> "快捷提示條 (ActionBar)";
            case BOSS_BAR -> "首領血條 (BossBar)";
            case TITLE_SUBTITLE -> "標題與副標題 (Title/Subtitle)";
            case ENTITY_TAGS -> "實體全息標籤 (Entity Hologram Tags)";
        };
    }
}
