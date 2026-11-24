package com.hypixel.translation.e2e;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.config.InterceptionMode;
import com.hypixel.translation.core.*;
import com.hypixel.translation.engine.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.translation.pipeline.FandomWikiCollector;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tier 1: System Feature Coverage Test Suite (≥55 isolated happy-path tests across 11 feature domains).
 * Derivation: TEST_INFRA.md, PROJECT.md, and teamwork_spec_miner_e2e_3/handoff.md.
 */
@Tag("tier1")
@Tag("e2e")
public class Tier1FeatureCoverageTest extends BaseE2ETest {

    // =========================================================================
    // Category 1: Pipeline & Data Ingestion (Features 1-9)
    // =========================================================================
    @Nested
    @DisplayName("Category 1: Data Ingestion & Pipeline Normalization")
    class DataIngestionTests {

        @Test
        @DisplayName("T1-ING-01: Hypixel Items Ingestion Valid Schema Parsing")
        void testHypixelItemsIngestionValidSchema() {
            String jsonPayload = """
            {
              "success": true,
              "items": [
                {
                  "id": "HYPERION",
                  "name": "Hyperion",
                  "tier": "LEGENDARY",
                  "category": "SWORD",
                  "stats": { "DAMAGE": 260, "STRENGTH": 150, "INTELLIGENCE": 350 }
                }
              ]
            }
            """;
            JsonObject root = JsonParser.parseString(jsonPayload).getAsJsonObject();
            assertTrue(root.get("success").getAsBoolean());
            JsonObject item = root.getAsJsonArray("items").get(0).getAsJsonObject();
            assertEquals("HYPERION", item.get("id").getAsString());
            assertEquals("Hyperion", item.get("name").getAsString());
            assertEquals("LEGENDARY", item.get("tier").getAsString());
            assertEquals(260, item.getAsJsonObject("stats").get("DAMAGE").getAsInt());
        }

        @Test
        @DisplayName("T1-ING-02: Hypixel Collections Progression Ingestion")
        void testHypixelCollectionsProgression() {
            String jsonPayload = """
            {
              "success": true,
              "collections": {
                "MINING": {
                  "items": {
                    "COAL": {
                      "name": "Coal",
                      "maxTiers": 9,
                      "tiers": [ { "tier": 1, "unlock": "COAL_MINION" } ]
                    }
                  }
                }
              }
            }
            """;
            JsonObject root = JsonParser.parseString(jsonPayload).getAsJsonObject();
            JsonObject coal = root.getAsJsonObject("collections")
                    .getAsJsonObject("MINING")
                    .getAsJsonObject("items")
                    .getAsJsonObject("COAL");
            assertEquals("Coal", coal.get("name").getAsString());
            assertEquals(9, coal.get("maxTiers").getAsInt());
            assertEquals("COAL_MINION", coal.getAsJsonArray("tiers").get(0).getAsJsonObject().get("unlock").getAsString());
        }

        @Test
        @DisplayName("T1-ING-03: Hypixel Skills Stat Buff Ingestion with Glyphs")
        void testHypixelSkillsIngestion() {
            String jsonPayload = """
            {
              "success": true,
              "skills": {
                "COMBAT": {
                  "name": "Combat",
                  "maxLevel": 60,
                  "levels": [
                    { "level": 50, "description": "Grants +200% Damage to mobs and +25\\ue02c Crit Chance." }
                  ]
                }
              }
            }
            """;
            JsonObject root = JsonParser.parseString(jsonPayload).getAsJsonObject();
            JsonObject combat = root.getAsJsonObject("skills").getAsJsonObject("COMBAT");
            assertEquals(60, combat.get("maxLevel").getAsInt());
            String desc = combat.getAsJsonArray("levels").get(0).getAsJsonObject().get("description").getAsString();
            assertTrue(desc.contains("+200% Damage"));
            assertTrue(desc.contains("\ue02c"));
        }

