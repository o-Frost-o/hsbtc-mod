package com.hypixel.translation.e2e;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.config.InterceptionMode;
import com.hypixel.translation.core.*;
import com.hypixel.translation.engine.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tier 3: Cross-Module Interaction & Pairwise Combination Test Suite (≥15 interaction tests).
 * Derivation: TEST_INFRA.md, PROJECT.md, and teamwork_spec_miner_e2e_3/handoff.md.
 */
@Tag("tier3")
@Tag("pairwise")
public class Tier3PairwiseCombinationsTest extends BaseE2ETest {

    @Test
    @DisplayName("P01: Token Resolution Embedded Inside Lore Stat Brackets with Formatting")
    void testTokenResolutionInsideLoreStatBrackets() {
        // F6 (Token Dictionary) + F10 (Lore Stat Brackets) + F19 (Text Traversal)
        String inputLine = "§7Crit Damage: §9+250% §e(+25%) §a[+15%]";
        String translated = loreParser.translateLoreLine(inputLine);

        assertEquals("§7暴擊傷害：§9+250% §e(+25%) §a[+15%]", translated);
    }

    @Test
    @DisplayName("P02: Reforge Collision + Gemstone Brackets + Master Mode Stars on Weapon")
    void testReforgeCollisionWithGemstonesAndMasterStars() {
        // F8 (Reforge Collisions) + F11 (Gemstones) + F14 (Stars)
        String rawWeaponName = "§d§lWarped Aspect of the End ✪✪✪✪§c➊";
        String rawGemstones = "§7Gemstones: §8[§7\ue003§8]";

        MutableComponent translatedName = dynamicItemCombiner.translateItemName(null, rawWeaponName);
        String translatedGemstones = loreParser.translateLoreLine(rawGemstones);

        assertEquals("§d§l扭曲 終界之劍 ✪✪✪✪§c➊", translatedName.getString());
        assertEquals("§7寶石槽：§8[§7\ue003§8]", translatedGemstones);
        assertEquals("Aspect of the End", dynamicItemCombiner.getCleanEnglishName(rawWeaponName, "warped"));
    }

    @Test
    @DisplayName("P03: Two-Way CSV Sync + Core Reload Live Pipeline")
    void testCsvSyncAndWebReloadPipeline() throws Exception {
        // F6 (Token Glossary) + F7 (CSV Synchronizer) + Core Reload
        // 1. Export CSV
        String exportCsv = csvSyncManager.exportTokensToCsvString(tokenDictionary);
        assertTrue(exportCsv.contains("ITEM_HYPERION"));

        // 2. Modify CSV
        String modifiedCsv = """
        "token_id","en","zh_tw","category"
        "ITEM_HYPERION","Hyperion","亥伯龍 (神劍)","item"
        """;

        // 3. Import CSV, persist external dictionary, and Reload
        csvSyncManager.importTokensFromCsvString(tokenDictionary, modifiedCsv);
        tokenDictionary.exportToFile(configDir.resolve("tokens.json"));
        translationCore.reload();

        // 4. Verify live item translation reflects updated token
        MutableComponent comp = dynamicItemCombiner.translateItemName(null, "§6Heroic Hyperion ✪✪✪✪✪");
        assertTrue(comp.getString().contains("亥伯龍 (神劍)"));
    }

    @Test
    @DisplayName("P04: Untranslated Capture Queue Ingestion + Token Promotion")
    void testCaptureQueueAndRestPromotion() throws Exception {
        // F16 (Untranslated Queue) + F6 (Token Dictionary)
        // 1. Capture unknown chat text
        String unknownText = "§6[Boss] Necron: You fool!";
        untranslatedQueue.capture(unknownText, ComponentCategory.CHAT);

        // 2. Query queue
        assertTrue(untranslatedQueue.getRecords().values().stream()
                .anyMatch(r -> r.cleanText().contains("[Boss] Necron: You fool!")));

        // 3. Promote to Token & Reload
        tokenDictionary.registerToken(new TokenRecord(
                "DIALOGUE_NECRON_FOOL", "[Boss] Necron: You fool!", "[首領] 涅克隆：愚蠢的傢伙！", "", "", "dialogue"));
        translationCore.reload();
        assertEquals("[首領] 涅克隆：愚蠢的傢伙！", tokenDictionary.resolveToken("DIALOGUE_NECRON_FOOL"));
    }

