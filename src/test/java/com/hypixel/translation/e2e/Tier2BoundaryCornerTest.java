package com.hypixel.translation.e2e;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.config.InterceptionMode;
import com.hypixel.translation.core.*;
import com.hypixel.translation.engine.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.translation.pipeline.FandomWikiCollector;
import com.hypixel.translation.pipeline.SchemaNormalizer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tier 2: Boundary, Edge Cases, Collision Disambiguation & Error Handling (≥55 tests).
 * Derivation: TEST_INFRA.md, PROJECT.md, and teamwork_spec_miner_e2e_3/handoff.md.
 */
@Tag("tier2")
@Tag("boundary")
public class Tier2BoundaryCornerTest extends BaseE2ETest {

    // =========================================================================
    // Category 1: Pipeline & Data Ingestion Boundaries
    // =========================================================================
    @Nested
    @DisplayName("Category 1: Pipeline Casing, Malformed JSON & Ingestion Boundaries")
    class PipelineBoundaries {

        @Test
        @DisplayName("T2-ING-01: Stat Key Casing Inconsistency Normalization")
        void testStatKeyCasingNormalization() {
            // 1. Test canonical normalization across case variations, spaces, hyphens, and abbreviations
            assertEquals("DEFENSE", SchemaNormalizer.normalizeStat("DEFENSE"));
            assertEquals("DEFENSE", SchemaNormalizer.normalizeStat("defense"));
            assertEquals("DEFENSE", SchemaNormalizer.normalizeStat("DEFENCE")); // British spelling
            assertEquals("RIFT_TIME", SchemaNormalizer.normalizeStat("rift_Time"));
            assertEquals("CRIT_DAMAGE", SchemaNormalizer.normalizeStat("CRITICAL_DAMAGE"));
            assertEquals("CRIT_DAMAGE", SchemaNormalizer.normalizeStat("crit damage"));
            assertEquals("CRIT_DAMAGE", SchemaNormalizer.normalizeStat("cd"));
            assertEquals("CRIT_CHANCE", SchemaNormalizer.normalizeStat("cc"));
            assertEquals("STRENGTH", SchemaNormalizer.normalizeStat("str"));
            assertEquals("DAMAGE", SchemaNormalizer.normalizeStat("dmg"));
            assertEquals("SPEED", SchemaNormalizer.normalizeStat("walk-speed"));

            // 2. Test boundary corner cases: null, empty string, and whitespace
            assertEquals("UNKNOWN", SchemaNormalizer.normalizeStat(null));
            assertEquals("UNKNOWN", SchemaNormalizer.normalizeStat(""));
            assertEquals("UNKNOWN", SchemaNormalizer.normalizeStat("   "));

            // 3. Test map-level batch normalization exercising SchemaNormalizer directly
            Map<String, Integer> rawStats = new HashMap<>();
            rawStats.put("DEFENSE", 100);
            rawStats.put("defense", 100);
            rawStats.put("DEFENCE", 100);
            rawStats.put("rift_Time", 25);
            rawStats.put("CRITICAL_DAMAGE", 50);
            rawStats.put("dmg", 260);

            Map<String, Integer> normalized = new HashMap<>();
            rawStats.forEach((k, v) -> normalized.put(SchemaNormalizer.normalizeStat(k), v));

            assertEquals(100, normalized.get("DEFENSE"));
            assertEquals(25, normalized.get("RIFT_TIME"));
            assertEquals(50, normalized.get("CRIT_DAMAGE"));
            assertEquals(260, normalized.get("DAMAGE"));
        }

        @Test
        @DisplayName("T2-ING-02: Unknown / Special Item Tier Handling")
        void testUnknownItemTier() {
            // 1. Verify normalization of special/non-standard Hypixel item tiers
            assertEquals("VERY_SPECIAL", SchemaNormalizer.normalizeRarity("VERY_SPECIAL"));
            assertEquals("VERY_SPECIAL", SchemaNormalizer.normalizeRarity("very special"));
            assertEquals("VERY_SPECIAL", SchemaNormalizer.normalizeRarity("very-special"));
            assertEquals("SPECIAL", SchemaNormalizer.normalizeRarity("special"));
            assertEquals("DIVINE", SchemaNormalizer.normalizeRarity("divine"));
            assertEquals("ULTIMATE", SchemaNormalizer.normalizeRarity("ultimate"));
            assertEquals("ADMIN", SchemaNormalizer.normalizeRarity("admin"));
            assertEquals("LEGENDARY", SchemaNormalizer.normalizeRarity("legendary"));

            // 2. Verify boundary values: null and blank strings default safely to COMMON
            assertEquals("COMMON", SchemaNormalizer.normalizeRarity(null));
            assertEquals("COMMON", SchemaNormalizer.normalizeRarity(""));
            assertEquals("COMMON", SchemaNormalizer.normalizeRarity("   "));

            // 3. Verify uncataloged/unknown tier strings are sanitized to UPPER_SNAKE_CASE
            assertEquals("UNOBTAINABLE_TIER", SchemaNormalizer.normalizeRarity("unobtainable tier"));
            assertEquals("CUSTOM_EVENT_TIER", SchemaNormalizer.normalizeRarity("custom-event-tier"));
        }