        @Test
        @DisplayName("T1-ING-04: Fandom MediaWiki Lua Reforge Table Extraction")
        void testFandomLuaReforgeParsing() {
            FandomWikiCollector collector = new FandomWikiCollector(tempDir.resolve("fandom"));
            String luaWikitext = """
            return {
            \t['SWORD'] = {
            \t\t['Heroic'] = {
            \t\t\tstats = { intelligence = 100, attack_speed = 7 },
            \t\t\tsource = 'Basic',
            \t\t\tdescription = 'Increases Intelligence and Attack Speed'
            \t\t},
            \t\t['Spicy'] = {
            \t\t\tstats = { crit_damage = 50, crit_chance = 1 },
            \t\t\tsource = 'Basic'
            \t\t}
            \t}
            }
            """;

            // 1. Verify extractReforgesFromWikitext extracts all reforge name keys
            Map<String, String> extracted = collector.extractReforgesFromWikitext(luaWikitext);
            assertNotNull(extracted, "Extracted reforges map must not be null");
            assertTrue(extracted.containsKey("Heroic"), "Must extract Heroic reforge");
            assertEquals("Heroic", extracted.get("Heroic"));
            assertTrue(extracted.containsKey("Spicy"), "Must extract Spicy reforge");
            assertEquals("Spicy", extracted.get("Spicy"));

            // 2. Verify parseLuaReforges extracts structured WikiReforge metadata records
            Map<String, FandomWikiCollector.WikiReforge> parsed = collector.parseLuaReforges(luaWikitext);
            assertNotNull(parsed, "Parsed Lua reforges must not be null");
            assertTrue(parsed.containsKey("Heroic"));
            FandomWikiCollector.WikiReforge heroic = parsed.get("Heroic");
            assertEquals("Heroic", heroic.name());
            assertEquals("SWORD", heroic.category());
            assertEquals("Basic", heroic.source());
            assertEquals("Increases Intelligence and Attack Speed", heroic.description());

            assertTrue(parsed.containsKey("Spicy"));
            assertEquals("SWORD", parsed.get("Spicy").category());
        }

        @Test
        @DisplayName("T1-ING-05: NEU Local Item JSON Structure Verification")
        void testNeuItemStructure() {
            String neuJson = """
            {
              "itemid": "minecraft:iron_sword",
              "displayname": "§6Hyperion",
              "nbttag": "{ExtraAttributes:{id:\\"HYPERION\\"}}",
              "lore": [
                "§7Damage: §c+260",
                "§7Intelligence: §b+350",
                "§6§lLEGENDARY DUNGEON SWORD"
              ]
            }
            """;
            JsonObject obj = JsonParser.parseString(neuJson).getAsJsonObject();
            assertEquals("minecraft:iron_sword", obj.get("itemid").getAsString());
            assertEquals("§6Hyperion", obj.get("displayname").getAsString());
            assertEquals(3, obj.getAsJsonArray("lore").size());
        }
    }

    // =========================================================================
    // Category 2: Central Tokenized Glossary (Features 12, 13)
    // =========================================================================
    @Nested
    @DisplayName("Category 2: Central Tokenized Glossary & CSV Two-Way Sync")
    class TokenGlossaryTests {

        @Test
        @DisplayName("T1-TOK-01: Single Token Resolution in Template")
        void testSingleTokenResolution() {
            String template = "§7{STAT_DAMAGE}: §c+100";
            String resolved = tokenDictionary.resolveString(template);
            assertEquals("§7傷害: §c+100", resolved);
        }

        @Test
        @DisplayName("T1-TOK-02: Multiple Tokens in Single Line")
        void testMultipleTokensInSingleLine() {
            String template = "§7{STAT_DAMAGE}: §c+100  §7{STAT_STRENGTH}: §c+50";
            String resolved = tokenDictionary.resolveString(template);
            assertEquals("§7傷害: §c+100  §7力量: §c+50", resolved);
        }

        @Test
        @DisplayName("T1-TOK-03: Token Glyph and Color Dynamic Resolution")
        void testTokenGlyphAndColorResolution() {
            String template = "{STAT_DAMAGE_COLOR}+{STAT_DAMAGE_GLYPH} {STAT_DAMAGE}";
            String resolved = tokenDictionary.resolveString(template);
            assertEquals("§c+\ue050 傷害", resolved);
        }