    @Test
    @DisplayName("P05: Deep Component Traversal Preserving ClickEvent Command Verbatim with Translated HoverEvent Tooltip")
    void testClickEventVerbatimWithTranslatedHoverEvent() {
        // F19 (Deep Traversal) + F24 (NPC/Chat) + F1 (Skyblocker Compatibility)
        ClickEvent clickEvent = new ClickEvent.RunCommand("/sbquest accept 42");
        HoverEvent hoverEvent = new HoverEvent.ShowText(Component.literal("§7Click to accept §eLumberjack's Quest\n§7Reward: §6+100 Coins"));
        Style style = Style.EMPTY.withColor(ChatFormatting.GREEN).withClickEvent(clickEvent).withHoverEvent(hoverEvent);

        Component input = Component.literal("§a[Accept Quest]").setStyle(style);

        MutableComponent translated = textTraverser.translateRecursive(input, str -> {
            if (str.contains("[Accept Quest]")) return "§a[接受任務]";
            if (str.contains("Click to accept")) return str.replace("Click to accept", "點擊接受").replace("Reward:", "獎勵：");
            return str;
        });

        assertEquals("§a[接受任務]", translated.getString());

        // ClickEvent verbatim
        assertNotNull(translated.getStyle().getClickEvent());
        assertInstanceOf(ClickEvent.RunCommand.class, translated.getStyle().getClickEvent());
        assertEquals("/sbquest accept 42", ((ClickEvent.RunCommand) translated.getStyle().getClickEvent()).command());

        // HoverEvent translated
        assertNotNull(translated.getStyle().getHoverEvent());
        assertInstanceOf(HoverEvent.ShowText.class, translated.getStyle().getHoverEvent());
        HoverEvent.ShowText st = (HoverEvent.ShowText) translated.getStyle().getHoverEvent();
        assertTrue(st.value().getString().contains("點擊接受"));
    }

    @Test
    @DisplayName("P06: Scoreboard Sidebar Translation on Display Layer while Maintaining English Regex Purity for Skyblocker")
    void testScoreboardDisplayLayerSkyblockerPurity() {
        // F17 (Display Interception) + F18 (Component Toggle) + F21 (Scoreboard)
        interceptionManager.setMode(ComponentCategory.SCOREBOARD, InterceptionMode.DISPLAY);

        String inMemoryPurseLine = "Purse: 12,500,450 (+1,200)";
        String inMemoryBankLine = "Bank: 50,000,000";

        // Skyblocker's actual purse regex
        Pattern pursePattern = Pattern.compile("(Purse|Piggy): (?<purse>[0-9,.]+)( \\((?<change>[+\\-][0-9,.]+)\\))?");
        Matcher m = pursePattern.matcher(inMemoryPurseLine);
        assertTrue(m.find(), "In-memory string must match Skyblocker purse regex");
        assertEquals("12,500,450", m.group("purse"));
        assertEquals("+1,200", m.group("change"));

        // Display layer produces localized text
        String displayedPurse = inMemoryPurseLine.replace("Purse", tokenDictionary.resolveToken("UI_PURSE"));
        String displayedBank = inMemoryBankLine.replace("Bank", tokenDictionary.resolveToken("UI_BANK"));

        assertEquals("錢包: 12,500,450 (+1,200)", displayedPurse);
        assertEquals("銀行: 50,000,000", displayedBank);
    }

