package com.hypixel.translation.e2e;

import com.hypixel.translation.core.*;
import com.hypixel.translation.engine.*;
import com.hypixel.translation.pipeline.SchemaNormalizer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tier 5: Adversarial Coverage Hardening, Concurrency Stress & Vulnerability Probes.
 *
 * Authored by Teamwork Empirical Challenger (Milestone 5).
 * Validates deep boundary conditions, cyclic references, thread contention,
 * malformed inputs, and empirical defect reproduction across Core, Engine, and Pipeline.
 */
@Tag("tier5")
@Tag("adversarial")
public class Tier5AdversarialHardeningTest extends BaseE2ETest {

    // =========================================================================
    // Suite 1: TokenDictionary Adversarial & Cyclic Recursion Tests
    // =========================================================================
    @Nested
    @DisplayName("Suite 1: TokenDictionary Adversarial & Cyclic Recursion Tests")
    class TokenDictionaryAdversarialTests {

        @Test
        @DisplayName("ADV-TOK-01: Direct Self-Referencing Token Does Not Cause Infinite Loop")
        void testDirectSelfRecursionCycle() {
            TokenDictionary dict = new TokenDictionary();
            dict.registerToken(new TokenRecord("LOOP", "Loop", "{LOOP}", "", "§c", "test"));

            // Must terminate without StackOverflowError and return stable string
            String resolved = assertDoesNotThrow(() -> dict.resolveString("{LOOP}"));
            assertNotNull(resolved);
            assertEquals("{LOOP}", resolved);
        }

        @Test
        @DisplayName("ADV-TOK-02: Mutual Two-Token Cycle (A -> B -> A) Terminates Safely")
        void testMutualTwoTokenCycle() {
            TokenDictionary dict = new TokenDictionary();
            dict.registerToken(new TokenRecord("TOKEN_A", "A", "Word {TOKEN_B}", "", "§a", "test"));
            dict.registerToken(new TokenRecord("TOKEN_B", "B", "Word {TOKEN_A}", "", "§b", "test"));

            String resolvedA = assertDoesNotThrow(() -> dict.resolveString("{TOKEN_A}"));
            assertNotNull(resolvedA);
            // Verify recursion terminated and cycle was broken
            assertTrue(resolvedA.contains("Word"));

            String resolvedB = assertDoesNotThrow(() -> dict.resolveString("{TOKEN_B}"));
            assertNotNull(resolvedB);
            assertTrue(resolvedB.contains("Word"));
        }

        @Test
        @DisplayName("ADV-TOK-03: Three-Token Indirect Cycle (A -> B -> C -> A) Terminates")
        void testThreeTokenIndirectCycle() {
            TokenDictionary dict = new TokenDictionary();
            dict.registerToken(new TokenRecord("CYC_1", "One", "P1 {CYC_2}", "", "", "test"));
            dict.registerToken(new TokenRecord("CYC_2", "Two", "P2 {CYC_3}", "", "", "test"));
            dict.registerToken(new TokenRecord("CYC_3", "Three", "P3 {CYC_1}", "", "", "test"));

            String resolved = assertDoesNotThrow(() -> dict.resolveString("Start: {CYC_1}"));
            assertNotNull(resolved);
            assertTrue(resolved.contains("P1") && resolved.contains("P2") && resolved.contains("P3"));
        }

        @Test
        @DisplayName("ADV-TOK-04: Ultra-Deep Token Chaining Beyond Max Depth (Depth > 5)")
        void testUltraDeepTokenChainingDepthLimit() {
            TokenDictionary dict = new TokenDictionary();
            for (int i = 0; i < 15; i++) {
                String current = "CHAIN_" + i;
                String next = (i < 14) ? "{CHAIN_" + (i + 1) + "}" : "FINAL_VALUE";
                dict.registerToken(new TokenRecord(current, current, next, "", "", "test"));
            }

            // MAX_RECURSION_DEPTH is 5. Expansion must stop at depth 5 without throwing StackOverflowError
            String resolved = assertDoesNotThrow(() -> dict.resolveString("{CHAIN_0}"));
            assertNotNull(resolved);
            // It should not reach FINAL_VALUE because depth is capped at 5
            assertTrue(resolved.contains("{CHAIN_"));
        }

        @Test
        @DisplayName("ADV-TOK-05: Special Suffix Boundaries (_GLYPH, _COLOR, _EN) and Edge Keys")
        void testSpecialSuffixBoundaries() {
            TokenDictionary dict = new TokenDictionary();
            dict.registerToken(new TokenRecord("STAT_DAMAGE", "Damage", "傷害", "\ue050", "§c", "stat"));

            assertEquals("\ue050", dict.resolveString("{STAT_DAMAGE_GLYPH}"));
            assertEquals("§c", dict.resolveString("{STAT_DAMAGE_COLOR}"));
            assertEquals("Damage", dict.resolveString("{STAT_DAMAGE_EN}"));
            assertEquals("傷害", dict.resolveString("{STAT_DAMAGE}"));

            // Adversarial test: Isolated suffixes without base token must not crash with IndexOutOfBounds
            assertEquals("{_GLYPH}", dict.resolveString("{_GLYPH}"));
            assertEquals("{_COLOR}", dict.resolveString("{_COLOR}"));
            assertEquals("{_EN}", dict.resolveString("{_EN}"));
        }