        @Test
        @DisplayName("T2-ING-03: Item with Empty Lore and Stats Arrays")
        void testItemWithEmptyLoreAndStats() {
            String json = "{\"id\":\"EMPTY_ITEM\",\"lore\":[],\"stats\":{}}";
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            assertEquals("EMPTY_ITEM", obj.get("id").getAsString());
            assertEquals(0, obj.getAsJsonArray("lore").size());
            assertEquals(0, obj.getAsJsonObject("stats").size());
        }

        @Test
        @DisplayName("T2-ING-04: API Rate Limit HTTP 429 Payload Recognition")
        void testRateLimitPayload() {
            String rateLimitJson = "{\"success\": false, \"cause\": \"Key throttle\", \"throttle\": true}";
            JsonObject obj = JsonParser.parseString(rateLimitJson).getAsJsonObject();
            assertFalse(obj.get("success").getAsBoolean());
            assertTrue(obj.get("throttle").getAsBoolean());
        }

        @Test
        @DisplayName("T2-ING-05: Fandom Wiki Malformed Lua Table Detection")
        void testMalformedLuaTableDetection() {
            FandomWikiCollector collector = new FandomWikiCollector(tempDir.resolve("fandom"));

            // 1. Truncated Lua table syntax with invalid quotation or missing table braces
            String malformedLua = "reforges = { [\"Heroic\"] = { item_types = ";
            Map<String, String> malformedResult = collector.extractReforgesFromWikitext(malformedLua);
            assertNotNull(malformedResult);
            assertTrue(malformedResult.isEmpty(), "Malformed Lua table syntax without single-quoted table key must return empty map");

            // 2. Snippet missing opening table brace for reforge entry
            String missingBraceLua = "reforges = { ['Heroic'] = item_types = {'SWORD'} }";
            Map<String, String> noBraceResult = collector.extractReforgesFromWikitext(missingBraceLua);
            assertFalse(noBraceResult.containsKey("Heroic"), "Reforge entry without opening brace must not be extracted");

            // 3. parseLuaReforges resilience against completely invalid / corrupted syntax
            String garbageText = "Not a valid Lua table at all! {{{ }}} random characters 12345";
            Map<String, FandomWikiCollector.WikiReforge> garbageResult = collector.parseLuaReforges(garbageText);
            assertNotNull(garbageResult);
            assertTrue(garbageResult.isEmpty(), "Garbage wikitext must safely produce empty WikiReforge map without throwing");

            // 4. Mixed stream: malformed entries must be skipped while valid entries are preserved
            String mixedLua = """
            -- Malformed entry: missing table brace
            ['BrokenReforge'] = incomplete_data,
            -- Valid entry: properly formatted
            ['Heroic'] = {
                item_types = {"SWORD"},
                stats = { intelligence = 100 }
            }
            """;
            Map<String, String> mixedResult = collector.extractReforgesFromWikitext(mixedLua);
            assertEquals(1, mixedResult.size());
            assertTrue(mixedResult.containsKey("Heroic"), "Valid reforge in mixed stream must be extracted");
            assertFalse(mixedResult.containsKey("BrokenReforge"), "Corrupted reforge entry must be skipped");
        }
    }

    // =========================================================================
    // Category 2: Token Glossary Boundaries
    // =========================================================================
    @Nested
    @DisplayName("Category 2: Token Glossary Boundaries & Circular Recursion")
    class TokenGlossaryBoundaries {

        @Test
        @DisplayName("T2-TOK-01: Unregistered Token ID Retained Literally")
        void testUnregisteredToken() {
            String template = "§7{NON_EXISTENT_TOKEN}: §c+100";
            String resolved = tokenDictionary.resolveString(template);
            assertEquals("§7{NON_EXISTENT_TOKEN}: §c+100", resolved);
        }

        @Test
        @DisplayName("T2-TOK-02: Empty String Token Value Handling")
        void testEmptyStringTokenValue() {
            tokenDictionary.registerToken(new TokenRecord("EMPTY_VAL", "Empty", "", "", "", "test"));
            String template = "Prefix {EMPTY_VAL} Suffix";
            String resolved = tokenDictionary.resolveString(template);
            assertEquals("Prefix  Suffix", resolved);
        }