        @Test
        @DisplayName("T1-TOK-04: RFC 4180 CSV Export Format")
        void testCsvExportFormat() {
            String csv = csvSyncManager.exportTokensToCsvString(tokenDictionary);
            assertTrue(csv.startsWith("\"token_id\",\"en\",\"zh_tw\",\"category\""));
            assertTrue(csv.contains("\"STAT_DAMAGE\",\"Damage\",\"傷害\",\"stat\""));
            assertTrue(csv.contains("\"ITEM_HYPERION\",\"Hyperion\",\"亥伯龍\",\"item\""));
        }

        @Test
        @DisplayName("T1-TOK-05: RFC 4180 CSV Import and In-Memory Update")
        void testCsvImportUpdate() {
            String newCsv = """
            "token_id","en","zh_tw","category"
            "STAT_MANA_REGEN","Mana Regen","魔力回復","stat"
            "STAT_DAMAGE","Damage","真·傷害","stat"
            """;
            csvSyncManager.importTokensFromCsvString(tokenDictionary, newCsv);

            assertEquals("魔力回復", tokenDictionary.resolveToken("STAT_MANA_REGEN"));
            assertEquals("真·傷害", tokenDictionary.resolveToken("STAT_DAMAGE"));
        }
    }

    // =========================================================================
    // Category 3: Item Decomposition Engine (Features 14, 15)
    // =========================================================================
    @Nested
    @DisplayName("Category 3: Item Decomposition Engine")
    class ItemDecompositionTests {

        @Test
        @DisplayName("T1-DEC-01: Simple Reforge Decomposition (Heroic Hyperion)")
        void testSimpleReforgeDecomposition() {
            MutableComponent result = dynamicItemCombiner.translateItemName(null, "§6Heroic Hyperion");
            assertEquals("§6英雄 亥伯龍", result.getString());
        }

        @Test
        @DisplayName("T1-DEC-02: Starred Dungeon Item Decomposition (Hyperion ✪✪✪✪✪)")
        void testStarredDungeonItemDecomposition() {
            MutableComponent result = dynamicItemCombiner.translateItemName(null, "§6Hyperion ✪✪✪✪✪");
            assertEquals("§6亥伯龍 ✪✪✪✪✪", result.getString());
        }

        @Test
        @DisplayName("T1-DEC-03: Reforged & Starred Weapon (Withered Hyperion ✪✪✪✪✪)")
        void testReforgedAndStarredWeapon() {
            MutableComponent result = dynamicItemCombiner.translateItemName(null, "§6Withered Hyperion ✪✪✪✪✪");
            assertEquals("§6凋零 亥伯龍 ✪✪✪✪✪", result.getString());
        }

        @Test
        @DisplayName("T1-DEC-04: Master Mode Starred Item (Spiritual Terminator ✪✪✪✪§c➊)")
        void testMasterModeStarredItem() {
            MutableComponent result = dynamicItemCombiner.translateItemName(null, "§6Spiritual Terminator ✪✪✪✪§c➊");
            assertEquals("§6精神 終結者 ✪✪✪✪§c➊", result.getString());
        }

        @Test
        @DisplayName("T1-DEC-05: Pet Level Decomposition ([Lvl 100] Baby Yeti)")
        void testPetLevelDecomposition() {
            MutableComponent result = dynamicItemCombiner.translateItemName(null, "§7[Lvl 100] §6Baby Yeti");
            assertEquals("§7[Lvl 100] §6雪怪寶寶", result.getString());
        }
    }

    // =========================================================================
    // Category 4: Lore Stat Lines & Brackets (Feature 16)
    // =========================================================================
    @Nested
    @DisplayName("Category 4: Lore Stat Lines & Bracket Formatting")
    class LoreParsingTests {

        @Test
        @DisplayName("T1-LOR-01: Base Stat Line Translation (Damage: +100)")
        void testBaseStatLine() {
            String translated = loreParser.translateLoreLine("§7Damage: §c+100");
            assertEquals("§7傷害：§c+100", translated);
        }