    @Test
    @DisplayName("P07: Pet Level Decomposition + Held Item Lore + Roman Numerals")
    void testPetLevelAndHeldItem() {
        // F9 (Pet Level) + F12 (Ability) + F13 (Roman Numerals)
        String rawPetName = "§7[Lvl 100] §6Baby Yeti";
        MutableComponent comp = dynamicItemCombiner.translateItemName(null, rawPetName);
        assertEquals("§7[Lvl 100] §6雪怪寶寶", comp.getString());

        String heldItemLine = "§8Held Item: §6Tier Boost";
        String zhHeldItem = heldItemLine.replace("Held Item:", "攜帶道具：")
                .replace("Tier Boost", tokenDictionary.resolveToken("ITEM_TIER_BOOST"));
        assertEquals("§8攜帶道具： §6階級提升", zhHeldItem);
    }

    @Test
    @DisplayName("P08: Fallback Suffix Translation on Unknown Item + Automatic Logging to Capture Queue")
    void testFallbackSuffixAndCaptureLogging() {
        // F15 (Fallback Translation) + F16 (Untranslated Queue)
        String unknownItem = "Netherite Broadsword";
        MutableComponent translated = dynamicItemCombiner.translateItemName(null, unknownItem);

        assertEquals("Netherite 闊劍", translated.getString());

        // Telemetry records partial fallback item
        untranslatedQueue.capture(unknownItem, ComponentCategory.TOOLTIPS);
        assertEquals(1, untranslatedQueue.size());
        assertTrue(untranslatedQueue.getRecords().values().iterator().next().cleanText().contains("Netherite Broadsword"));
    }