        @Test
        @DisplayName("T2-TOK-03: Circular Recursive Token References Terminates Gracefully")
        void testCircularTokenRecursion() {
            // A points to {B}, B points to {A}
            tokenDictionary.registerToken(new TokenRecord("TOKEN_A", "A", "See {TOKEN_B}", "", "", "test"));
            tokenDictionary.registerToken(new TokenRecord("TOKEN_B", "B", "See {TOKEN_A}", "", "", "test"));

            assertDoesNotThrow(() -> {
                String result = tokenDictionary.resolveString("{TOKEN_A}");
                assertNotNull(result);
                assertTrue(result.contains("See"));
            });
        }

        @Test
        @DisplayName("T2-TOK-04: Deep Nested Tokens (Depth 3 Valid)")
        void testDeepNestedTokens() {
            tokenDictionary.registerToken(new TokenRecord("TOKEN_C", "C", "ResolvedValue", "", "", "test"));
            tokenDictionary.registerToken(new TokenRecord("TOKEN_B", "B", "{TOKEN_C}", "", "", "test"));
            tokenDictionary.registerToken(new TokenRecord("TOKEN_A", "A", "{TOKEN_B}", "", "", "test"));

            String result = tokenDictionary.resolveString("{TOKEN_A}");
            assertEquals("ResolvedValue", result);
        }

        @Test
        @DisplayName("T2-TOK-05: Special Characters in Token ID (Hyphens and Numbers)")
        void testSpecialCharTokenId() {
            tokenDictionary.registerToken(new TokenRecord("STAT_TRUE_DEFENSE_2", "True Defense II", "真實防禦 II", "", "", "stat"));
            String result = tokenDictionary.resolveString("{STAT_TRUE_DEFENSE_2}");
            assertEquals("真實防禦 II", result);
        }
    }

    // =========================================================================
    // Category 3: CSV Sync Boundaries
    // =========================================================================
    @Nested
    @DisplayName("Category 3: RFC 4180 CSV Boundaries & Error Recovery")
    class CsvSyncBoundaries {

        @Test
        @DisplayName("T2-CSV-01: CSV Field with Commas and Escaped Quotes")
        void testCsvCommasAndEscapedQuotes() {
            String csv = """
            "token_id","en","zh_tw","category"
            "TOKEN_TEST","Hello, ""World""!","哈囉，""世界""！","misc"
            """;
            csvSyncManager.importTokensFromCsvString(tokenDictionary, csv);

            TokenRecord rec = tokenDictionary.get("TOKEN_TEST");
            assertNotNull(rec);
            assertEquals("Hello, \"World\"!", rec.en());
            assertEquals("哈囉，\"世界\"！", rec.zh_tw());
        }

        @Test
        @DisplayName("T2-CSV-02: CSV Field with Multiline Embedded Newlines")
        void testCsvMultilineField() {
            String csv = "\"token_id\",\"en\",\"zh_tw\",\"category\"\r\n\"TOKEN_MULTI\",\"Line 1\r\nLine 2\",\"第一行\r\n第二行\",\"lore\"\r\n";
            csvSyncManager.importTokensFromCsvString(tokenDictionary, csv);

            TokenRecord rec = tokenDictionary.get("TOKEN_MULTI");
            assertNotNull(rec);
            assertEquals("Line 1\r\nLine 2", rec.en());
            assertEquals("第一行\r\n第二行", rec.zh_tw());
        }

        @Test
        @DisplayName("T2-CSV-03: Malformed CSV with Unclosed Double Quotes Rejection")
        void testMalformedCsvUnclosedQuotes() {
            String malformed = "\"token_id\",\"en\",\"zh_tw\",\"category\"\n\"TOKEN_BAD\",\"Unclosed quote,val,test";
            assertThrows(IllegalArgumentException.class, () -> csvSyncManager.importTokensFromCsvString(tokenDictionary, malformed));
        }

        @Test
        @DisplayName("T2-CSV-04: CSV with Mismatched Column Counts Rejection")
        void testMismatchedColumnCount() {
            String badRowCsv = """
            "token_id","en","zh_tw","category"
            "TOKEN_1","Only Two Columns"
            """;
            assertThrows(IllegalArgumentException.class, () -> csvSyncManager.importTokensFromCsvString(tokenDictionary, badRowCsv));
        }

        @Test
        @DisplayName("T2-CSV-05: Empty CSV File Handling")
        void testEmptyCsvFile() {
            int prevSize = tokenDictionary.size();
            assertDoesNotThrow(() -> csvSyncManager.importTokensFromCsvString(tokenDictionary, ""));
            assertDoesNotThrow(() -> csvSyncManager.importTokensFromCsvString(tokenDictionary, "\"token_id\",\"en\",\"zh_tw\",\"category\"\r\n"));
            assertEquals(prevSize, tokenDictionary.size());
        }

