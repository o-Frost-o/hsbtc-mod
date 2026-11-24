package com.hypixel.translation.interception;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.config.InterceptionMode;
import com.hypixel.translation.config.ModConfig;
import com.hypixel.translation.core.DialogueDictionary;
import com.hypixel.translation.core.ItemDictionary;
import com.hypixel.translation.core.TokenRecord;
import com.hypixel.translation.core.TranslationCore;
import com.hypixel.translation.core.UntranslatedCaptureQueue;
import com.hypixel.translation.e2e.BaseE2ETest;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Tag("interception")
@Tag("component")
public class InterceptionTranslatorsTest extends BaseE2ETest {

    @Test
    @DisplayName("Verify TooltipTranslator alt/shift preview toggle and subtitle")
    void testTooltipTranslatorPreviewAndSubtitle() {
        ModConfig config = ModConfig.getInstance();
        config.setEnableAltShiftPreview(true);
        config.setShowEnglishSubtitle(false);

        List<Component> originalTooltip = List.of(
                Component.literal("§dHeroic Hyperion ✪✪✪✪✪"),
                Component.literal("§7Damage: §c+260")
        );

        // When Alt/Shift is NOT down -> Translated
        List<Component> normal = TooltipTranslator.translateItemTooltip(null, originalTooltip, false);
        assertEquals(2, normal.size());
        assertTrue(normal.get(0).getString().contains("英雄") || normal.get(0).getString().contains("亥伯龍"));

        // When Alt/Shift IS down -> Reverts to original English
        List<Component> preview = TooltipTranslator.translateItemTooltip(null, originalTooltip, true);
        assertEquals(2, preview.size());
        assertEquals("§dHeroic Hyperion ✪✪✪✪✪", preview.get(0).getString());
        assertEquals("§7Damage: §c+260", preview.get(1).getString());

        // When showEnglishSubtitle is enabled
        config.setShowEnglishSubtitle(true);
        // Note: passing null stack skips item subtitle, but testing with simulated stack logic
        List<Component> normalSub = TooltipTranslator.translateItemTooltip(null, originalTooltip, false);
        assertNotNull(normalSub);
        config.setShowEnglishSubtitle(false);
    }