        @Test
        @DisplayName("ADV-TOK-06: Malformed Token Placeholders In Templates")
        void testMalformedTokenPlaceholders() {
            TokenDictionary dict = new TokenDictionary();
            dict.registerToken(new TokenRecord("VALID", "Valid", "有效", "", "", "test"));

            assertEquals("", dict.resolveString(""));
            assertEquals("{", dict.resolveString("{"));
            assertEquals("}", dict.resolveString("}"));
            assertEquals("{}", dict.resolveString("{}"));
            assertEquals("{UNCLOSED", dict.resolveString("{UNCLOSED"));
            assertEquals("UNOPENED}", dict.resolveString("UNOPENED}"));
            assertEquals("{NOT A TOKEN}", dict.resolveString("{NOT A TOKEN}"));
            assertEquals("{123_456}", dict.resolveString("{123_456}")); // Not registered, stays literal
            assertEquals("Prefix 有效 Suffix", dict.resolveString("Prefix {VALID} Suffix"));
        }

        @Test
        @DisplayName("ADV-TOK-07: Cache Capacity Saturation Under 5,000 Distinct Templates")
        void testCacheCapacityStress() {
            TokenDictionary dict = new TokenDictionary();
            dict.registerToken(new TokenRecord("FOO", "Foo", "巴", "", "", "test"));

            // MAX_CACHE_SIZE is 4096. Inserting 5000 distinct items must not lead to OOM
            for (int i = 0; i < 5000; i++) {
                dict.resolveString("Template " + i + " {FOO}");
            }
            // Verify cache cleared safely
            dict.clearCache();
            assertEquals("巴", dict.resolveString("{FOO}"));
        }

        @Test
        @DisplayName("ADV-TOK-08: High-Concurrency Thread Contention on TokenDictionary")
        void testConcurrentReadWriteStress() throws InterruptedException {
            TokenDictionary dict = new TokenDictionary();
            int threads = 10;
            int opsPerThread = 200;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            List<Throwable> errors = new CopyOnWriteArrayList<>();

            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            if (threadId % 2 == 0) {
                                // Writer
                                String key = "CONC_" + threadId + "_" + (i % 20);
                                dict.registerToken(new TokenRecord(key, "En" + i, "中" + i, "", "", "stat"));
                            } else {
                                // Reader
                                String key = "CONC_" + (threadId - 1) + "_" + (i % 20);
                                dict.resolveString("Check {" + key + "}");
                            }
                        }
                    } catch (Throwable e) {
                        errors.add(e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent test timed out");
            executor.shutdownNow();
            assertTrue(errors.isEmpty(), "Concurrent errors occurred: " + errors);
        }

        @Test
        @DisplayName("ADV-TOK-09: TokenRecord Compact Constructor Defensively Normalizes Null Values")
        void testTranslateTextNullZhTwDefensiveNormalization() {
            TokenDictionary dict = new TokenDictionary();
            // TokenRecord compact constructor normalizes null zh_tw to ""
            TokenRecord record = new TokenRecord("NULL_ZH", "FragileItem", null, null, null, null);
            assertEquals("", record.zh_tw());
            assertEquals("general", record.category());

            dict.registerToken(record);
            // translateText safely replaces with "" without NullPointerException
            String translated = dict.translateText("I have FragileItem in hand");
            assertEquals("I have  in hand", translated);
        }
    }

    // =========================================================================
    // Suite 2: CsvSyncManager Adversarial & Malformed Data Tests
    // =========================================================================
    @Nested
    @DisplayName("Suite 2: CsvSyncManager Adversarial & Malformed Data Tests")
    class CsvSyncAdversarialTests {

        @Test
        @DisplayName("ADV-CSV-01: Unterminated Quote Throws IllegalArgumentException")
        void testCsvUnterminatedQuote() {
            CsvSyncManager sync = new CsvSyncManager();
            TokenDictionary dict = new TokenDictionary();
            String badCsv = "\"token_id\",\"en\",\"zh_tw\",\"category\"\r\n\"STAT_TEST\",\"Unterminated field,傷害,stat";

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> sync.importTokensFromCsvString(dict, badCsv));
            assertTrue(ex.getMessage().contains("Unterminated quote"));
        }