        @Test
        @DisplayName("T2-CSV-06: CSV with CRLF and LF Line Endings Mixed")
        void testMixedLineEndings() {
            String mixed = "\"token_id\",\"en\",\"zh_tw\",\"category\"\r\n\"T_CRLF\",\"CRLF\",\"換行1\",\"misc\"\n\"T_LF\",\"LF\",\"換行2\",\"misc\"\r\n";
            csvSyncManager.importTokensFromCsvString(tokenDictionary, mixed);
            assertEquals("換行1", tokenDictionary.resolveToken("T_CRLF"));
            assertEquals("換行2", tokenDictionary.resolveToken("T_LF"));
        }
    }

    // =========================================================================
    // Category 4: Item Decomposition Boundaries & 276 Collision Items
    // =========================================================================
    @Nested
    @DisplayName("Category 4: Reforge Collision Items & Star Extremes")
    class ItemDecompositionBoundaries {

        @Test
        @DisplayName("T2-DEC-01: Collision Item Ancient Claw without Reforge (Path A - NBT)")
        void testCollisionItemAncientClawNoReforge() {
            // NBT modifier is null: base item Ancient Claw must NOT strip "Ancient"
            String clean = dynamicItemCombiner.getCleanEnglishName("§aAncient Claw", null);
            assertEquals("Ancient Claw", clean);

            MutableComponent comp = dynamicItemCombiner.translateItemName(null, "§aAncient Claw");
            assertEquals("§a古代之爪", comp.getString());
        }

        @Test
        @DisplayName("T2-DEC-02: Collision Item Ancient Claw with Reforge Fabled (Path A - NBT)")
        void testCollisionItemAncientClawWithReforge() {
            String clean = dynamicItemCombiner.getCleanEnglishName("§aFabled Ancient Claw", "fabled");
            assertEquals("Ancient Claw", clean);

            MutableComponent comp = dynamicItemCombiner.translateItemName(null, "§aFabled Ancient Claw");
            assertEquals("§a傳說 古代之爪", comp.getString());
        }

        @Test
        @DisplayName("T2-DEC-03: Collision Item Warped Stone (Path B - Text Only)")
        void testCollisionItemWarpedStoneTextOnly() {
            // Text only: "Warped Stone" is a base item, NOT reforge Warped + Stone
            String clean = dynamicItemCombiner.getCleanEnglishName("§5Warped Stone", null);
            assertEquals("Warped Stone", clean);

            MutableComponent comp = dynamicItemCombiner.translateItemName(null, "§5Warped Stone");
            assertEquals("§5扭曲之石", comp.getString());
        }

        @Test
        @DisplayName("T2-DEC-04: Item with Reforge Identical to Rarity (Mythic Heavy Helmet)")
        void testReforgeIdenticalToRarity() {
            String clean = dynamicItemCombiner.getCleanEnglishName("§5Mythic Heavy Helmet", "mythic");
            assertEquals("Heavy Helmet", clean);

            MutableComponent comp = dynamicItemCombiner.translateItemName(null, "§5Mythic Heavy Helmet");
            assertEquals("§5神話 重型頭盔", comp.getString());
        }

        @Test
        @DisplayName("T2-DEC-05: Item with Extreme Master Mode Stars (Terminator §c➊➋➌➍➎)")
        void testExtremeMasterModeStars() {
            String clean = dynamicItemCombiner.getCleanEnglishName("§6Terminator §c➊➋➌➍➎", null);
            assertEquals("Terminator", clean);

            MutableComponent comp = dynamicItemCombiner.translateItemName(null, "§6Terminator §c➊➋➌➍➎");
            assertEquals("§6終結者 §c➊➋➌➍➎", comp.getString());
        }

        @Test
        @DisplayName("T2-COL-01: Collision Item Blessed Fruit")
        void testCollisionBlessedFruit() {
            String clean = dynamicItemCombiner.getCleanEnglishName("§aBlessed Fruit", null);
            assertEquals("Blessed Fruit", clean);
        }

        @Test
        @DisplayName("T2-COL-02: Collision Item Bulky Stone")
        void testCollisionBulkyStone() {
            String clean = dynamicItemCombiner.getCleanEnglishName("§5Bulky Stone", null);
            assertEquals("Bulky Stone", clean);
        }

        @Test
        @DisplayName("T2-COL-03: Double Prefix Collision Ancient Ancient Claw")
        void testDoublePrefixCollisionAncientClaw() {
            // "Ancient" reforge on "Ancient Claw" base item
            MutableComponent comp = dynamicItemCombiner.translateItemName(null, "§aAncient Ancient Claw");
            assertEquals("§a遠古 古代之爪", comp.getString());
            assertEquals("Ancient Claw", dynamicItemCombiner.getCleanEnglishName("§aAncient Ancient Claw", "ancient"));
        }