        @Test
        @DisplayName("T1-LOR-02: Stat Line with Reforge Bonus Round Bracket (Damage: +260 (+30))")
        void testStatLineWithRoundBracket() {
            String translated = loreParser.translateLoreLine("§7Damage: §c+260 §e(+30)");
            assertEquals("§7傷害：§c+260 §e(+30)", translated);
        }

        @Test
        @DisplayName("T1-LOR-03: Stat Line with Round & Square Brackets (Strength: +150 (+20) [+15])")
        void testStatLineWithDualBrackets() {
            String translated = loreParser.translateLoreLine("§7Strength: §c+150 §e(+20) §a[+15]");
            assertEquals("§7力量：§c+150 §e(+20) §a[+15]", translated);
        }

        @Test
        @DisplayName("T1-LOR-04: Negative Stat Line (Speed: -10)")
        void testNegativeStatLine() {
            String translated = loreParser.translateLoreLine("§7Speed: §c-10");
            assertEquals("§7速度：§c-10", translated);
        }

        @Test
        @DisplayName("T1-LOR-05: Percentage Stat Line (Crit Damage: +250%)")
        void testPercentageStatLine() {
            String translated = loreParser.translateLoreLine("§7Crit Damage: §9+250%");
            assertEquals("§7暴擊傷害：§9+250%", translated);
        }
    }

    // =========================================================================
    // Category 5: Gemstones, Abilities & Enchants (Features 17-20)
    // =========================================================================
    @Nested
    @DisplayName("Category 5: Gemstones, Abilities & Enchantments")
    class GemstoneAndAbilityTests {

        @Test
        @DisplayName("T1-GAE-01: Gemstone Slot Line Symbol Preservation")
        void testGemstoneSlotLine() {
            String translated = loreParser.translateLoreLine("§7Gemstones: §8[\ue003] [⚔]");
            assertEquals("§7寶石槽：§8[\ue003] [⚔]", translated);
        }

        @Test
        @DisplayName("T1-GAE-02: Ability Header Line Translation")
        void testAbilityHeader() {
            String translated = loreParser.translateLoreLine("§6Ability: Instant Transmission  §e§lRIGHT CLICK");
            assertEquals("§6技能：瞬間移動  §e§l右鍵點擊", translated);
        }

        @Test
        @DisplayName("T1-GAE-03: Mana Cost and Cooldown Line Translation")
        void testManaCostAndCooldown() {
            String line1 = loreParser.translateLoreLine("§8Mana Cost: §b50\ue003");
            String line2 = loreParser.translateLoreLine("§8Cooldown: §a10s");
            assertEquals("§8魔力消耗：§b50\ue003", line1);
            assertEquals("§8冷卻時間：§a10秒", line2);
        }

        @Test
        @DisplayName("T1-GAE-04: Enchantment Line with Roman Numerals (Sharpness V)")
        void testEnchantmentsLine() {
            String translated = loreParser.translateLoreLine("§9Sharpness V, Critical VI, Giant Killer V");
            assertEquals("§9鋒利 V, 暴擊 VI, 巨人殺手 V", translated);
        }

        @Test
        @DisplayName("T1-GAE-05: Rarity & Category Footer Line (LEGENDARY DUNGEON SWORD)")
        void testRarityFooterLine() {
            String translated = loreParser.translateLoreLine("§6§lLEGENDARY DUNGEON SWORD");
            assertEquals("§6§l傳奇 地下城 劍", translated);
        }
    }

    // =========================================================================
    // Category 6: Fallback Translation & Untranslated Capture (Features 21, 22)
    // =========================================================================
    @Nested
    @DisplayName("Category 6: Fallback Rules & Untranslated Text Capture")
    class FallbackAndCaptureTests {

        @Test
        @DisplayName("T1-FAL-01: Suffix Rule Match for Sword (Dragon Shortsword)")
        void testSuffixRuleShortsword() {
            MutableComponent comp = dynamicItemCombiner.translateItemName(null, "Dragon Shortsword");
            assertEquals("Dragon 短劍", comp.getString());
        }