        @Test
        @DisplayName("ADV-CSV-02: Insufficient Columns Row Throws IllegalArgumentException")
        void testCsvInsufficientColumns() {
            CsvSyncManager sync = new CsvSyncManager();
            TokenDictionary dict = new TokenDictionary();
            String truncatedCsv = "\"token_id\",\"en\",\"zh_tw\",\"category\"\r\n\"STAT_A\",\"Damage\"";

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> sync.importTokensFromCsvString(dict, truncatedCsv));
            assertTrue(ex.getMessage().contains("expected at least 4 columns"));
        }

        @Test
        @DisplayName("ADV-CSV-03: Empty Token ID Throws IllegalArgumentException")
        void testCsvEmptyTokenId() {
            CsvSyncManager sync = new CsvSyncManager();
            TokenDictionary dict = new TokenDictionary();
            String emptyIdCsv = "\"token_id\",\"en\",\"zh_tw\",\"category\"\r\n\"   \",\"Damage\",\"傷害\",\"stat\"";

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> sync.importTokensFromCsvString(dict, emptyIdCsv));
            assertTrue(ex.getMessage().contains("token_id cannot be empty"));
        }

        @Test
        @DisplayName("ADV-CSV-04: Transactional Atomicity Rollback On Malformed Row")
        void testCsvTransactionalRollback() {
            CsvSyncManager sync = new CsvSyncManager();
            TokenDictionary dict = new TokenDictionary();
            dict.registerToken(new TokenRecord("INITIAL", "Initial", "初始", "", "", "test"));
            int initialSize = dict.size();

            // Row 1 is valid, Row 2 is malformed (<4 columns)
            String partialCsv = "\"token_id\",\"en\",\"zh_tw\",\"category\"\r\n" +
                    "\"NEW_1\",\"One\",\"一\",\"stat\"\r\n" +
                    "\"NEW_2_MALFORMED\"";

            assertThrows(IllegalArgumentException.class, () -> sync.importTokensFromCsvString(dict, partialCsv));

            // Atomicity check: NEW_1 must NOT have been committed to dictionary!
            assertFalse(dict.containsToken("NEW_1"), "Transactional violation: partial commit on CSV failure!");
            assertEquals(initialSize, dict.size());
        }

        @Test
        @DisplayName("ADV-CSV-05: Escaped Double Quotes and Commas RFC 4180 Roundtrip")
        void testCsvEscapedQuotesAndCommasFidelity() {
            CsvSyncManager sync = new CsvSyncManager();
            TokenDictionary dict = new TokenDictionary();
            dict.registerToken(new TokenRecord("QUOTE_COMMA", "Hello, \"World\"", "你好，「世界」", "", "", "general"));

            String exported = sync.exportTokensToCsvString(dict);
            assertTrue(exported.contains("\"\"World\"\""));

            TokenDictionary importedDict = new TokenDictionary();
            sync.importTokensFromCsvString(importedDict, exported);

            TokenRecord rec = importedDict.get("QUOTE_COMMA");
            assertNotNull(rec);
            assertEquals("Hello, \"World\"", rec.en());
            assertEquals("你好，「世界」", rec.zh_tw());
        }

        @Test
        @DisplayName("ADV-CSV-06: Multiline Field With Embedded CRLF Preserved")
        void testCsvMultilineFieldFidelity() {
            CsvSyncManager sync = new CsvSyncManager();
            TokenDictionary dict = new TokenDictionary();
            dict.registerToken(new TokenRecord("MULTILINE", "Line1\r\nLine2", "第一行\r\n第二行", "", "", "general"));

            String exported = sync.exportTokensToCsvString(dict);
            TokenDictionary importedDict = new TokenDictionary();
            sync.importTokensFromCsvString(importedDict, exported);

            TokenRecord rec = importedDict.get("MULTILINE");
            assertNotNull(rec);
            assertEquals("Line1\r\nLine2", rec.en());
            assertEquals("第一行\r\n第二行", rec.zh_tw());
        }

        @Test
        @DisplayName("ADV-CSV-07: UTF-8 BOM Stripping Resilience")
        void testCsvUtf8BomHandling() {
            CsvSyncManager sync = new CsvSyncManager();
            TokenDictionary dict = new TokenDictionary();
            String csvWithBom = CsvSyncManager.UTF8_BOM + "\"token_id\",\"en\",\"zh_tw\",\"category\"\r\n\"BOM_TOKEN\",\"Bom\",\"炸彈\",\"general\"";

            sync.importTokensFromCsvString(dict, csvWithBom);
            assertTrue(dict.containsToken("BOM_TOKEN"));
            assertEquals("炸彈", dict.get("BOM_TOKEN").zh_tw());
        }

        @Test
        @DisplayName("ADV-CSV-08: High-Volume 2,000 Rows CSV Import Stress")
        void testLargeVolumeCsvImportStress() {
            CsvSyncManager sync = new CsvSyncManager();
            TokenDictionary dict = new TokenDictionary();
            StringBuilder sb = new StringBuilder();
            sb.append("\"token_id\",\"en\",\"zh_tw\",\"category\"\r\n");
            for (int i = 0; i < 2000; i++) {
                sb.append("\"TOK_").append(i).append("\",\"English ").append(i)
                  .append("\",\"中文 ").append(i).append("\",\"category\"\r\n");
            }

            long start = System.currentTimeMillis();
            sync.importTokensFromCsvString(dict, sb.toString());
            long duration = System.currentTimeMillis() - start;

            assertEquals(2000, dict.size());
            assertTrue(duration < 2000, "2000 rows CSV import took too long: " + duration + "ms");
        }

        @Test
        @DisplayName("ADV-CSV-09: Injection Payloads In CSV Are Treated Safely As Pure Text")
        void testCsvInjectionPayloads() {
            CsvSyncManager sync = new CsvSyncManager();
            TokenDictionary dict = new TokenDictionary();
            String payloadCsv = "\"token_id\",\"en\",\"zh_tw\",\"category\"\r\n" +
                    "\"INJ_1\",\"=cmd|'/C calc'!A0\",\"<script>alert('XSS')</script>\",\"; DROP TABLE tokens; --\"\r\n";

            assertDoesNotThrow(() -> sync.importTokensFromCsvString(dict, payloadCsv));
            TokenRecord rec = dict.get("INJ_1");
            assertNotNull(rec);
            assertEquals("=cmd|'/C calc'!A0", rec.en());
            assertEquals("<script>alert('XSS')</script>", rec.zh_tw());
        }
    }

    // =========================================================================
    // Suite 3: UntranslatedCaptureQueue Concurrency & Capacity Bounds
    // =========================================================================
    @Nested
    @DisplayName("Suite 3: UntranslatedCaptureQueue Concurrency & Capacity Bounds")
    class UntranslatedCaptureQueueAdversarialTests {

        @Test
        @DisplayName("ADV-CAP-01: High-Concurrency Producer Contention (20 Threads, 5,000 Items)")
        void testHighConcurrencyProducers() throws InterruptedException {
            UntranslatedCaptureQueue queue = new UntranslatedCaptureQueue(2000);
            int threads = 20;
            int itemsPerThread = 250;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            List<Throwable> errors = new CopyOnWriteArrayList<>();

            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < itemsPerThread; i++) {
                            queue.capture("Unique text " + threadId + "_" + i, "CONCURRENT");
                        }
                    } catch (Throwable e) {
                        errors.add(e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            executor.shutdownNow();
            assertTrue(errors.isEmpty(), "Errors during concurrent capture: " + errors);
            // Verify size is tightly bounded near 2000
            assertTrue(queue.size() <= 2050, "Queue exceeded capacity drastically: " + queue.size());
        }

        @Test
        @DisplayName("ADV-CAP-02: Concurrent Duplicate Deduplication Increments Seen Count Atomically")
        void testConcurrentDeduplicationAtomicCount() throws InterruptedException {
            UntranslatedCaptureQueue queue = new UntranslatedCaptureQueue(500);
            int threads = 10;
            int repeatsPerThread = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);

            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < repeatsPerThread; i++) {
                            queue.capture("§cWarning: The Dragon has awakened!", "ALERT");
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            executor.shutdownNow();

            assertEquals(1, queue.size(), "Duplicate clean texts must collapse to single record");
            UntranslatedRecord rec = queue.getRecords().values().iterator().next();
            assertEquals(threads * repeatsPerThread, rec.seenCount(), "seenCount must equal total captures");
            assertEquals("Warning: The Dragon has awakened!", rec.cleanText());
        }

        @Test
        @DisplayName("ADV-CAP-03: Pure Color Codes and Whitespace Discarded Without Capture")
        void testPureColorCodeDiscard() {
            UntranslatedCaptureQueue queue = new UntranslatedCaptureQueue(100);
            queue.capture(null, "TEST");
            queue.capture("", "TEST");
            queue.capture("    ", "TEST");
            queue.capture("§a§b§c§r", "TEST");
            queue.capture("§1   §2§3 ", "TEST");

            assertEquals(0, queue.size(), "Pure color codes or blank text must never be captured");
        }

        @Test
        @DisplayName("ADV-CAP-04: Concurrent Flush While Producers Actively Insert")
        void testConcurrentFlushWhileWriting() throws Exception {
            UntranslatedCaptureQueue queue = new UntranslatedCaptureQueue(1000);
            Path flushFile = tempDir.resolve("untranslated_concurrent.json");
            AtomicInteger running = new AtomicInteger(1);
            ExecutorService executor = Executors.newFixedThreadPool(4);

            // 3 Producer threads
            for (int i = 0; i < 3; i++) {
                final int tid = i;
                executor.submit(() -> {
                    int counter = 0;
                    while (running.get() == 1) {
                        queue.capture("Streamed text " + tid + "_" + (counter++), "STREAM");
                    }
                });
            }

            // Flush 5 times while producers write
            for (int f = 0; f < 5; f++) {
                Thread.sleep(20);
                assertDoesNotThrow(() -> queue.flushToJson(flushFile));
                assertTrue(Files.exists(flushFile));
            }

            running.set(0);
            executor.shutdown();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    // =========================================================================
    // Suite 4: RomanNumeralParser Boundary & Extreme Value Tests
    // =========================================================================
    @Nested
    @DisplayName("Suite 4: RomanNumeralParser Boundary & Extreme Value Tests")
    class RomanNumeralParserAdversarialTests {

        @Test
        @DisplayName("ADV-ROM-01: Boundary Values for Arabic and Chinese Conversions")
        void testArabicAndChineseBoundaries() {
            RomanNumeralParser parser = new RomanNumeralParser();

            // Valid range 1-10
            assertEquals("I", parser.toRoman(1));
            assertEquals("X", parser.toRoman(10));
            assertEquals("一", parser.toChinese(1));
            assertEquals("十", parser.toChinese(10));

            // Boundaries: 0, negative, > 10 return integer string fallback
            assertEquals("0", parser.toRoman(0));
            assertEquals("-1", parser.toRoman(-1));
            assertEquals("11", parser.toRoman(11));
            assertEquals("3999", parser.toRoman(3999));

            assertEquals("0", parser.toChinese(0));
            assertEquals("-5", parser.toChinese(-5));
            assertEquals("15", parser.toChinese(15));
        }

        @Test
        @DisplayName("ADV-ROM-02: Extreme and Malformed Roman Input Rejection")
        void testMalformedRomanInputs() {
            RomanNumeralParser parser = new RomanNumeralParser();

            assertEquals(-1, parser.toArabic(null));
            assertEquals(-1, parser.toArabic(""));
            assertEquals(-1, parser.toArabic("0"));
            assertEquals(-1, parser.toArabic("-I"));
            assertEquals(-1, parser.toArabic("XI"));
            assertEquals(-1, parser.toArabic("IIII"));
            assertEquals(-1, parser.toArabic("MMXXVI"));
            assertEquals(-1, parser.toArabic("i")); // Lowercase must be rejected
            assertEquals(-1, parser.toArabic("v"));
            assertEquals(-1, parser.toArabic("x"));
        }

        @Test
        @DisplayName("ADV-ROM-03: Word Boundary Safety In English Sentences")
        void testWordBoundarySafety() {
            RomanNumeralParser parser = new RomanNumeralParser();

            // Embedded 'I', 'V', 'X' in English words must NEVER be replaced
            String text = "Bonsai Tree with Vivid colors and Six branches";
            assertEquals(text, parser.translateRomanLine(text, true));

            // Standalone valid roman numerals must be translated
            String romanLine = "Overload V and Power X in Chapter I";
            String expected = "Overload 五 and Power 十 in Chapter 一";
            assertEquals(expected, parser.translateRomanLine(romanLine, true));
        }

        @Test
        @DisplayName("ADV-ROM-04: Hyphenated Words Avoid Word Boundary False Positives (e.g. X-ray)")
        void testHyphenatedWordFalsePositiveDefect() {
            RomanNumeralParser parser = new RomanNumeralParser();

            // In regex (?<![A-Za-z0-9-])(I|...|X)(?![A-Za-z0-9-]), hyphenated words like "X-ray"
            // do not match Roman numeral "X" and remain uncorrupted.
            String result = parser.translateRomanLine("X-ray vision", true);
            assertEquals("X-ray vision", result, "Hyphenated word X-ray must not match Roman numeral X");
        }
    }

    // =========================================================================
    // Suite 5: LoreParser Adversarial & Format Stress Tests
    // =========================================================================
    @Nested
    @DisplayName("Suite 5: LoreParser Adversarial & Format Stress Tests")
    class LoreParserAdversarialTests {

        @Test
        @DisplayName("ADV-LOR-01: Complex Lore Stat Lines With Brackets Preserved")
        void testComplexLoreStatLineBrackets() {
            String line = "§7Damage: §c+310 §e(+30) §a[+20]";
            String translated = loreParser.translateLoreLine(line);

            assertTrue(translated.contains("傷害："));
            assertTrue(translated.contains("§c+310"));
            assertTrue(translated.contains("§e(+30)"));
            assertTrue(translated.contains("§a[+20]"));
        }

        @Test
        @DisplayName("ADV-LOR-02: Missing Space After Colon In Stat Line")
        void testMissingSpaceAfterColon() {
            String line = "Damage:+100";
            String translated = loreParser.translateLoreLine(line);
            assertEquals("傷害：+100", translated);
        }

        @Test
        @DisplayName("ADV-LOR-03: Multi-Word Stat Labels and Rarity Footers")
        void testMultiWordStatsAndFooters() {
            assertEquals("§7海怪機率：§a+5%", loreParser.translateLoreLine("§7Sea Creature Chance: §a+5%"));
            assertEquals("§7採礦幸運：§a+120", loreParser.translateLoreLine("§7Mining Fortune: §a+120"));

            assertEquals("§6§l傳奇 地下城 劍", loreParser.translateLoreLine("§6§lLEGENDARY DUNGEON SWORD"));
            assertEquals("§d§l神話 弓", loreParser.translateLoreLine("§d§lMYTHIC BOW"));
            assertEquals("§c§l極度特殊 胸甲", loreParser.translateLoreLine("§c§lVERY SPECIAL CHESTPLATE"));
        }

        @Test
        @DisplayName("ADV-LOR-04: Gemstone Slots and Ability Trigger Translation")
        void testGemstonesAndAbilities() {
            assertEquals("§7寶石槽：§8[\ue003] [⚔]", loreParser.translateLoreLine("§7Gemstones: §8[\ue003] [⚔]"));
            assertEquals("§7寶石槽：§8[◆]", loreParser.translateLoreLine("§7Gemstone Slot: §8[◆]"));

            String ability = "§6Ability: Instant Transmission  §e§lRIGHT CLICK";
            String transAbility = loreParser.translateLoreLine(ability);
            assertEquals("§6技能：瞬間移動  §e§l右鍵點擊", transAbility);
        }

        @Test
        @DisplayName("ADV-LOR-05: Cooldown Line Regex Safely Matches Seconds Suffix Without Word Corruption")
        void testCooldownBlanketReplaceDefectProbe() {
            // Line with cooldown containing descriptive words with lowercase 's'
            String cooldownLine = "Cooldown: 10s (scales with Speed)";
            String result = loreParser.translateLoreLine(cooldownLine);

            assertEquals("冷卻時間：10秒 (scales with Speed)", result, "Regex (?<=\\d)s\\b replaces 's' only following digits without corrupting words");
        }

        @Test
        @DisplayName("ADV-LOR-06: Multi-Formatting and Bold Codes Match STAT_LINE_PATTERN")
        void testBoldFormattingFailsLoreParserPatternDefectProbe() {
            // A stat line with bold color formatting: §c§lDamage: +100
            String boldLine = "§c§lDamage: +100";
            String result = loreParser.translateLoreLine(boldLine);

            assertEquals("§c§l傷害：+100", result, "Multi-formatting prefix like §c§l must match STAT_LINE_PATTERN");
        }

        @Test
        @DisplayName("ADV-LOR-07: Stat Line Priority Prevents Breaking Power Hijacking")
        void testBreakingPowerHijackedByEnchantmentDefectProbe() {
            String line = "§7Breaking Power: §a8";
            String result = loreParser.translateLoreLine(line);

            assertEquals("§7破壞力：§a8", result, "Stat line parsing must evaluate before enchantments so Breaking Power is translated cleanly");
        }
    }

    // =========================================================================
    // Suite 6: DynamicItemCombiner Adversarial Collision & Star Stress Tests
    // =========================================================================
    @Nested
    @DisplayName("Suite 6: DynamicItemCombiner Adversarial Collision & Star Stress Tests")
    class DynamicItemCombinerAdversarialTests {

        @Test
        @DisplayName("ADV-DIC-01: All 17 Collision Base Items Without Reforge Retain Identity")
        void testAll17CollisionBaseItemsWithoutReforge() {
            List<String> collisionItems = List.of(
                    "Ancient Claw", "Ancient Cloak", "Warped Stone", "Bulky Stone", "Blessed Fruit",
                    "Calcified Heart", "Beady Eyes", "Light Blue Carpet", "Giant Isopod Shard", "Heavy Helmet",
                    "Wise Dragon Helmet", "Spiritual Stone", "Fabled Stone", "Heroic Stone", "Submerged Stone",
                    "Spicy Squid", "Heavy Pearl"
            );

            for (String item : collisionItems) {
                DynamicItemCombiner.DecomposedName d = dynamicItemCombiner.decomposeName(item, null);
                assertNull(d.reforge(), "Base collision item must NOT have stripped reforge: " + item);
                assertEquals(item, d.baseName(), "Base name must match exactly: " + item);
            }
        }

        @Test
        @DisplayName("ADV-DIC-02: Double Reforge Collisions (Reforge Same Name As Base Item)")
        void testDoubleReforgeCollisions() {
            // "Ancient Ancient Claw" -> reforge=Ancient, base=Ancient Claw
            DynamicItemCombiner.DecomposedName d1 = dynamicItemCombiner.decomposeName("Ancient Ancient Claw", null);
            assertEquals("Ancient", d1.reforge());
            assertEquals("Ancient Claw", d1.baseName());

            // "Warped Warped Stone" -> reforge=Warped, base=Warped Stone
            DynamicItemCombiner.DecomposedName d2 = dynamicItemCombiner.decomposeName("Warped Warped Stone", null);
            assertEquals("Warped", d2.reforge());
            assertEquals("Warped Stone", d2.baseName());

            // "Heavy Heavy Helmet" -> reforge=Heavy, base=Heavy Helmet
            DynamicItemCombiner.DecomposedName d3 = dynamicItemCombiner.decomposeName("Heavy Heavy Helmet", null);
            assertEquals("Heavy", d3.reforge());
            assertEquals("Heavy Helmet", d3.baseName());
        }

        @Test
        @DisplayName("ADV-DIC-03: Cross Reforges On Collision Base Items")
        void testCrossReforgesOnCollisionItems() {
            DynamicItemCombiner.DecomposedName d1 = dynamicItemCombiner.decomposeName("Fabled Ancient Claw", null);
            assertEquals("Fabled", d1.reforge());
            assertEquals("Ancient Claw", d1.baseName());

            DynamicItemCombiner.DecomposedName d2 = dynamicItemCombiner.decomposeName("Heroic Warped Stone", null);
            assertEquals("Heroic", d2.reforge());
            assertEquals("Warped Stone", d2.baseName());
        }

        @Test
        @DisplayName("ADV-DIC-04: Dungeon Stars (✪) and Master Mode Stars (➊-➎) Extraction")
        void testStarsExtractionFidelity() {
            DynamicItemCombiner.DecomposedName d1 = dynamicItemCombiner.decomposeName("Hyperion ✪✪✪✪✪", null);
            assertEquals("✪✪✪✪✪", d1.stars());
            assertEquals("Hyperion", d1.baseName());

            DynamicItemCombiner.DecomposedName d2 = dynamicItemCombiner.decomposeName("Hyperion ✪✪✪✪✪➊", null);
            assertEquals("✪✪✪✪✪➊", d2.stars());
            assertEquals("Hyperion", d2.baseName());

            DynamicItemCombiner.DecomposedName d3 = dynamicItemCombiner.decomposeName("§d§lHyperion §6✪✪✪✪✪§c➎", null);
            assertEquals("§d§l", d3.colorPrefix());
            assertEquals("§6✪✪✪✪✪§c➎", d3.stars());
            assertEquals("Hyperion", d3.baseName());
        }

        @Test
        @DisplayName("ADV-DIC-05: Pet Level Boundaries and Format Edge Cases")
        void testPetLevelBoundaries() {
            DynamicItemCombiner.DecomposedName p1 = dynamicItemCombiner.decomposeName("[Lvl 1] Baby Yeti", null);
            assertEquals("1", p1.petLevel());
            assertEquals("Baby Yeti", p1.baseName());

            DynamicItemCombiner.DecomposedName p2 = dynamicItemCombiner.decomposeName("[Lvl 200] Golden Dragon", null);
            assertEquals("200", p2.petLevel());
            assertEquals("Golden Dragon", p2.baseName());

            DynamicItemCombiner.DecomposedName p3 = dynamicItemCombiner.decomposeName("[Lvl 0] Bat", null);
            assertEquals("0", p3.petLevel());
            assertEquals("Bat", p3.baseName());

            // Unclosed bracket does not crash
            DynamicItemCombiner.DecomposedName p4 = dynamicItemCombiner.decomposeName("[Lvl 100 Baby Yeti", null);
            assertNull(p4.petLevel());
            assertEquals("[Lvl 100 Baby Yeti", p4.baseName());
        }

        @Test
        @DisplayName("ADV-DIC-06: Null and Empty Inputs Safe Handling")
        void testNullAndEmptyInputsSafety() {
            DynamicItemCombiner.DecomposedName dNull = dynamicItemCombiner.decomposeName(null, null);
            assertNotNull(dNull);
            assertEquals("", dNull.baseName());

            DynamicItemCombiner.DecomposedName dEmpty = dynamicItemCombiner.decomposeName("", null);
            assertNotNull(dEmpty);
            assertEquals("", dEmpty.baseName());

            MutableComponent comp = assertDoesNotThrow(() -> dynamicItemCombiner.translateItemName(null, null));
            assertNotNull(comp);

            assertEquals("", dynamicItemCombiner.getCleanEnglishName(null));
            assertEquals("", dynamicItemCombiner.getCleanEnglishName(null, null));
            assertEquals("", dynamicItemCombiner.fallbackTranslate(null));
        }

        @Test
        @DisplayName("ADV-DIC-07: Alt/Shift Tooltip Preview Reverts 100% to English")
        void testTooltipPreviewAltShiftReversion() {
            List<Component> original = List.of(
                    Component.literal("Hyperion"),
                    Component.literal("Damage: +260")
            );

            // When isAltOrShiftDown is true, returns verbatim English tooltip copy
            List<Component> preview = dynamicItemCombiner.translateTooltip(null, original, true);
            assertEquals(2, preview.size());
            assertEquals("Hyperion", preview.get(0).getString());
            assertEquals("Damage: +260", preview.get(1).getString());
        }
    }

    // =========================================================================
    // Suite 7: TextTraverser Adversarial Stress & Event Preservation
    // =========================================================================
    @Nested
    @DisplayName("Suite 7: TextTraverser Adversarial Stress & Event Preservation")
    class TextTraverserAdversarialTests {

        @Test
        @DisplayName("ADV-TXT-01: Ultra-Deep Sibling Tree Recursion (100 Nested Siblings)")
        void testUltraDeepSiblingTreeRecursion() {
            MutableComponent root = Component.literal("Root");
            MutableComponent current = root;
            for (int i = 0; i < 100; i++) {
                MutableComponent child = Component.literal("Node_" + i);
                current.append(child);
                current = child;
            }

            MutableComponent translated = assertDoesNotThrow(
                    () -> textTraverser.translateRecursive(root, s -> s.replace("Node_", "節點_")));
            assertNotNull(translated);
            assertTrue(translated.getString().contains("節點_99"));
        }

        @Test
        @DisplayName("ADV-TXT-02: Complete Style Formatting Attributes Preserved")
        void testStyleFormattingPreservation() {
            Style complexStyle = Style.EMPTY
                    .withColor(TextColor.fromLegacyFormat(ChatFormatting.RED))
                    .withBold(true)
                    .withItalic(false)
                    .withUnderlined(true)
                    .withStrikethrough(false)
                    .withObfuscated(false);

            MutableComponent comp = Component.literal("Styled Text").setStyle(complexStyle);
            MutableComponent result = textTraverser.translateRecursive(comp, s -> "已樣式文字");

            assertEquals("已樣式文字", result.getString());
            assertEquals(complexStyle.getColor(), result.getStyle().getColor());
            assertEquals(complexStyle.isBold(), result.getStyle().isBold());
            assertEquals(complexStyle.isItalic(), result.getStyle().isItalic());
            assertEquals(complexStyle.isUnderlined(), result.getStyle().isUnderlined());
        }

        @Test
        @DisplayName("ADV-TXT-03: ClickEvent OpenUrl and RunCommand Preserved Verbatim")
        void testClickEventPreservationVerbatim() {
            ClickEvent urlEvent = new ClickEvent.OpenUrl(URI.create("https://hypixel.net"));
            ClickEvent cmdEvent = new ClickEvent.RunCommand("/warp hub");

            MutableComponent urlComp = Component.literal("Click URL").setStyle(Style.EMPTY.withClickEvent(urlEvent));
            MutableComponent cmdComp = Component.literal("Click Command").setStyle(Style.EMPTY.withClickEvent(cmdEvent));

            MutableComponent transUrl = textTraverser.translateRecursive(urlComp, s -> "點擊連結");
            MutableComponent transCmd = textTraverser.translateRecursive(cmdComp, s -> "執行指令");

            assertEquals("點擊連結", transUrl.getString());
            assertEquals(urlEvent, transUrl.getStyle().getClickEvent());

            assertEquals("執行指令", transCmd.getString());
            assertEquals(cmdEvent, transCmd.getStyle().getClickEvent());
        }

        @Test
        @DisplayName("ADV-TXT-04: HoverEvent ShowText Inner Tooltip Recursively Translated")
        void testHoverEventShowTextRecursion() {
            Component innerTooltip = Component.literal("Damage: +100");
            HoverEvent hover = new HoverEvent.ShowText(innerTooltip);
            MutableComponent comp = Component.literal("Item").setStyle(Style.EMPTY.withHoverEvent(hover));

            MutableComponent translated = textTraverser.translateRecursive(comp, s -> s.replace("Damage", "傷害"));

            assertNotNull(translated.getStyle().getHoverEvent());
            assertInstanceOf(HoverEvent.ShowText.class, translated.getStyle().getHoverEvent());
            HoverEvent.ShowText transHover = (HoverEvent.ShowText) translated.getStyle().getHoverEvent();
            assertEquals("傷害: +100", transHover.value().getString());
        }

        @Test
        @DisplayName("ADV-TXT-05: Null Component and Null Translator Safety")
        void testNullComponentAndTranslatorSafety() {
            MutableComponent nullComp = textTraverser.translateRecursive(null, s -> "Translated");
            assertNotNull(nullComp);
            assertEquals("", nullComp.getString());

            MutableComponent comp = Component.literal("Hello");
            MutableComponent nullTrans = textTraverser.translateRecursive(comp, null);
            assertEquals("Hello", nullTrans.getString());

            MutableComponent returnsNull = textTraverser.translateRecursive(comp, s -> null);
            assertEquals("Hello", returnsNull.getString(), "Null translator result must fallback to original text");
        }
    }

    // =========================================================================
    // Suite 8: SchemaNormalizer & Pipeline Robustness Tests
    // =========================================================================
    @Nested
    @DisplayName("Suite 8: SchemaNormalizer & Pipeline Robustness Tests")
    class SchemaNormalizerAdversarialTests {

        @Test
        @DisplayName("ADV-SCH-01: Stat Normalization Exhaustive Variations")
        void testStatNormalizationExhaustive() {
            assertEquals("UNKNOWN", SchemaNormalizer.normalizeStat(null));
            assertEquals("UNKNOWN", SchemaNormalizer.normalizeStat(""));
            assertEquals("UNKNOWN", SchemaNormalizer.normalizeStat("   "));

            assertEquals("DEFENSE", SchemaNormalizer.normalizeStat("defence"));
            assertEquals("DEFENSE", SchemaNormalizer.normalizeStat("DEFENSE"));
            assertEquals("DAMAGE", SchemaNormalizer.normalizeStat("dmg"));
            assertEquals("STRENGTH", SchemaNormalizer.normalizeStat("str"));
            assertEquals("CRIT_CHANCE", SchemaNormalizer.normalizeStat("cc"));
            assertEquals("CRIT_DAMAGE", SchemaNormalizer.normalizeStat("cd"));
            assertEquals("SPEED", SchemaNormalizer.normalizeStat("walk_speed"));
            assertEquals("TRUE_DEFENSE", SchemaNormalizer.normalizeStat("td"));
            assertEquals("SEA_CREATURE_CHANCE", SchemaNormalizer.normalizeStat("scc"));
        }

        @Test
        @DisplayName("ADV-SCH-02: Rarity Normalization Exhaustive Variations")
        void testRarityNormalizationExhaustive() {
            assertEquals("COMMON", SchemaNormalizer.normalizeRarity(null));
            assertEquals("COMMON", SchemaNormalizer.normalizeRarity(""));
            assertEquals("COMMON", SchemaNormalizer.normalizeRarity("   "));

            assertEquals("VERY_SPECIAL", SchemaNormalizer.normalizeRarity("very special"));
            assertEquals("SPECIAL", SchemaNormalizer.normalizeRarity("SPECIAL"));
            assertEquals("DIVINE", SchemaNormalizer.normalizeRarity("divine"));
            assertEquals("MYTHIC", SchemaNormalizer.normalizeRarity("mythic"));
            assertEquals("LEGENDARY", SchemaNormalizer.normalizeRarity("legendary"));
            assertEquals("EPIC", SchemaNormalizer.normalizeRarity("epic"));
            assertEquals("RARE", SchemaNormalizer.normalizeRarity("rare"));
            assertEquals("UNCOMMON", SchemaNormalizer.normalizeRarity("uncommon"));
            assertEquals("COMMON", SchemaNormalizer.normalizeRarity("common"));
            assertEquals("ULTIMATE", SchemaNormalizer.normalizeRarity("ultimate"));
            assertEquals("ADMIN", SchemaNormalizer.normalizeRarity("admin"));
        }

        @Test
        @DisplayName("ADV-SCH-03: Category Normalization Exhaustive Variations")
        void testCategoryNormalizationExhaustive() {
            assertEquals("MISC", SchemaNormalizer.normalizeCategory(null));
            assertEquals("MISC", SchemaNormalizer.normalizeCategory(""));
            assertEquals("MISC", SchemaNormalizer.normalizeCategory("   "));

            assertEquals("SWORD", SchemaNormalizer.normalizeCategory("sword"));
            assertEquals("BOW", SchemaNormalizer.normalizeCategory("bow"));
            assertEquals("CHESTPLATE", SchemaNormalizer.normalizeCategory("chestplate"));
            assertEquals("FISHING_ROD", SchemaNormalizer.normalizeCategory("fishing_weapon"));
            assertEquals("REFORGE_STONE", SchemaNormalizer.normalizeCategory("reforge-stone"));
        }

        @Test
        @DisplayName("ADV-SCH-04: Lore Template Extraction Edge Cases")
        void testLoreTemplateExtractionEdgeCases() {
            SchemaNormalizer normalizer = new SchemaNormalizer();

            assertTrue(normalizer.extractLoreTemplate(null).isEmpty());
            assertTrue(normalizer.extractLoreTemplate(List.of()).isEmpty());

            List<String> rawLore = List.of(
                    "§cMana Cost: §a50",
                    "§cCooldown: §a1.5s",
                    "§7Damage: §c+310",
                    "§8A powerful blade."
            );

            List<String> template = normalizer.extractLoreTemplate(rawLore);
            assertEquals(4, template.size());
            assertTrue(template.get(0).contains("{0}"));
            assertTrue(template.get(1).contains("{0}s"));
            assertTrue(template.get(2).contains("{STAT_DAMAGE}"));
            assertEquals("§8A powerful blade.", template.get(3));
        }
    }
}