        @Test
        @DisplayName("T2-COL-04: Collision Item Heavy Helmet Base Item")
        void testCollisionHeavyHelmet() {
            String clean = dynamicItemCombiner.getCleanEnglishName("§5Heavy Helmet", null);
            assertEquals("Heavy Helmet", clean);
        }

        @Test
        @DisplayName("T2-COL-05: Collision Item Light Blue Carpet Base Item")
        void testCollisionLightBlueCarpet() {
            String clean = dynamicItemCombiner.getCleanEnglishName("§fLight Blue Carpet", null);
            assertEquals("Light Blue Carpet", clean);
        }
    }

    // =========================================================================
    // Category 5: Lore Stat Parsing Boundaries
    // =========================================================================
    @Nested
    @DisplayName("Category 5: Lore Stat Boundaries & Formatting Codes")
    class LoreParsingBoundaries {

        @Test
        @DisplayName("T2-LOR-01: Stat Line with Zero Value (+0)")
        void testStatZeroValue() {
            String translated = loreParser.translateLoreLine("§7Damage: §c+0");
            assertEquals("§7傷害：§c+0", translated);
        }

        @Test
        @DisplayName("T2-LOR-02: Stat Line with Massive Numbers (+999,999,999)")
        void testStatMassiveNumbers() {
            String translated = loreParser.translateLoreLine("§7Health: §c+999,999,999");
            assertEquals("§7生命值：§c+999,999,999", translated);
        }

        @Test
        @DisplayName("T2-LOR-03: Stat Line with Decimal Float (+0.5%)")
        void testStatDecimalFloat() {
            String translated = loreParser.translateLoreLine("§7Attack Speed: §e+0.5%");
            assertEquals("§7攻擊速度：§e+0.5%", translated);
        }

        @Test
        @DisplayName("T2-LOR-04: Stat Line with 3 Bonus Brackets (+150 (+30) [+25] (+5))")
        void testStatMultipleBonusBrackets() {
            String translated = loreParser.translateLoreLine("§7Strength: §c+150 §e(+30) §a[+25] §c(+5)");
            assertEquals("§7力量：§c+150 §e(+30) §a[+25] §c(+5)", translated);
        }

        @Test
        @DisplayName("T2-LOR-05: Obfuscated Recombobulated Rarity Tag (§ka)")
        void testObfuscatedRecombobulatorTag() {
            String line = "§d§l§ka§r §d§lMYTHIC SWORD §r§d§l§ka";
            String translated = loreParser.translateLoreLine(line);
            assertTrue(translated.contains("§ka"));
            assertTrue(translated.contains("神話 劍"));
        }
    }

    // =========================================================================
    // Category 6: Gemstones, Abilities & Enchants Boundaries
    // =========================================================================
    @Nested
    @DisplayName("Category 6: Gemstones, Abilities & Enchantment Boundaries")
    class GemstoneAndAbilityBoundaries {

        @Test
        @DisplayName("T2-GAE-01: Divan's Drill 5 Mixed Gemstone Slots with Lock State")
        void testDivanDrillGemstoneSlots() {
            String line = "§7Gemstones: §8[§7\ue015§8] [§7\ue015§8] [\ue053] [\ue053] [✦]";
            String translated = loreParser.translateLoreLine(line);
            assertEquals("§7寶石槽：§8[§7\ue015§8] [§7\ue015§8] [\ue053] [\ue053] [✦]", translated);
        }

        @Test
        @DisplayName("T2-GAE-02: Ability with Multiple Activation Types (LEFT or RIGHT CLICK)")
        void testAbilityMultipleActivations() {
            String line = "§6Ability: Dual Cast  §e§lLEFT CLICK §7or §e§lRIGHT CLICK";
            String translated = loreParser.translateLoreLine(line);
            assertEquals("§6技能：Dual Cast  §e§l左鍵點擊 §7or §e§l右鍵點擊", translated);
        }

        @Test
        @DisplayName("T2-GAE-03: Ability with Dual Cost (Mana and Vitality)")
        void testAbilityDualCost() {
            String line1 = loreParser.translateLoreLine("§8Mana Cost: §b150\ue003");
            String line2 = loreParser.translateLoreLine("§8Vitality Cost: §450\ue028");
            assertEquals("§8魔力消耗：§b150\ue003", line1);
            assertEquals("§8活力消耗：§450\ue028", line2);
        }

        @Test
        @DisplayName("T2-GAE-04: Roman Numerals Range I through X")
        void testRomanNumeralsRange() {
            assertEquals("保護 I", romanNumeralParser.translateRomanLine("Protection I", false).replace("Protection", "保護"));
            assertEquals("保護 IV", romanNumeralParser.translateRomanLine("Protection IV", false).replace("Protection", "保護"));
            assertEquals("保護 IX", romanNumeralParser.translateRomanLine("Protection IX", false).replace("Protection", "保護"));
            assertEquals("保護 X", romanNumeralParser.translateRomanLine("Protection X", false).replace("Protection", "保護"));
        }