        @Test
        @DisplayName("T1-FAL-02: Suffix Rule Match for Helmet (Obsidian Helmet)")
        void testSuffixRuleHelmet() {
            MutableComponent comp = dynamicItemCombiner.translateItemName(null, "Obsidian Helmet");
            assertEquals("Obsidian 頭盔", comp.getString());
        }

        @Test
        @DisplayName("T1-CAP-01: Untranslated String Capture Recording")
        void testUntranslatedCapture() {
            untranslatedQueue.capture("§eWelcome to the Rift!", ComponentCategory.CHAT);
            assertEquals(1, untranslatedQueue.size());
            UntranslatedRecord rec = untranslatedQueue.getRecords().values().iterator().next();
            assertEquals("Welcome to the Rift!", rec.cleanText());
            assertEquals(1, rec.seenCount());
        }

        @Test
        @DisplayName("T1-CAP-02: Untranslated String Deduplication Count")
        void testUntranslatedDeduplication() {
            untranslatedQueue.capture("Welcome to the Rift!", ComponentCategory.CHAT);
            untranslatedQueue.capture("Welcome to the Rift!", ComponentCategory.CHAT);
            untranslatedQueue.capture("Welcome to the Rift!", ComponentCategory.CHAT);
            assertEquals(1, untranslatedQueue.size());
            UntranslatedRecord rec = untranslatedQueue.getRecords().values().iterator().next();
            assertEquals(3, rec.seenCount());
        }

        @Test
        @DisplayName("T1-CAP-03: Untranslated Queue JSON Flush to Disk")
        void testUntranslatedFlushJson() throws Exception {
            untranslatedQueue.capture("Secret Room Unlocked", ComponentCategory.ACTION_BAR);
            Path jsonFile = tempDir.resolve("untranslated_capture.json");
            untranslatedQueue.flushToJson(jsonFile);

            assertTrue(Files.exists(jsonFile));
            String content = Files.readString(jsonFile);
            assertTrue(content.contains("Secret Room Unlocked"));
            assertTrue(content.contains("ACTION_BAR"));
        }
    }

    // =========================================================================
    // Category 7: Non-Destructive Display Interception (Features 23, 24)
    // =========================================================================
    @Nested
    @DisplayName("Category 7: Non-Destructive Interception & Routing Modes")
    class InterceptionModeTests {

        @Test
        @DisplayName("T1-INT-01: Display Layer Mode Invariant (In-Memory English Retained)")
        void testDisplayLayerInvariant() {
            Component englishComp = Component.literal("Damage: +260");
            MutableComponent displayed = textTraverser.translateRecursive(englishComp, s -> loreParser.translateLoreLine(s));

            assertEquals("傷害：+260", displayed.getString());
            assertEquals("Damage: +260", englishComp.getString()); // Must NOT mutate original!
        }

        @Test
        @DisplayName("T1-INT-02: Packet Layer Mode Decision")
        void testPacketLayerDecision() {
            interceptionManager.setMode(ComponentCategory.CHAT, InterceptionMode.PACKET);
            assertTrue(interceptionManager.shouldInterceptPacket(ComponentCategory.CHAT));
            assertFalse(interceptionManager.shouldInterceptDisplay(ComponentCategory.CHAT));
        }

        @Test
        @DisplayName("T1-INT-03: Disabled Component Mode Preserves English")
        void testDisabledModeDecision() {
            interceptionManager.setMode(ComponentCategory.SCOREBOARD, InterceptionMode.DISABLED);
            assertFalse(interceptionManager.shouldInterceptDisplay(ComponentCategory.SCOREBOARD));
            assertFalse(interceptionManager.shouldInterceptPacket(ComponentCategory.SCOREBOARD));
        }

        @Test
        @DisplayName("T1-INT-04: Skyblocker Coexistence Check via Regex")
        void testSkyblockerCoexistencePurseRegex() {
            Pattern pursePattern = Pattern.compile("(Purse|Piggy): (?<purse>[0-9,.]+)");
            String inMemoryText = "Purse: 1,500,200";

            // In-memory matcher succeeds
            Matcher m = pursePattern.matcher(inMemoryText);
            assertTrue(m.find());
            assertEquals("1,500,200", m.group("purse"));

            // Display text is translated cleanly
            String displayText = inMemoryText.replace("Purse", tokenDictionary.resolveToken("UI_PURSE"));
            assertEquals("錢包: 1,500,200", displayText);
        }