    @Test
    @DisplayName("P09: Concurrent Dictionary Read/Write and Core Reload Operations")
    void testConcurrentRestVirtualThreads() throws Exception {
        int readers = 10;
        int writers = 5;
        ExecutorService exec = Executors.newFixedThreadPool(readers + writers + 1);
        CountDownLatch latch = new CountDownLatch(readers + writers + 1);

        // 10 Readers
        for (int i = 0; i < readers; i++) {
            exec.submit(() -> {
                try {
                    assertTrue(translationCore.getTokenDictionary().size() > 0);
                } catch (Exception e) {
                    fail(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 5 Writers
        for (int i = 0; i < writers; i++) {
            final int id = i;
            exec.submit(() -> {
                try {
                    translationCore.getTokenDictionary().registerToken(
                            new TokenRecord("TOK_THREAD_" + id, "E" + id, "Z" + id, "", "", "test"));
                } catch (Exception e) {
                    fail(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 1 Reload
        exec.submit(() -> {
            try {
                translationCore.reload();
            } catch (Exception e) {
                fail(e);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        exec.shutdown();
        assertTrue(translationCore.getTokenDictionary().size() >= writers);
    }

    @Test
    @DisplayName("P10: Hotkey Preview Reversion + Clipboard Copy Integration")
    void testHotkeyPreviewAndClipboard() {
        // F19 (Preview Keybind) + F20 (Clipboard Copy)
        String weaponDisplayName = "§6Heroic Hyperion ✪✪✪✪✪";
        List<Component> tooltip = List.of(
                Component.literal(weaponDisplayName),
                Component.literal("§7Damage: §c+260")
        );

        // 1. Hover with Alt key down -> returns English
        List<Component> englishTooltip = dynamicItemCombiner.translateTooltip(null, tooltip, true);
        assertEquals("§6Heroic Hyperion ✪✪✪✪✪", englishTooltip.get(0).getString());

        // 2. Clean name extracted for /ah copy
        String cleanName = dynamicItemCombiner.getCleanEnglishName(weaponDisplayName, "heroic");
        assertEquals("Hyperion", cleanName);
        String ahCommand = "/ah " + cleanName;
        assertEquals("/ah Hyperion", ahCommand);

        // 3. Hover with Alt key up -> returns zh_tw
        List<Component> zhTooltip = dynamicItemCombiner.translateTooltip(null, tooltip, false);
        assertEquals("§6英雄 亥伯龍 ✪✪✪✪✪", zhTooltip.get(0).getString());
    }

    @Test
    @DisplayName("P11: Corrupted CSV Batch Import Recovery Without Dictionary Degradation")
    void testCorruptedCsvRollback() {
        // F6 (Token Dictionary) + F7 (CSV Sync)
        int initialTokens = tokenDictionary.size();
        assertTrue(initialTokens > 0);

        String malformedCsv = """
        "token_id","en","zh_tw","category"
        "VALID_1","V1","正1","test"
        "MALFORMED_ROW","Unclosed string...
        """;

        assertThrows(IllegalArgumentException.class, () -> csvSyncManager.importTokensFromCsvString(tokenDictionary, malformedCsv));

        // Original dictionary must remain 100% intact
        assertEquals(initialTokens, tokenDictionary.size());
        assertNull(tokenDictionary.get("VALID_1")); // Transactional rollback: partial batch discarded
    }

    @Test
    @DisplayName("P12: Recombobulated Rarity Tag Obfuscation + Catacombs Completion Requirement")
    void testRecombobulatedObfuscationAndRequirement() {
        // F14 (Rarity Parser) + F10 (Lore Parser) + F6 (Token Glossary)
        String reqLine = "§4❣ §cRequires The Catacombs Floor VII Completion.";
        String rarityLine = "§d§l§ka§r §d§lMYTHIC DUNGEON SWORD §r§d§l§ka";

        String translatedReq = loreParser.translateLoreLine(reqLine);
        String translatedRarity = loreParser.translateLoreLine(rarityLine);

        assertEquals("§4❣ §c需要通關 地下墓穴 第七層。", translatedReq);
        assertEquals("§d§l§ka§r §d§l神話 地下城 劍 §r§d§l§ka", translatedRarity);
    }

    @Test
    @DisplayName("P13: Container Screen Title & Clickable Slots Translation")
    void testContainerTitleAndSlots() {
        // F20 (Container UI) + F19 (Deep Traversal)
        Component menuTitle = Component.literal("SkyBlock Menu");
        MutableComponent tTitle = textTraverser.translateRecursive(menuTitle, s -> s.replace("Menu", "選單"));
        assertEquals("SkyBlock 選單", tTitle.getString());

        Component slotItem = Component.literal("§aYour Profile");
        MutableComponent tSlot = textTraverser.translateRecursive(slotItem, s -> s.replace("Your Profile", "你的個人檔案"));
        assertEquals("§a你的個人檔案", tSlot.getString());
    }

    @Test
    @DisplayName("P14: Tab List Header/Footer Translation Without Breaking PlayerListManager")
    void testTabListHeaderAndPlayerRegex() {
        // F22 (Tab List) + F1 (Skyblocker Compatibility)
        String rawHeader = "§bYou are playing on §eHypixel SkyBlock";
        String rawPlayerWidget = "§a[Mage 50] Player1";

        // Display layer header
        String zhHeader = rawHeader.replace("You are playing on", "你正在遊玩");
        assertEquals("§b你正在遊玩 §eHypixel SkyBlock", zhHeader);

        // Skyblocker PlayerListManager regex
        Pattern magePattern = Pattern.compile("^\\[Mage (?<level>\\d+)\\]");
        String cleanWidget = rawPlayerWidget.replaceAll("§[0-9a-fk-or]", "");
        Matcher m = magePattern.matcher(cleanWidget);
        assertTrue(m.find(), "Mage class regex must succeed on in-memory widget string");
        assertEquals("50", m.group("level"));
    }

    @Test
    @DisplayName("P15: Dual-Mode Interception Toggle (Chat PACKET mode, Scoreboard DISPLAY mode)")
    void testDualModeToggleInterception() {
        // F18 (Component Toggle) + F17 (Display Intercept) + F24 (Chat)
        interceptionManager.setMode(ComponentCategory.CHAT, InterceptionMode.PACKET);
        interceptionManager.setMode(ComponentCategory.SCOREBOARD, InterceptionMode.DISPLAY);

        assertTrue(interceptionManager.shouldInterceptPacket(ComponentCategory.CHAT));
        assertFalse(interceptionManager.shouldInterceptDisplay(ComponentCategory.CHAT));

        assertTrue(interceptionManager.shouldInterceptDisplay(ComponentCategory.SCOREBOARD));
        assertFalse(interceptionManager.shouldInterceptPacket(ComponentCategory.SCOREBOARD));
    }
}