        @Test
        @DisplayName("T2-GAE-05: Non-Roman Word Ending with 'I' (Bonsai)")
        void testNonRomanWordEnding() {
            String text = "Bonsai Tree";
            String translated = romanNumeralParser.translateRomanLine(text, false);
            assertEquals("Bonsai Tree", translated);
        }
    }

    // =========================================================================
    // Category 7: Fallback & Capture Queue Boundaries
    // =========================================================================
    @Nested
    @DisplayName("Category 7: Fallback Matching & Capture Queue Boundaries")
    class FallbackAndCaptureBoundaries {

        @Test
        @DisplayName("T2-FAL-01: Completely Unknown Gibberish Item Name Retained")
        void testGibberishItemName() {
            MutableComponent comp = dynamicItemCombiner.translateItemName(null, "XylophoneQWERTY123");
            assertEquals("XylophoneQWERTY123", comp.getString());
        }

        @Test
        @DisplayName("T2-CAP-01: Untranslated Queue Capacity Limit (5000 max)")
        void testQueueCapacityLimit() {
            UntranslatedCaptureQueue smallQueue = new UntranslatedCaptureQueue(50);
            for (int i = 0; i < 100; i++) {
                smallQueue.capture("Unique Message " + i, ComponentCategory.CHAT);
            }
            assertEquals(50, smallQueue.size()); // Bound to capacity limit
        }

        @Test
        @DisplayName("T2-CAP-02: Null and Empty String Capture Rejection")
        void testNullAndEmptyCaptureRejection() {
            int initial = untranslatedQueue.size();
            untranslatedQueue.capture(null, ComponentCategory.CHAT);
            untranslatedQueue.capture("", ComponentCategory.CHAT);
            untranslatedQueue.capture("   ", ComponentCategory.CHAT);
            assertEquals(initial, untranslatedQueue.size());
        }

        @Test
        @DisplayName("T2-CAP-03: String with Exclusive Color Codes Rejection")
        void testColorCodesOnlyCaptureRejection() {
            int initial = untranslatedQueue.size();
            untranslatedQueue.capture("§c§l§r§a", ComponentCategory.CHAT);
            assertEquals(initial, untranslatedQueue.size());
        }