    @Test
    @DisplayName("Verify ChatTranslator Alt-key original English preview for NPC dialogues and interactive options")
    void testChatTranslatorNpcAltPreview() {
        ModConfig config = ModConfig.getInstance();
        boolean prevPreview = config.isEnableAltShiftPreview();
        config.setEnableAltShiftPreview(true);
        try {
            com.hypixel.translation.core.TranslationCore core = com.hypixel.translation.core.TranslationCore.getInstance();
            DialogueDictionary diag = core.getDialogueDictionary();
            diag.registerDialogue("[NPC] Adventurer Saul: So, have you defeated The Professor yet?",
                    "[NPC] 冒險家索爾（Adventurer Saul）：那麼，你打敗教授了嗎？");
            diag.registerDialogue("[BOSS] Bonzo: Welcome to my arena!",
                    "[BOSS] 邦佐（Bonzo）：歡迎來到我的競技場！");
            diag.registerDialogue("Sending to server mini123A...",
                    "正在傳送至伺服器 mini123A...");
            diag.registerDialogue("[Yes, I am ready!]",
                    "[是的，我準備好了！]");

            Component npc = Component.literal("§e[NPC] §fAdventurer Saul§f: So, have you defeated The Professor yet?");
            Component boss = Component.literal("§c[BOSS] Bonzo§f: Welcome to my arena!");
            Component option = Component.literal("§a[Yes, I am ready!]").setStyle(
                    Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/selectnpcoption 1"))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to reply")))
            );
            Component serverNotice = Component.literal("§7Sending to server mini123A...");
            Component playerChat = Component.literal("§b[MVP+] SomePlayer§f: hello!");

            assertTrue(ChatTranslator.isNpcDialogue(npc));
            assertTrue(ChatTranslator.isNpcDialogue(boss));
            assertTrue(ChatTranslator.isNpcDialogue(option));
            assertFalse(ChatTranslator.isNpcDialogue(serverNotice));
            assertFalse(ChatTranslator.isNpcDialogue(playerChat));

            // Alt NOT pressed -> NPC & BOSS & option & notice are translated
            assertTrue(ChatTranslator.translateChatMessage(npc, false).getString().contains("冒險家索爾"));
            assertTrue(ChatTranslator.translateChatMessage(boss, false).getString().contains("邦佐"));
            assertEquals("§a[是的，我準備好了！]", ChatTranslator.translateChatMessage(option, false).getString());
            assertEquals("§7正在傳送至伺服器 mini123A...", ChatTranslator.translateChatMessage(serverNotice, false).getString());

            // Alt pressed -> NPC, BOSS, and NPC options revert to original English (with ClickEvent/Style intact);
            // non-NPC system notices remain translated.
            Component npcAlt = ChatTranslator.translateChatMessage(npc, true);
            assertEquals(npc.getString(), npcAlt.getString());
            Component bossAlt = ChatTranslator.translateChatMessage(boss, true);
            assertEquals(boss.getString(), bossAlt.getString());
            Component optionAlt = ChatTranslator.translateChatMessage(option, true);
            assertEquals(option.getString(), optionAlt.getString());
            assertInstanceOf(ClickEvent.RunCommand.class, optionAlt.getStyle().getClickEvent());
            assertEquals("§7正在傳送至伺服器 mini123A...", ChatTranslator.translateChatMessage(serverNotice, true).getString());

            // Disabled Alt preview setting -> keeps translated text even when altDown=true
            config.setEnableAltShiftPreview(false);
            assertTrue(ChatTranslator.translateChatMessage(npc, true).getString().contains("冒險家索爾"));
        } finally {
            config.setEnableAltShiftPreview(prevPreview);
        }
    }

    @Test
    @DisplayName("Verify TooltipTranslator generic tooltip translation")
    void testGenericTooltipTranslation() {
        List<Component> generic = List.of(
                Component.literal("Damage"),
                Component.literal("Strength")
        );
        List<Component> translated = TooltipTranslator.translateGenericTooltip(generic);
        assertEquals(2, translated.size());
        assertEquals("傷害", translated.get(0).getString());
        assertEquals("力量", translated.get(1).getString());
    }

    @Test
    @DisplayName("Verify ScoreboardTranslator translation")
    void testScoreboardTranslator() {
        Component objective = Component.literal("SKYBLOCK");
        Component translated = ScoreboardTranslator.translate(objective);
        assertNotNull(translated);

        String line = ScoreboardTranslator.translateText("Damage");
        assertEquals("傷害", line);
    }

    @Test
    @DisplayName("Verify HologramTranslator NPC and click tags")
    void testHologramTranslator() {
        Component npcTag = Component.literal("§e[NPC] §aElle §e: §bCLICK");
        Component translated = HologramTranslator.translate(npcTag, null);
        assertNotNull(translated);
        assertTrue(translated.getString().contains("點擊"));
    }

    @Test
    @DisplayName("Verify InterceptionManager routing queries")
    void testInterceptionManagerRouting() {
        com.hypixel.translation.interception.InterceptionManager manager = com.hypixel.translation.interception.InterceptionManager.getInstance();
        manager.setMode(ComponentCategory.TOOLTIPS, InterceptionMode.DISPLAY);
        assertTrue(manager.shouldInterceptDisplay(ComponentCategory.TOOLTIPS));
        assertFalse(manager.shouldInterceptPacket(ComponentCategory.TOOLTIPS));

        manager.setMode(ComponentCategory.CHAT, InterceptionMode.PACKET);
        assertFalse(manager.shouldInterceptDisplay(ComponentCategory.CHAT));
        assertTrue(manager.shouldInterceptPacket(ComponentCategory.CHAT));

        manager.setMode(ComponentCategory.SCOREBOARD, InterceptionMode.DISABLED);
        assertFalse(manager.shouldInterceptDisplay(ComponentCategory.SCOREBOARD));
        assertFalse(manager.shouldInterceptPacket(ComponentCategory.SCOREBOARD));

        // Restore defaults
        manager.setMode(ComponentCategory.TOOLTIPS, InterceptionMode.DISPLAY);
        manager.setMode(ComponentCategory.CHAT, InterceptionMode.DISPLAY);
        manager.setMode(ComponentCategory.SCOREBOARD, InterceptionMode.DISPLAY);
    }

    private static List<Style> collectCharStyles(Component comp) {
        List<Style> styles = new ArrayList<>();
        comp.visit((style, text) -> {
            if (text != null) {
                for (int i = 0; i < text.length(); i++) {
                    styles.add(style);
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
        return styles;
    }

    @Test
    @DisplayName("Verify ScoreboardTranslator reassembles Hypixel team split with legacy § codes and dummy §h entry")
    void testScoreboardHypixelTeamSplitWithLegacyCodesAndDummyEntry() {
        tokenDictionary.registerToken(new TokenRecord("LOC_YOUR_ISLAND", "Your Island", "你的島嶼", "", "§a", "location"));

        Component split = Component.empty()
                .append(Component.literal(" §7\ue067 §aYour Isla"))
                .append(Component.literal("§h"))
                .append(Component.literal("§and"));

        Component translated = ScoreboardTranslator.translate(split, tokenDictionary, null);
        assertEquals(" \ue067 你的島嶼", translated.getString());
        List<Style> styles = collectCharStyles(translated);
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GRAY), styles.get(1).getColor());
        int idxIsland = translated.getString().indexOf("你的島嶼");
        assertTrue(idxIsland >= 0);
        for (int i = idxIsland; i < idxIsland + "你的島嶼".length(); i++) {
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), styles.get(i).getColor());
        }
    }

    @Test
    @DisplayName("Verify ScoreboardTranslator animated title styled siblings and RGB TextColor preservation")
    void testScoreboardAnimatedTitleStyledSiblingsAndRgbPreservation() {
        Component animatedTitle = Component.empty()
                .append(Component.literal("SKYBL").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("O").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal("CK").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        Component translatedTitle = ScoreboardTranslator.translate(animatedTitle, tokenDictionary, null);
        assertEquals("空島生存", translatedTitle.getString());
        List<Style> titleStyles = collectCharStyles(translatedTitle);
        for (Style s : titleStyles) {
            assertTrue(s.isBold());
            assertTrue(TextColor.fromLegacyFormat(ChatFormatting.GOLD).equals(s.getColor())
                    || TextColor.fromLegacyFormat(ChatFormatting.YELLOW).equals(s.getColor()));
        }

        tokenDictionary.registerToken(new TokenRecord("LOC_YOUR_ISLAND", "Your Island", "你的島嶼", "", "§a", "location"));
        TextColor greenRgb = TextColor.fromRgb(0x55FF55);
        TextColor orangeRgb = TextColor.fromRgb(0xFFAA00);
        Component rgbSplit = Component.empty()
                .append(Component.literal("Your ").setStyle(Style.EMPTY.withColor(greenRgb)))
                .append(Component.literal("Island").setStyle(Style.EMPTY.withColor(orangeRgb)));

        Component translatedRgb = ScoreboardTranslator.translate(rgbSplit, tokenDictionary, null);
        assertEquals("你的島嶼", translatedRgb.getString());
        List<Style> rgbStyles = collectCharStyles(translatedRgb);
        assertEquals(greenRgb, rgbStyles.get(0).getColor());
        assertEquals(orangeRgb, rgbStyles.get(rgbStyles.size() - 1).getColor());
    }

    @Test
    @DisplayName("Verify ScoreboardTranslator numeric anchor preservation and DialogueDictionary match")
    void testScoreboardNumericAnchorPreservationAndDialogueMatch() {
        Component purse = Component.empty()
                .append(Component.literal("Purse: ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("1,234").withStyle(ChatFormatting.GOLD));
        Component translatedPurse = ScoreboardTranslator.translate(purse, tokenDictionary, null);
        assertEquals("錢包: 1,234", translatedPurse.getString());
        List<Style> purseStyles = collectCharStyles(translatedPurse);
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.WHITE), purseStyles.get(0).getColor());
        int numIdx = translatedPurse.getString().indexOf("1,234");
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), purseStyles.get(numIdx).getColor());

        DialogueDictionary diag = new DialogueDictionary(tokenDictionary, new ItemDictionary());
        diag.loadFromJson("""
                {
                  "dialogues": {
                    "Talk to Calvin": "與凱文交談"
                  }
                }
                """);
        Component talk = Component.empty()
                .append(Component.literal("Talk to ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("Calvin").withStyle(ChatFormatting.YELLOW));
        Component translatedTalk = ScoreboardTranslator.translate(talk, tokenDictionary, diag);
        assertEquals("與凱文交談", translatedTalk.getString());
        for (Style s : collectCharStyles(translatedTalk)) {
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.YELLOW), s.getColor());
        }

        // Regression C4: colon anchor + forward-contiguous space tie-break preserves GOLD on "目標：" and YELLOW on "與 NPC 對話"
        diag.loadFromJson("""
                {
                  "dialogues": {
                    "Objective: Talk to the NPC": "目標：與 NPC 對話"
                  }
                }
                """);
        Component objectiveNpc = Component.empty()
                .append(Component.literal("Objective: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("Talk to the NPC").withStyle(ChatFormatting.YELLOW));
        Component translatedNpc = ScoreboardTranslator.translate(objectiveNpc, tokenDictionary, diag);
        assertEquals("目標：與 NPC 對話", translatedNpc.getString());
        List<Style> npcStyles = collectCharStyles(translatedNpc);
        TextColor gold = TextColor.fromLegacyFormat(ChatFormatting.GOLD);
        TextColor yellow = TextColor.fromLegacyFormat(ChatFormatting.YELLOW);
        for (int idx = 0; idx < "目標：".length(); idx++) {
            assertEquals(gold, npcStyles.get(idx).getColor(), "Expected GOLD at index " + idx);
        }
        for (int idx = "目標：".length(); idx < translatedNpc.getString().length(); idx++) {
            assertEquals(yellow, npcStyles.get(idx).getColor(), "Expected YELLOW at index " + idx);
        }
    }

    @Test
    @DisplayName("Verify ScoreboardTranslator edge cases and junk capture suppression")
    void testScoreboardEdgeCasesAndJunkCaptureSuppression() {
        assertNull(ScoreboardTranslator.translate(null));
        assertEquals("", ScoreboardTranslator.translate(Component.empty()).getString());
        assertEquals("   ", ScoreboardTranslator.translate(Component.literal("   ")).getString());
        assertEquals("§h", ScoreboardTranslator.translate(Component.literal("§h")).getString());
        assertEquals("X", ScoreboardTranslator.translate(Component.literal("X")).getString());

        assertFalse(TranslationCore.shouldCaptureUntranslated("§h", ComponentCategory.SCOREBOARD));
        assertFalse(TranslationCore.shouldCaptureUntranslated("11/18/26 m24AN", ComponentCategory.SCOREBOARD));
        assertFalse(TranslationCore.shouldCaptureUntranslated("11/18/26  L21G", ComponentCategory.SCOREBOARD));
        assertFalse(TranslationCore.shouldCaptureUntranslated("www.hypixel.net", ComponentCategory.SCOREBOARD));
        assertFalse(TranslationCore.shouldCaptureUntranslated(" §72:00pm", ComponentCategory.SCOREBOARD));
        assertTrue(TranslationCore.shouldCaptureUntranslated("Mysterious Unknown Line", ComponentCategory.SCOREBOARD));
    }

    @Test
    @DisplayName("Verify TranslationCore UI categories Dialogue routing, false-positive guards, and Style/Event preservation")
    void testTranslationCoreUiCategoriesDialogueRoutingAndFalsePositiveGuards() {
        tokenDictionary.registerToken(new TokenRecord("UI_BESTIARY", "Bestiary", "圖鑑", "", "", "ui"));
        tokenDictionary.registerToken(new TokenRecord("UI_BINGO", "Bingo", "賓果模式", "", "", "ui"));

        DialogueDictionary diag = new DialogueDictionary(tokenDictionary, new ItemDictionary());
        diag.loadFromJson("""
                {
                  "dialogues": {
                    "An": "安",
                    "Sam": "山姆",
                    "Bestiary": "圖鑑管理員",
                    "Bingo": "賓果指導員",
                    "Pinging...": "正在偵測延遲...",
                    "Click to open the Hypixel Leveling menu": "點擊開啟 Hypixel 等級選單"
                  },
                  "patterns": [
                    {"regex": "^Sending to server (.+?)\\\\.\\\\.\\\\.$", "zh_tw": "正在傳送至伺服器 $1..."}
                  ]
                }
                """);

        com.hypixel.translation.core.TranslationCore core = new com.hypixel.translation.core.TranslationCore(
                tokenDictionary,
                new ItemDictionary(),
                diag,
                dynamicItemCombiner,
                loreParser,
                new UntranslatedCaptureQueue(100),
                interceptionManager,
                textTraverser,
                ModConfig.getInstance()
        );

        for (ComponentCategory cat : List.of(
                ComponentCategory.TOOLTIPS,
                ComponentCategory.CONTAINER_UI,
                ComponentCategory.TAB_LIST,
                ComponentCategory.SCOREBOARD
        )) {
            assertEquals("正在偵測延遲...", core.translateAndCapture("Pinging...", cat));
            assertEquals("正在傳送至伺服器 mini123A...", core.translateAndCapture("Sending to server mini123A...", cat));
            assertEquals("點擊開啟 Hypixel 等級選單",
                    core.translateAndCapture("Click to open the §3Hypixel Leveling §emenu", cat));
            assertEquals("圖鑑", core.translateAndCapture("Bestiary", cat));
            assertEquals("賓果模式", core.translateAndCapture("Bingo", cat));
            assertEquals("§8AN", core.translateAndCapture("§8AN", cat));
            assertEquals("an", core.translateAndCapture("an", cat));
            assertEquals("Sam", core.translateAndCapture("Sam", cat));
            assertEquals("[SAM]", core.translateAndCapture("[SAM]", cat));
            assertEquals("12345", core.translateAndCapture("12345", cat));
        }

        // CHAT continues using full DialogueDictionary.translate (translating NPC Sam)
        assertEquals("[NPC] 山姆: Hello", core.translateAndCapture("[NPC] Sam: Hello", ComponentCategory.CHAT));

        // TextTraverser via TranslationCore preserves Style, TextColor, ClickEvent, HoverEvent
        TextColor rgb = TextColor.fromRgb(0x33CC99);
        ClickEvent click = new ClickEvent.RunCommand("/warp hub");
        HoverEvent hover = new HoverEvent.ShowText(Component.literal("Damage").withStyle(ChatFormatting.RED));
        Component styled = Component.literal("Pinging...")
                .setStyle(Style.EMPTY.withColor(rgb).withClickEvent(click).withHoverEvent(hover));
        Component traversed = core.getTextTraverser().translateRecursive(
                styled, s -> core.translateAndCapture(s, ComponentCategory.TOOLTIPS));
        assertEquals("正在偵測延遲...", traversed.getString());
        assertEquals(rgb, traversed.getStyle().getColor());
        assertEquals(click, traversed.getStyle().getClickEvent());
        assertInstanceOf(HoverEvent.ShowText.class, traversed.getStyle().getHoverEvent());
        assertEquals("傷害", ((HoverEvent.ShowText) traversed.getStyle().getHoverEvent()).value().getString());
    }
}