        @Test
        @DisplayName("T1-INT-05: 9-Category Default Initialization to DISPLAY")
        void testDefaultCategoryModes() {
            for (ComponentCategory cat : ComponentCategory.values()) {
                assertEquals(InterceptionMode.DISPLAY, interceptionManager.getMode(cat));
                assertTrue(interceptionManager.shouldInterceptDisplay(cat));
            }
        }
    }

    // =========================================================================
    // Category 8: Deep Text Traversal & Event Preservation (Feature 25)
    // =========================================================================
    @Nested
    @DisplayName("Category 8: Deep Text Traversal & Event Preservation")
    class TextTraversalTests {

        @Test
        @DisplayName("T1-TRA-01: Text Component Literal Translation")
        void testLiteralTranslation() {
            Component c = Component.literal("Click Here");
            MutableComponent t = textTraverser.translateRecursive(c, s -> s.replace("Click Here", "點擊此處"));
            assertEquals("點擊此處", t.getString());
        }

        @Test
        @DisplayName("T1-TRA-02: Style Formatting Color and Bold Preservation")
        void testStyleFormattingPreservation() {
            Component c = Component.literal("Warning").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            MutableComponent t = textTraverser.translateRecursive(c, s -> "警告");

            assertEquals("警告", t.getString());
            assertNotNull(t.getStyle());
            assertTrue(t.getStyle().isBold());
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), t.getStyle().getColor());
        }

        @Test
        @DisplayName("T1-TRA-03: ClickEvent Command Verbatim Preservation")
        void testClickEventVerbatim() {
            Style s = Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/warp hub"));
            Component c = Component.literal("[WARP]").setStyle(s);

            MutableComponent t = textTraverser.translateRecursive(c, str -> "[傳送]");
            assertEquals("[傳送]", t.getString());
            assertNotNull(t.getStyle().getClickEvent());
            assertInstanceOf(ClickEvent.RunCommand.class, t.getStyle().getClickEvent());
            assertEquals("/warp hub", ((ClickEvent.RunCommand) t.getStyle().getClickEvent()).command());
        }

        @Test
        @DisplayName("T1-TRA-04: HoverEvent ShowText Tooltip Translation")
        void testHoverEventShowTextTranslation() {
            Style s = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal("Damage: +100")));
            Component c = Component.literal("Hover Me").setStyle(s);

            MutableComponent t = textTraverser.translateRecursive(c, str -> {
                if (str.equals("Hover Me")) return "懸浮查看";
                if (str.equals("Damage: +100")) return "傷害：+100";
                return str;
            });

            assertEquals("懸浮查看", t.getString());
            assertNotNull(t.getStyle().getHoverEvent());
            assertInstanceOf(HoverEvent.ShowText.class, t.getStyle().getHoverEvent());
            HoverEvent.ShowText st = (HoverEvent.ShowText) t.getStyle().getHoverEvent();
            assertEquals("傷害：+100", st.value().getString());
        }

        @Test
        @DisplayName("T1-TRA-05: Recursive Sibling Traversal Preservation")
        void testRecursiveSiblingTraversal() {
            Component c = Component.literal("Root ")
                    .append(Component.literal("Child A "))
                    .append(Component.literal("Child B"));

            MutableComponent t = textTraverser.translateRecursive(c, str -> str.replace("Root ", "根 ")
                    .replace("Child A ", "子A ")
                    .replace("Child B", "子B"));

            assertEquals("根 子A 子B", t.getString());
            assertEquals(2, t.getSiblings().size());
        }
    }

    // =========================================================================
    // Category 9: UI, HUD, & Holograms (Features 26-30)
    // =========================================================================
    @Nested
    @DisplayName("Category 9: UI, HUD, Scoreboard & Holograms")
    class UiAndHudTests {

        @Test
        @DisplayName("T1-HUD-01: Container Screen Title Translation (SkyBlock Menu)")
        void testContainerScreenTitle() {
            // Authentic Minecraft Container Screen Title Component
            Component titleComp = Component.literal("SkyBlock Menu").withStyle(ChatFormatting.DARK_GRAY);

            // Execute translation engine via recursive Component traverser with TokenDictionary lookups
            MutableComponent translated = textTraverser.translateRecursive(titleComp, tokenDictionary::translateText);

            // 1. Verify text localization to Traditional Chinese
            assertEquals("SkyBlock 選單", translated.getString());

            // 2. Verify style and color preservation
            assertNotNull(translated.getStyle());
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY), translated.getStyle().getColor());

            // 3. Verify non-destructive display invariant (underlying in-memory Component retains pure English)
            assertEquals("SkyBlock Menu", titleComp.getString(),
                    "In-memory container title must remain pure English for external mod regex matchers");
        }

        @Test
        @DisplayName("T1-HUD-02: Anvil Screen Title Translation")
        void testAnvilScreenTitle() {
            // Authentic Anvil Screen Title Component
            Component anvilComp = Component.literal("Anvil").withStyle(ChatFormatting.DARK_GRAY);

            // Execute translation engine
            MutableComponent translated = textTraverser.translateRecursive(anvilComp, tokenDictionary::translateText);

            // 1. Verify localized title
            assertEquals("鐵砧", translated.getString());

            // 2. Verify dark gray container title formatting is retained
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY), translated.getStyle().getColor());

            // 3. Verify in-memory non-destructive invariant
            assertEquals("Anvil", anvilComp.getString());
        }

        @Test
        @DisplayName("T1-HUD-03: Scoreboard Objective Sidebar Header")
        void testScoreboardHeader() {
            // Authentic Scoreboard Header Component with Yellow and Bold styles
            Component headerComp = Component.literal("SKYBLOCK").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);

            // Execute translation engine
            MutableComponent translated = textTraverser.translateRecursive(headerComp, tokenDictionary::translateText);

            // 1. Verify localized header text
            assertEquals("空島生存", translated.getString());

            // 2. Verify formatting codes: Yellow color and Bold attribute preserved
            assertNotNull(translated.getStyle());
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.YELLOW), translated.getStyle().getColor());
            assertTrue(translated.getStyle().isBold(), "Scoreboard header bold formatting must be preserved");

            // 3. Verify Skyblocker compatibility: in-memory objective title is strictly untouched
            assertEquals("SKYBLOCK", headerComp.getString());
            assertTrue(headerComp.getString().contains("SKYBLOCK"),
                    "In-memory scoreboard objective must retain 'SKYBLOCK' for external mod detection");
        }

        @Test
        @DisplayName("T1-HUD-04: ActionBar Mana Display Translation")
        void testActionBarMana() {
            // Authentic compound ActionBar Component: [Aqua Mana] + [Space] + [Green Mana/s]
            Component manaCurrent = Component.literal("150/150 Mana").withStyle(ChatFormatting.AQUA);
            Component spacer = Component.literal(" ");
            Component manaRegen = Component.literal("+5 Mana/s").withStyle(ChatFormatting.GREEN);
            Component barComp = Component.empty().append(manaCurrent).append(spacer).append(manaRegen);

            // Execute translation engine
            MutableComponent translated = textTraverser.translateRecursive(barComp, tokenDictionary::translateText);

            // 1. Verify full localized action bar string
            assertEquals("150/150 魔力 +5 魔力/秒", translated.getString());

            // 2. Verify sibling styles and color boundaries
            assertEquals(3, translated.getSiblings().size());
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA), translated.getSiblings().get(0).getStyle().getColor());
            assertEquals("150/150 魔力", translated.getSiblings().get(0).getString());
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), translated.getSiblings().get(2).getStyle().getColor());
            assertEquals("+5 魔力/秒", translated.getSiblings().get(2).getString());

            // 3. Verify Skyblocker external regex parsing against in-memory English Component
            Pattern manaPattern = Pattern.compile("(?<cur>\\d+)/(?<max>\\d+) Mana");
            Matcher m = manaPattern.matcher(barComp.getString());
            assertTrue(m.find(), "Skyblocker mana HUD regex must match in-memory English Component");
            assertEquals("150", m.group("cur"));
            assertEquals("150", m.group("max"));
        }

        @Test
        @DisplayName("T1-HUD-05: Floating Hologram NPC Tag Translation")
        void testFloatingHologramNpc() {
            // Authentic Floating Hologram Component: Yellow "[NPC] Jerry" + Green Bold "CLICK"
            Component npcPrefix = Component.literal("[NPC] Jerry").withStyle(ChatFormatting.YELLOW);
            Component spacer = Component.literal(" ");
            Component clickAction = Component.literal("CLICK").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            Component holoComp = Component.empty().append(npcPrefix).append(spacer).append(clickAction);

            // Execute translation engine
            MutableComponent translated = textTraverser.translateRecursive(holoComp, tokenDictionary::translateText);

            // 1. Verify localized text
            assertEquals("[NPC] 傑瑞 點擊", translated.getString());

            // 2. Verify leaf styles: Yellow NPC tag, Green Bold CLICK prompt
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.YELLOW), translated.getSiblings().get(0).getStyle().getColor());
            assertEquals("[NPC] 傑瑞", translated.getSiblings().get(0).getString());
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), translated.getSiblings().get(2).getStyle().getColor());
            assertTrue(translated.getSiblings().get(2).getStyle().isBold());
            assertEquals("點擊", translated.getSiblings().get(2).getString());

            // 3. Verify in-memory invariant for NPC interaction detection
            assertEquals("[NPC] Jerry CLICK", holoComp.getString());
        }
    }

    // =========================================================================
    // Category 10: Hotkey Preview & English Clipboard (Feature 31)
    // =========================================================================
    @Nested
    @DisplayName("Category 10: Hotkey Preview & English Clipboard Copy")
    class HotkeyAndClipboardTests {

        @Test
        @DisplayName("T1-PRE-01: Alt Key Preview Reversion to English")
        void testAltKeyPreviewReversion() {
            List<Component> original = List.of(
                    Component.literal("§6Heroic Hyperion ✪✪✪✪✪"),
                    Component.literal("§7Damage: §c+260")
            );
            List<Component> displayed = dynamicItemCombiner.translateTooltip(null, original, true);

            assertEquals("§6Heroic Hyperion ✪✪✪✪✪", displayed.get(0).getString());
            assertEquals("§7Damage: §c+260", displayed.get(1).getString());
        }

        @Test
        @DisplayName("T1-PRE-02: Normal Render When Alt Key Released")
        void testNormalRenderKeyReleased() {
            List<Component> original = List.of(
                    Component.literal("§6Heroic Hyperion ✪✪✪✪✪"),
                    Component.literal("§7Damage: §c+260")
            );
            List<Component> displayed = dynamicItemCombiner.translateTooltip(null, original, false);

            assertEquals("§6英雄 亥伯龍 ✪✪✪✪✪", displayed.get(0).getString());
            assertEquals("§7傷害：§c+260", displayed.get(1).getString());
        }

        @Test
        @DisplayName("T1-PRE-03: English Subtitle Appended Note")
        void testEnglishSubtitleNote() {
            String cleanEnglish = dynamicItemCombiner.getCleanEnglishName("§6Heroic Hyperion ✪✪✪✪✪", null);
            String note = "§8English: " + cleanEnglish;
            assertEquals("§8English: Hyperion", note);
        }

        @Test
        @DisplayName("T1-PRE-04: Clean Name Clipboard Extraction for Weapon (Hyperion)")
        void testCleanClipboardWeapon() {
            String clean = dynamicItemCombiner.getCleanEnglishName("§6Heroic Hyperion ✪✪✪✪✪", null);
            assertEquals("Hyperion", clean);
        }

        @Test
        @DisplayName("T1-PRE-05: Clean Name Clipboard Extraction for Collision Item (Ancient Claw)")
        void testCleanClipboardCollisionItem() {
            String clean = dynamicItemCombiner.getCleanEnglishName("§aAncient Claw", null);
            assertEquals("Ancient Claw", clean);
        }
    }
}