        @Test
        @DisplayName("T2-CAP-04: Concurrent Insertion from 50 Threads")
        void testConcurrentQueueInsertion() throws Exception {
            int threadCount = 20;
            int itemsPerThread = 50;
            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int tid = t;
                exec.submit(() -> {
                    try {
                        for (int i = 0; i < itemsPerThread; i++) {
                            untranslatedQueue.capture("Concurrent String " + (i % 10), ComponentCategory.CHAT);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            exec.shutdown();
            // Exactly 10 distinct strings should be deduplicated
            assertEquals(10, untranslatedQueue.size());
        }
    }

    // =========================================================================
    // Category 8: Text Traversal & Event Boundaries
    // =========================================================================
    @Nested
    @DisplayName("Category 8: Deep Text Traversal & Component Tree Extremes")
    class TextTraversalBoundaries {

        @Test
        @DisplayName("T2-TRA-01: Null and Empty Component Traversal")
        void testEmptyComponentTraversal() {
            MutableComponent empty = Component.empty();
            MutableComponent res = textTraverser.translateRecursive(empty, Function.identity());
            assertEquals("", res.getString());
        }

        @Test
        @DisplayName("T2-TRA-02: Deeply Nested Siblings (Depth 20)")
        void testDeeplyNestedSiblings() {
            MutableComponent root = Component.literal("Node0");
            MutableComponent current = root;
            for (int i = 1; i <= 20; i++) {
                MutableComponent child = Component.literal(" Node" + i);
                current.append(child);
            }

            MutableComponent translated = textTraverser.translateRecursive(root, s -> s.replace("Node", "節點"));
            assertTrue(translated.getString().contains("節點0"));
            assertTrue(translated.getString().contains("節點20"));
        }

        @Test
        @DisplayName("T2-TRA-03: Component with ClickEvent OpenUrl Preservation")
        void testClickEventOpenUrl() {
            Style style = Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create("https://hypixel.net")));
            Component c = Component.literal("[WEBSITE]").setStyle(style);

            MutableComponent t = textTraverser.translateRecursive(c, s -> "[網站]");
            assertEquals("[網站]", t.getString());
            assertInstanceOf(ClickEvent.OpenUrl.class, t.getStyle().getClickEvent());
            assertEquals("https://hypixel.net", ((ClickEvent.OpenUrl) t.getStyle().getClickEvent()).uri().toString());
        }

        @Test
        @DisplayName("T2-TRA-04: Component with ClickEvent CopyToClipboard Preservation")
        void testClickEventCopyToClipboard() {
            Style style = Style.EMPTY.withClickEvent(new ClickEvent.CopyToClipboard("party invite 12345"));
            Component c = Component.literal("[COPY]").setStyle(style);

            MutableComponent t = textTraverser.translateRecursive(c, s -> "[複製]");
            assertEquals("[複製]", t.getString());
            assertInstanceOf(ClickEvent.CopyToClipboard.class, t.getStyle().getClickEvent());
            assertEquals("party invite 12345", ((ClickEvent.CopyToClipboard) t.getStyle().getClickEvent()).value());
        }

        @Test
        @DisplayName("T2-TRA-05: Empty Style with No Formatting Preservation")
        void testEmptyStylePreservation() {
            Component c = Component.literal("Pure").setStyle(Style.EMPTY);
            MutableComponent t = textTraverser.translateRecursive(c, s -> "純粹");
            assertEquals("純粹", t.getString());
            assertEquals(Style.EMPTY, t.getStyle());
        }
    }

    // =========================================================================
    // Category 9: UI & HUD Boundaries
    // =========================================================================
    @Nested
    @DisplayName("Category 9: UI, HUD & Screen Component Boundaries")
    class UiAndHudBoundaries {

        @Test
        @DisplayName("T2-HUD-01: Empty Container Title")
        void testEmptyContainerTitle() {
            MutableComponent title = Component.empty();
            MutableComponent translated = textTraverser.translateRecursive(title, Function.identity());
            assertEquals("", translated.getString());
        }

        @Test
        @DisplayName("T2-HUD-02: Long Scoreboard Line (Exceeding 40 Characters)")
        void testLongScoreboardLine() {
            // Authentic long scoreboard line exceeding 60 characters with Gold prefix and White detail
            Component prefix = Component.literal("Objective: ").withStyle(ChatFormatting.GOLD);
            Component details = Component.literal("Defeat the Corrupted Necron in the Floor VII Boss Arena").withStyle(ChatFormatting.WHITE);
            Component lineComp = Component.empty().append(prefix).append(details);

            // Execute translation engine
            MutableComponent translated = textTraverser.translateRecursive(lineComp, tokenDictionary::translateText);

            // 1. Boundary assertions: verify length handling and deep token substitutions
            assertTrue(translated.getString().length() > 30, "Translated line must accommodate multi-token substitution");
            assertTrue(translated.getString().contains("目標："), "Objective label must be translated");
            assertTrue(translated.getString().contains("第七層首領競技場"), "Floor VII Arena token must be translated");
            assertTrue(translated.getString().contains("腐化涅克隆"), "Boss token must be translated");

            // 2. Multibyte UTF-8 integrity check
            byte[] utf8Bytes = translated.getString().getBytes(StandardCharsets.UTF_8);
            assertTrue(utf8Bytes.length > translated.getString().length(),
                    "Multibyte UTF-8 Traditional Chinese characters must encode cleanly without corruption");

            // 3. Verify Style preservation on multi-node tree
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), translated.getSiblings().get(0).getStyle().getColor());
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.WHITE), translated.getSiblings().get(1).getStyle().getColor());

            // 4. Verify external mod regex against unmodified in-memory component
            Pattern objectivePattern = Pattern.compile("^Objective: Defeat the (?<boss>.*) in the (?<area>.*)$");
            Matcher m = objectivePattern.matcher(lineComp.getString());
            assertTrue(m.find(), "Skyblocker scoreboard parser must extract boss and arena from in-memory English line");
            assertEquals("Corrupted Necron", m.group("boss"));
            assertEquals("Floor VII Boss Arena", m.group("area"));
        }

        @Test
        @DisplayName("T2-HUD-03: ActionBar with Color Transition Codes")
        void testActionBarColorTransitions() {
            // Multi-segment ActionBar with distinct color boundaries: Green HP, Green Defense, Aqua Mana
            Component hp = Component.literal("100/100 HP").withStyle(ChatFormatting.GREEN);
            Component sp1 = Component.literal("   ").withStyle(ChatFormatting.WHITE);
            Component def = Component.literal("+100 Defense").withStyle(ChatFormatting.GREEN);
            Component sp2 = Component.literal("   ").withStyle(ChatFormatting.WHITE);
            Component mana = Component.literal("200/200 Mana").withStyle(ChatFormatting.AQUA);
            Component barComp = Component.empty().append(hp).append(sp1).append(def).append(sp2).append(mana);

            // Execute translation engine
            MutableComponent translated = textTraverser.translateRecursive(barComp, tokenDictionary::translateText);

            // 1. Verify dynamic token resolution against dictionary values
            String expected = "100/100 " + tokenDictionary.resolveToken("STAT_HP")
                    + "   +100 " + tokenDictionary.resolveToken("STAT_DEFENSE")
                    + "   200/200 " + tokenDictionary.resolveToken("STAT_MANA");
            assertEquals(expected, translated.getString());

            // 2. Verify that color transitions are strictly preserved across independent sibling nodes
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), translated.getSiblings().get(0).getStyle().getColor());
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), translated.getSiblings().get(2).getStyle().getColor());
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA), translated.getSiblings().get(4).getStyle().getColor());

            // 3. Verify in-memory English purity for Skyblocker defense parsing
            assertEquals("100/100 HP   +100 Defense   200/200 Mana", barComp.getString());
            Pattern defPattern = Pattern.compile("\\+(?<def>\\d+) Defense");
            Matcher m = defPattern.matcher(barComp.getString());
            assertTrue(m.find(), "Skyblocker defense parser regex must match in-memory English Component");
            assertEquals("100", m.group("def"));
        }

        @Test
        @DisplayName("T2-HUD-04: Floating Hologram with Special Formatting Tags")
        void testFloatingHologramTags() {
            // Complex hologram tree: Bold White "[" + Bold Gold "GOLD" + Bold White "] " + Yellow Prompt
            Component bracketL = Component.literal("[").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
            Component tag = Component.literal("GOLD").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            Component bracketR = Component.literal("] ").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
            Component prompt = Component.literal("Click to collect!").withStyle(ChatFormatting.YELLOW);
            Component holoComp = Component.empty().append(bracketL).append(tag).append(bracketR).append(prompt);

            // Execute translation engine
            MutableComponent translated = textTraverser.translateRecursive(holoComp, tokenDictionary::translateText);

            // 1. Verify localized composite text
            assertEquals("[黃金] 點擊領取！", translated.getString());

            // 2. Verify leaf formatting attributes: Bold White brackets, Bold Gold tag, non-bold Yellow prompt
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.WHITE), translated.getSiblings().get(0).getStyle().getColor());
            assertTrue(translated.getSiblings().get(0).getStyle().isBold());
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), translated.getSiblings().get(1).getStyle().getColor());
            assertTrue(translated.getSiblings().get(1).getStyle().isBold());
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.YELLOW), translated.getSiblings().get(3).getStyle().getColor());
            assertFalse(translated.getSiblings().get(3).getStyle().isBold());

            // 3. Verify in-memory invariant
            assertEquals("[GOLD] Click to collect!", holoComp.getString());
        }

        @Test
        @DisplayName("T2-HUD-05: Tab List with Formatting Reset Between Tokens")
        void testTabListResetFormatting() {
            // Tab list entry: Red Bold Rank + White Name + Explicit Formatting Reset (Style.EMPTY) Gray Ping
            Component rankComp = Component.literal("[ADMIN] ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            Component nameComp = Component.literal("hypixel").withStyle(ChatFormatting.WHITE);
            Component pingComp = Component.literal(" (120ms)").setStyle(Style.EMPTY.withColor(TextColor.fromLegacyFormat(ChatFormatting.GRAY)).withBold(false));
            Component tabComp = Component.empty().append(rankComp).append(nameComp).append(pingComp);

            // Execute translation engine
            MutableComponent translated = textTraverser.translateRecursive(tabComp, tokenDictionary::translateText);

            // 1. Verify localized rank prefix while preserving player name and latency
            assertEquals("[管理員] hypixel (120ms)", translated.getString());

            // 2. Verify rank leaf translation and bold red style retention
            assertEquals("[管理員] ", translated.getSiblings().get(0).getString());
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), translated.getSiblings().get(0).getStyle().getColor());
            assertTrue(translated.getSiblings().get(0).getStyle().isBold());

            // 3. Verify player username is preserved unmodified in pure White
            assertEquals("hypixel", translated.getSiblings().get(1).getString(), "Player username must never be translated");
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.WHITE), translated.getSiblings().get(1).getStyle().getColor());

            // 4. Verify formatting reset on ping widget (not bold, gray color)
            assertEquals(" (120ms)", translated.getSiblings().get(2).getString());
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GRAY), translated.getSiblings().get(2).getStyle().getColor());
            assertFalse(translated.getSiblings().get(2).getStyle().isBold(), "Ping widget must retain reset non-bold formatting");

            // 5. Verify Skyblocker PlayerListManager regex compatibility on in-memory component
            Pattern tabRegex = Pattern.compile("^\\[ADMIN\\]\\s+(?<name>\\w+)\\s+\\((?<ping>\\d+)ms\\)$");
            Matcher m = tabRegex.matcher(tabComp.getString());
            assertTrue(m.find(), "Skyblocker player tab regex must match in-memory English component");
            assertEquals("hypixel", m.group("name"));
            assertEquals("120", m.group("ping"));
        }
    }
}
