package com.hypixel.translation.e2e;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.config.ModConfig;
import com.hypixel.translation.core.DialogueDictionary;
import com.hypixel.translation.core.ItemDictionary;
import com.hypixel.translation.core.ItemTranslationRecord;
import com.hypixel.translation.core.LoreLineDictionary;
import com.hypixel.translation.core.TokenDictionary;
import com.hypixel.translation.core.TokenRecord;
import com.hypixel.translation.core.UntranslatedCaptureQueue;
import com.hypixel.translation.engine.DynamicItemCombiner;
import com.hypixel.translation.engine.LoreParser;
import com.hypixel.translation.engine.RomanNumeralParser;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R2 E2E Quality & Coverage Gate Suite (12 tests: 4 T1, 4 T2, 2 T3, 2 T4).
 * Verifies production dictionary coverage, Traditional Chinese purity (0 SC regressions),
 * placeholder integrity, and runtime resolution across tokens.json, items.json,
 * dialogues.json, raw_wiki_dialogues.json, and live capture samples.
 */
@Tag("e2e")
@Tag("r2")
@DisplayName("R2: Dictionary Quality, Coverage & TC Purity E2E Suite")
public class R2DictionaryQualityE2ETest extends BaseE2ETest {

    private static final String DICT_REL_DIR = "src/main/resources/assets/hsbtr/dictionaries";
    private static final String WIKI_REL_PATH = "cache/fandom/raw_wiki_dialogues.json";
    private static final String ACTIVE_PRISM_CAPTURE_PATH =
            "D:\\PrismLauncher\\instances\\26.2\\minecraft\\config\\hypixel_skyblock_translation\\untranslated_capture.json";
    private static final String PRISM_CAPTURE_PATH =
            "C:\\Users\\User\\AppData\\Roaming\\PrismLauncher\\instances\\26.2\\minecraft\\config\\hypixel_skyblock_translation\\untranslated_capture.json";

    private static final Pattern CJK_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern COLOR_STRIP_PATTERN = Pattern.compile("§[0-9a-fklmnorA-FKLMNOR]");
    private static final Pattern VALID_TOKEN_ID_PATTERN = Pattern.compile("^[A-Z0-9_]+$");
    private static final Pattern BRACE_BLOCK_PATTERN = Pattern.compile("\\{([^}]*)\\}");
    private static final Pattern UNRESOLVED_STAT_PATTERN = Pattern.compile("\\{STAT_[A-Za-z0-9_]+\\}");
    private static final Pattern WIKI_PLUS_MINUS_NOISE = Pattern.compile("^[+-]\\d+[^a-zA-Z0-9_\\s]*\\s+.*");
    private static final Pattern WIKI_NPC_PATTERN = Pattern.compile("^\\[NPC\\]\\s*([^:]+):\\s*(.*)$");
    private static final Pattern ASCII_LETTER_PATTERN = Pattern.compile("[A-Za-z]");

    private static final Set<String> VALID_TOKEN_CATEGORIES = Set.of(
            "stat", "reforge", "item", "category", "ability", "enchant", "location", "ui",
            "collection", "mayor", "rarity", "skill", "npc", "boss", "general",
            "dialogue", "mob", "potion", "feature", "event", "attribute", "faction", "mechanic"
    );

    // 317 verified strictly-Simplified-Chinese-only codepoints (excludes TC homographs and non-Big5 TC like \u87CE, \u9C5D).
    // Includes \u4F53 (ti) and \u561E (le) observed in baseline regressions.
    private static final String STRICT_SC_CHARS =
            "\u4E13\u4E1A\u4E1B\u4E1C\u4E1D\u4E24\u4E25\u4E27\u4E2A\u4E34\u4E3A\u4E3E\u4E48\u4E49\u4E4C\u4E50" +
            "\u4E54\u4E60\u4E61\u4E66\u4E70\u4E71\u4E89\u4E8F\u4E9A\u4EA7\u4EB2\u4EBF\u4EC5\u4ED1\u4ED3\u4EEA" +
            "\u4EEC\u4EF7\u4F17\u4F18\u4F1A\u4F1E\u4F1F\u4F20\u4F24\u4F26\u4F2A\u4F53\u4FA6\u4FA7\u4FA8\u4FE9" +
            "\u4FED\u503A\u503E\u50A8\u513F\u5151\u515A\u5170\u5173\u5174\u517B\u517D\u5199\u519B\u519C\u51E4" +
            "\u51FB\u5218\u5219\u521B\u5220\u5242\u5251\u5265\u5267\u529D\u529E\u52A1\u52A8\u52B3\u52BF" +
            "\u533A\u533B\u534E\u534F\u5355\u5356\u5362\u536B\u5385\u538B\u53BF\u53C2\u53CC\u53D1\u53D8\u53F7" +
            "\u53F9\u542C\u542F\u5458\u54CD\u54D1\u5524\u561E\u56FD\u56FE\u5706\u5723\u573A\u574F\u5757\u575B" +
            "\u5760\u5792\u57A6\u57AB\u58F0\u5904\u5907\u5934\u5B9E\u5BA0\u5BF9\u5BFC\u5C06\u5C81\u5E01\u5E08" +
            "\u5E2E\u5E86\u5E94\u5F00\u5F02\u5F20\u5F52\u5F53\u5F55\u6001\u603B\u6076\u60CA\u6218\u62A4\u62A5" +
            "\u6362\u73B0\u7535\u7EBF\u7EC3\u7ECF\u7ED3\u7ED9\u7EDF\u7EE7\u7EF4\u7EFF\u7F16\u7F51\u7F57\u7F5A" +
            "\u7F62\u804C\u8054\u80A0\u80A4\u80BE\u80C6\u80DC\u810F\u8111\u817E\u827A\u8282\u82CF\u830E\u8327" +
            "\u836F\u83B7\u8425\u8427\u8428\u84DD\u867D\u867E\u8681\u8682\u8747\u8865\u889C\u88C5\u89C2\u89C6" +
            "\u89C8\u89C9\u8BA1\u8BA2\u8BA4\u8BA8\u8BA9\u8BAD\u8BAE\u8BB0\u8BB2\u8BB8\u8BBA\u8BBE\u8BBF\u8BC1" +
            "\u8BC4\u8BC6\u8BC9\u8BCD\u8BD1\u8BD5\u8BD7\u8BDD\u8BE5\u8BE6\u8BED\u8BEF\u8BF4\u8BF7\u8BF8\u8BFB" +
            "\u8BFE\u8C01\u8C03\u8C08\u8C0B\u8C22\u8C28\u8D35\u8D37\u8D38\u8D39\u8D44\u8D4F\u8D50\u8D54\u8D56" +
            "\u8D5A\u8D5B\u8D5E\u8D60\u8D62\u8D8B\u8DC3\u8DF5\u8E2A\u8F66\u8FB9\u8FBE\u8FC7\u8FD0\u8FD8\u8FD9" +
            "\u8FDB\u8FDC\u8FDE\u9009\u9057\u94BB\u94C1\u94DC\u94DD\u94F6\u9521\u953B\u9547\u955C\u957F\u95E8" +
            "\u95EA\u95ED\u95EE\u95F2\u95F4\u95F9\u95FB\u9601\u9605\u9633\u9634\u9635\u9636\u9645\u9646\u968F" +
            "\u9690\u96BE\u96FE\u9875\u9876\u9879\u987A\u987B\u9886\u9898\u989C\u98CE\u98D8\u98DE\u996D\u996E" +
            "\u9970\u997C\u9986\u9A6C\u9A71\u9A8C\u9C7C\u9E1F\u9EA6\u9F50\u9F7F\u9F84\u9F99\u9F9F";

    private static final Set<Character> STRICT_SC_SET = buildScSet();

    private static Set<Character> buildScSet() {
        Set<Character> set = new HashSet<>(STRICT_SC_CHARS.length() * 2);
        for (int i = 0; i < STRICT_SC_CHARS.length(); i++) {
            set.add(STRICT_SC_CHARS.charAt(i));
        }
        return Collections.unmodifiableSet(set);
    }

    private static Path resolveProjectPath(String relativePath) {
        Path p = Path.of(relativePath);
        if (Files.exists(p)) return p;
        Path fallback = Path.of("C:\\Users\\User\\teamwork_projects\\hypixel_skyblock_translation", relativePath);
        if (Files.exists(fallback)) return fallback;
        return p;
    }

    private static JsonObject unwrapTokensRoot(JsonObject root) {
        return (root.has("tokens") && root.get("tokens").isJsonObject())
                ? root.getAsJsonObject("tokens")
                : root;
    }

    private static JsonObject unwrapItemsRoot(JsonObject root) {
        return (root.has("items") && root.get("items").isJsonObject())
                ? root.getAsJsonObject("items")
                : root;
    }

    private static JsonObject unwrapDialoguesRoot(JsonObject root) {
        return (root.has("dialogues") && root.get("dialogues").isJsonObject())
                ? root.getAsJsonObject("dialogues")
                : root;
    }

    private static JsonObject unwrapLoreLinesRoot(JsonObject root) {
        return (root.has("lines") && root.get("lines").isJsonObject())
                ? root.getAsJsonObject("lines")
                : root;
    }

    private static String extractLoreLineZhTw(JsonElement val) {
        if (val == null || val.isJsonNull()) return null;
        if (val.isJsonPrimitive()) {
            return val.getAsString();
        }
        if (val.isJsonObject()) {
            JsonObject obj = val.getAsJsonObject();
            if (obj.has("zh_tw") && !obj.get("zh_tw").isJsonNull()) {
                return obj.get("zh_tw").getAsString();
            }
        }
        return null;
    }

    private static boolean containsCjk(String text) {
        return text != null && CJK_PATTERN.matcher(text).find();
    }

    private static String stripFormatting(String text) {
        return text == null ? "" : COLOR_STRIP_PATTERN.matcher(text).replaceAll("");
    }

    private static List<String> findScViolations(String context, String value) {
        if (value == null || value.isEmpty()) return List.of();
        List<String> hits = new ArrayList<>();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (STRICT_SC_SET.contains(ch)) {
                hits.add(context + " char='" + ch + "' (U+" + String.format("%04X", (int) ch) + ") in \"" + value + "\"");
            }
        }
        return hits;
    }

    private static boolean hasBalancedBraces(String text) {
        if (text == null || text.isEmpty()) return true;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
                if (depth > 1) return false;
            } else if (c == '}') {
                depth--;
                if (depth < 0) return false;
            }
        }
        return depth == 0;
    }

    private static void checkPlaceholderAndEncoding(String context, String text, List<String> errors) {
        if (text == null) return;
        if (text.contains("\uFFFD")) {
            errors.add(context + " contains U+FFFD replacement character");
        }
        if (!hasBalancedBraces(text)) {
            errors.add(context + " has unbalanced braces: \"" + text + "\"");
        }
        Matcher m = BRACE_BLOCK_PATTERN.matcher(text);
        while (m.find()) {
            String full = m.group(0);
            boolean isCaptureTemplateVar = context.contains("patterns")
                    && m.start() > 0
                    && text.charAt(m.start() - 1) == '$'
                    && full.matches("^\\{[A-Za-z][A-Za-z0-9_]*\\}$");
            if (!full.matches("^\\{[A-Z0-9_]+\\}$") && !isCaptureTemplateVar) {
                errors.add(context + " has invalid placeholder " + full + " in \"" + text + "\"");
            }
        }
    }

    private static TokenDictionary loadProdTokens() throws IOException {
        TokenDictionary dict = new TokenDictionary();
        dict.loadFromFile(resolveProjectPath(DICT_REL_DIR + "/tokens.json"));
        return dict;
    }

    private static ItemDictionary loadProdItems() throws IOException {
        ItemDictionary dict = new ItemDictionary();
        dict.loadFromFile(resolveProjectPath(DICT_REL_DIR + "/items.json"));
        return dict;
    }

    private static DialogueDictionary loadProdDialogues(TokenDictionary tok, ItemDictionary item) throws IOException {
        DialogueDictionary dict = new DialogueDictionary(tok, item);
        dict.loadFromFile(resolveProjectPath(DICT_REL_DIR + "/dialogues.json"));
        return dict;
    }

    private record WikiCleanSubline(String cleanLine, String lookupKey) {}

    private static List<WikiCleanSubline> extractCleanWikiSublines(String rawBlock) {
        if (rawBlock == null || rawBlock.isEmpty()) return List.of();
        List<WikiCleanSubline> result = new ArrayList<>();
        String[] lines = rawBlock.split("\n", -1);
        for (String rawLine : lines) {
            String l = rawLine.replaceAll("'{2,3}", "").trim();
            if (l.isEmpty() || l.length() < 2 || l.equals("!") || l.equals("?") || l.equals("...")
                    || l.equals("-") || l.contains("$[c]") || l.contains("SkyBlock Wiki")) {
                continue;
            }
            if (WIKI_PLUS_MINUS_NOISE.matcher(l).matches()) {
                continue;
            }
            Matcher npc = WIKI_NPC_PATTERN.matcher(l);
            String key = npc.matches() ? npc.group(2).trim() : l;
            if (key.isEmpty() || key.equals("...")) {
                continue;
            }
            result.add(new WikiCleanSubline(l, key));
        }
        return result;
    }

    private static boolean isWikiSublineCovered(DialogueDictionary dict, WikiCleanSubline subline) {
        String trFull = dict.translate(subline.cleanLine());
        if (!trFull.equals(subline.cleanLine()) && containsCjk(trFull)) {
            return true;
        }
        String trKey = dict.translate(subline.lookupKey());
        return !trKey.equals(subline.lookupKey()) && containsCjk(trKey);
    }

    private record LoreTelemetry(
            int totalItems,
            int loreItems,
            int nameWithCjk,
            int totalLoreLines,
            int blankOrFmtLines,
            int numericSymbolLines,
            int placeholderOnlyLines,
            int rawCjkLines,
            int englishOnlyLines,
            int resolvedCjkLoreLines,
            int translatableLines
    ) {}

    private static LoreTelemetry computeLoreTelemetry(ItemDictionary items, TokenDictionary tokens) {
        int totalItems = items.size();
        int loreItems = 0;
        int nameWithCjk = 0;
        int totalLoreLines = 0;
        int blankOrFmt = 0;
        int numericSymbol = 0;
        int placeholderOnly = 0;
        int rawCjk = 0;
        int englishOnly = 0;
        int resolvedCjk = 0;

        for (ItemTranslationRecord rec : items.getItems().values()) {
            if (containsCjk(rec.name_zh_tw())) {
                nameWithCjk++;
            }
            List<String> lore = rec.lore_template();
            if (lore != null && !lore.isEmpty()) {
                loreItems++;
                for (String rawLine : lore) {
                    totalLoreLines++;
                    String noFmt = stripFormatting(rawLine).trim();
                    if (noFmt.isEmpty()) {
                        blankOrFmt++;
                        continue;
                    }
                    boolean hasPlaceholder = BRACE_BLOCK_PATTERN.matcher(noFmt).find();
                    String noPlaceholders = BRACE_BLOCK_PATTERN.matcher(noFmt).replaceAll("").trim();
                    boolean hasAlphaOutsidePlaceholders = ASCII_LETTER_PATTERN.matcher(noPlaceholders).find();
                    boolean hasRawCjk = containsCjk(noFmt);

                    if (hasRawCjk) {
                        rawCjk++;
                    } else if (!hasAlphaOutsidePlaceholders && !hasPlaceholder) {
                        numericSymbol++;
                    } else if (!hasAlphaOutsidePlaceholders) {
                        placeholderOnly++;
                    } else {
                        englishOnly++;
                    }

                    String resolved = tokens.resolveString(rawLine);
                    if (containsCjk(stripFormatting(resolved))) {
                        resolvedCjk++;
                    }
                }
            }
        }

        int translatableLines = totalLoreLines - blankOrFmt - numericSymbol;
        return new LoreTelemetry(
                totalItems, loreItems, nameWithCjk, totalLoreLines,
                blankOrFmt, numericSymbol, placeholderOnly, rawCjk, englishOnly,
                resolvedCjk, translatableLines
        );
    }

    // =========================================================================
    // Tier 1: Dictionary Coverage & Schema Partition Tests (4 tests)
    // =========================================================================
    @Nested
    @Tag("tier1")
    @DisplayName("Tier 1: Production Dictionary Coverage & Schema Gates")
    class Tier1DictionaryCoverage {

        @Test
        @DisplayName("R2-T1-01: tokens.json Expanded Size, Schema, Bare Stat Keys & UI/HUD Resolution")
        void testTokensCoverageAndBareStatResolution() throws IOException {
            Path tokensPath = resolveProjectPath(DICT_REL_DIR + "/tokens.json");
            assertTrue(Files.exists(tokensPath), "tokens.json must exist");

            JsonObject root = JsonParser.parseString(Files.readString(tokensPath, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject tokRoot = unwrapTokensRoot(root);
            assertTrue(tokRoot.size() >= 1050,
                    "Expected expanded tokens.json size >= 1050, got " + tokRoot.size());

            for (Map.Entry<String, JsonElement> entry : tokRoot.entrySet()) {
                String key = entry.getKey();
                assertTrue(VALID_TOKEN_ID_PATTERN.matcher(key).matches(),
                        "Invalid tokenId format: " + key);
                JsonObject obj = entry.getValue().getAsJsonObject();
                assertTrue(obj.has("en") && !obj.get("en").getAsString().isBlank(),
                        "Token " + key + " must have non-blank en");
                assertTrue(obj.has("zh_tw") && !obj.get("zh_tw").getAsString().isBlank(),
                        "Token " + key + " must have non-blank zh_tw");
                String cat = obj.has("category") ? obj.get("category").getAsString() : "general";
                assertTrue(VALID_TOKEN_CATEGORIES.contains(cat),
                        "Token " + key + " has invalid category: " + cat);
            }

            TokenDictionary dict = loadProdTokens();
            assertTrue(dict.size() >= 1050, "TokenDictionary loaded size >= 1050");

            List<String> bareStatKeys = List.of(
                    "INTELLIGENCE", "STRENGTH", "DEFENSE", "HEALTH", "DAMAGE", "CRIT_DAMAGE", "CRIT_CHANCE"
            );
            for (String statKey : bareStatKeys) {
                String resolved = dict.resolveString("{" + statKey + "}");
                assertFalse(resolved.contains("{"),
                        "Bare stat key {" + statKey + "} must resolve in tokens.json, got: " + resolved);
                assertTrue(containsCjk(resolved),
                        "Bare stat key {" + statKey + "} must resolve to CJK, got: " + resolved);
            }

            assertTrue(containsCjk(dict.translateText("Objective")),
                    "UI/HUD term 'Objective' must translate to CJK via tokens.json");
            assertTrue(containsCjk(dict.translateText("Purse")),
                    "UI/HUD term 'Purse' must translate to CJK via tokens.json");
            assertTrue(containsCjk(dict.translateText("Talk to")),
                    "UI/HUD term 'Talk to' must translate to CJK via tokens.json");
        }

        @Test
        @DisplayName("R2-T1-02: items.json Size, Lore Count & Traditional Chinese Name Completeness")
        void testItemsCoverageAndNameCompleteness() throws IOException {
            ItemDictionary items = loadProdItems();
            assertTrue(items.size() >= 9500, "Expected items.json size >= 9500, got " + items.size());

            int loreItems = 0;
            int cjkNames = 0;
            for (ItemTranslationRecord rec : items.getItems().values()) {
                assertNotNull(rec.name_zh_tw(), "name_zh_tw must not be null for " + rec.id());
                assertFalse(rec.name_zh_tw().isBlank(), "name_zh_tw must not be blank for " + rec.id());
                if (containsCjk(rec.name_zh_tw())) {
                    cjkNames++;
                }
                if (rec.lore_template() != null && !rec.lore_template().isEmpty()) {
                    loreItems++;
                }
            }

            assertTrue(loreItems >= 8900, "Expected lore_items >= 8900, got " + loreItems);
            double nameCjkRatio = cjkNames / (double) items.size();
            assertTrue(nameCjkRatio >= 0.995,
                    String.format("Expected name_zh_tw CJK ratio >= 99.5%%, got %.3f%% (%d/%d)",
                            nameCjkRatio * 100.0, cjkNames, items.size()));
        }

        @Test
        @DisplayName("R2-T1-03: dialogues.json Expanded Size & Wiki Subline/Block Coverage >= 85%")
        void testDialoguesCoverageAgainstRawWiki() throws IOException {
            TokenDictionary tokens = loadProdTokens();
            ItemDictionary items = loadProdItems();
            DialogueDictionary dialogues = loadProdDialogues(tokens, items);

            assertTrue(dialogues.size() >= 4500,
                    "Expected dialogues.size() >= 4500, got " + dialogues.size());
            assertTrue(dialogues.patternCount() >= 31,
                    "Expected dialogues patterns >= 31, got " + dialogues.patternCount());

            Path wikiPath = resolveProjectPath(WIKI_REL_PATH);
            assertTrue(Files.exists(wikiPath), "cache/fandom/raw_wiki_dialogues.json must exist");
            JsonArray blocks = JsonParser.parseString(Files.readString(wikiPath, StandardCharsets.UTF_8)).getAsJsonArray();

            int totalBlocks = 0;
            int coveredBlocks = 0;
            int totalCleanSublines = 0;
            int coveredCleanSublines = 0;

            for (JsonElement el : blocks) {
                JsonObject blockObj = el.getAsJsonObject();
                String raw = blockObj.has("raw") ? blockObj.get("raw").getAsString() : "";
                List<WikiCleanSubline> sublines = extractCleanWikiSublines(raw);
                if (sublines.isEmpty()) continue;

                totalBlocks++;
                boolean blockCovered = false;
                for (WikiCleanSubline subline : sublines) {
                    totalCleanSublines++;
                    if (isWikiSublineCovered(dialogues, subline)) {
                        coveredCleanSublines++;
                        blockCovered = true;
                    }
                }
                if (blockCovered) {
                    coveredBlocks++;
                }
            }

            double sublineCov = coveredCleanSublines / (double) totalCleanSublines;
            double blockCov = coveredBlocks / (double) totalBlocks;
            System.out.printf("[R2-WikiTelemetry] blocks=%d/%d (%.2f%%), cleanSublines=%d/%d (%.2f%%)%n",
                    coveredBlocks, totalBlocks, blockCov * 100.0,
                    coveredCleanSublines, totalCleanSublines, sublineCov * 100.0);

            assertTrue(sublineCov >= 0.85,
                    String.format("Expected clean Wiki subline coverage >= 85%%, got %.2f%% (%d/%d)",
                            sublineCov * 100.0, coveredCleanSublines, totalCleanSublines));
            assertTrue(blockCov >= 0.85,
                    String.format("Expected Wiki block coverage >= 85%%, got %.2f%% (%d/%d)",
                            blockCov * 100.0, coveredBlocks, totalBlocks));
        }

        @Test
        @DisplayName("R2-T1-04: Live Capture & Runtime Log Strings Resolve to Traditional Chinese")
        void testLiveCaptureStringsResolveToCjk() throws IOException {
            TokenDictionary tokens = loadProdTokens();
            ItemDictionary items = loadProdItems();
            DialogueDictionary dialogues = loadProdDialogues(tokens, items);

            List<String> requiredLiveStrings = List.of(
                    "My storage is full! :(",
                    "Hypixel Store",
                    "You have just received 1,222 coins as interest in your personal bank account!",
                    "CLICK HERE to claim your rewards!",
                    "Pinging..."
            );

            for (String sample : requiredLiveStrings) {
                String viaDialogue = dialogues.translate(sample);
                String finalTranslated = viaDialogue.equals(sample) ? tokens.translateText(sample) : viaDialogue;
                assertNotEquals(sample, finalTranslated,
                        "Live capture string must translate: \"" + sample + "\"");
                assertTrue(containsCjk(finalTranslated),
                        "Live capture string must translate to CJK: \"" + sample + "\" -> \"" + finalTranslated + "\"");
            }

            Path prismCapture = Path.of(ACTIVE_PRISM_CAPTURE_PATH);
            if (!Files.exists(prismCapture)) prismCapture = Path.of(PRISM_CAPTURE_PATH);
            if (Files.exists(prismCapture)) {
                JsonArray arr = JsonParser.parseString(Files.readString(prismCapture, StandardCharsets.UTF_8)).getAsJsonArray();
                assertTrue(arr.size() > 0, "Existing untranslated_capture.json must be non-empty");
            }
        }
    }

    // =========================================================================
    // Tier 2: Purity, Placeholders & Non-CJK Classification (4 tests)
    // =========================================================================
    @Nested
    @Tag("tier2")
    @DisplayName("Tier 2: TC Purity, Placeholder Integrity & Lore Classification")
    class Tier2PurityAndPlaceholders {

        @Test
        @DisplayName("R2-T2-01: Zero Corrupted Placeholders, Unbalanced Braces, or U+FFFD Across Dictionaries")
        void testZeroCorruptedPlaceholdersAndBalancedBraces() throws IOException {
            List<String> errors = new ArrayList<>();

            JsonObject tokensObj = unwrapTokensRoot(JsonParser.parseString(
                    Files.readString(resolveProjectPath(DICT_REL_DIR + "/tokens.json"), StandardCharsets.UTF_8)).getAsJsonObject());
            for (Map.Entry<String, JsonElement> e : tokensObj.entrySet()) {
                JsonObject obj = e.getValue().getAsJsonObject();
                if (obj.has("zh_tw")) {
                    checkPlaceholderAndEncoding("tokens[" + e.getKey() + "].zh_tw", obj.get("zh_tw").getAsString(), errors);
                }
            }

            JsonObject itemsObj = unwrapItemsRoot(JsonParser.parseString(
                    Files.readString(resolveProjectPath(DICT_REL_DIR + "/items.json"), StandardCharsets.UTF_8)).getAsJsonObject());
            for (Map.Entry<String, JsonElement> e : itemsObj.entrySet()) {
                JsonObject obj = e.getValue().getAsJsonObject();
                if (obj.has("name_zh_tw")) {
                    checkPlaceholderAndEncoding("items[" + e.getKey() + "].name_zh_tw", obj.get("name_zh_tw").getAsString(), errors);
                }
                if (obj.has("lore_template") && obj.get("lore_template").isJsonArray()) {
                    int idx = 0;
                    for (JsonElement le : obj.getAsJsonArray("lore_template")) {
                        checkPlaceholderAndEncoding("items[" + e.getKey() + "].lore[" + idx + "]", le.getAsString(), errors);
                        idx++;
                    }
                }
            }

            JsonObject dlgRoot = JsonParser.parseString(
                    Files.readString(resolveProjectPath(DICT_REL_DIR + "/dialogues.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject dlgMap = unwrapDialoguesRoot(dlgRoot);
            for (Map.Entry<String, JsonElement> e : dlgMap.entrySet()) {
                if ("meta".equals(e.getKey()) || "patterns".equals(e.getKey())) continue;
                String val = e.getValue().isJsonPrimitive()
                        ? e.getValue().getAsString()
                        : e.getValue().getAsJsonObject().get("zh_tw").getAsString();
                checkPlaceholderAndEncoding("dialogues[" + e.getKey() + "]", val, errors);
            }
            if (dlgRoot.has("patterns") && dlgRoot.get("patterns").isJsonArray()) {
                int idx = 0;
                for (JsonElement pe : dlgRoot.getAsJsonArray("patterns")) {
                    JsonObject po = pe.getAsJsonObject();
                    if (po.has("zh_tw")) {
                        checkPlaceholderAndEncoding("dialogues.patterns[" + idx + "]", po.get("zh_tw").getAsString(), errors);
                    }
                    idx++;
                }
            }

            Path loreLinesPath = resolveProjectPath(DICT_REL_DIR + "/lore_lines.json");
            if (Files.exists(loreLinesPath)) {
                JsonObject llRoot = JsonParser.parseString(Files.readString(loreLinesPath, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject llMap = unwrapLoreLinesRoot(llRoot);
                for (Map.Entry<String, JsonElement> e : llMap.entrySet()) {
                    if ("meta".equals(e.getKey())) continue;
                    String val = extractLoreLineZhTw(e.getValue());
                    if (val != null) {
                        checkPlaceholderAndEncoding("lore_lines[" + e.getKey() + "]", val, errors);
                    }
                }
            }

            assertTrue(errors.isEmpty(),
                    "Found " + errors.size() + " placeholder/encoding error(s). First 10:\n"
                            + String.join("\n", errors.subList(0, Math.min(10, errors.size()))));
        }

        @Test
        @DisplayName("R2-T2-02: Zero Simplified Chinese Regressions & Zero False Positives on Valid Non-Big5 TC")
        void testZeroSimplifiedChineseRegressions() throws IOException {
            // Sanity guard: legitimate non-Big5 Traditional Chinese characters must NOT trigger SC detector
            assertTrue(findScViolations("sanity-nonbig5", "\u87CE\u9C5D").isEmpty(),
                    "Legitimate non-Big5 TC chars (U+87CE, U+9C5D) must not be flagged as SC");

            List<String> hits = new ArrayList<>();

            TokenDictionary tokens = loadProdTokens();
            for (TokenRecord rec : tokens.getTokens().values()) {
                hits.addAll(findScViolations("tokens[" + rec.tokenId() + "]", rec.zh_tw()));
            }

            ItemDictionary items = loadProdItems();
            for (ItemTranslationRecord rec : items.getItems().values()) {
                hits.addAll(findScViolations("items[" + rec.id() + "].name_zh_tw", rec.name_zh_tw()));
                for (int i = 0; i < rec.lore_template().size(); i++) {
                    hits.addAll(findScViolations("items[" + rec.id() + "].lore[" + i + "]", rec.lore_template().get(i)));
                }
            }

            JsonObject dlgRoot = JsonParser.parseString(
                    Files.readString(resolveProjectPath(DICT_REL_DIR + "/dialogues.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject dlgMap = unwrapDialoguesRoot(dlgRoot);
            for (Map.Entry<String, JsonElement> e : dlgMap.entrySet()) {
                if ("meta".equals(e.getKey()) || "patterns".equals(e.getKey())) continue;
                String val = e.getValue().isJsonPrimitive()
                        ? e.getValue().getAsString()
                        : e.getValue().getAsJsonObject().get("zh_tw").getAsString();
                hits.addAll(findScViolations("dialogues[" + e.getKey() + "]", val));
            }
            if (dlgRoot.has("patterns") && dlgRoot.get("patterns").isJsonArray()) {
                int idx = 0;
                for (JsonElement pe : dlgRoot.getAsJsonArray("patterns")) {
                    JsonObject po = pe.getAsJsonObject();
                    if (po.has("zh_tw")) {
                        hits.addAll(findScViolations("dialogues.patterns[" + idx + "]", po.get("zh_tw").getAsString()));
                    }
                    idx++;
                }
            }

            Path loreLinesPath = resolveProjectPath(DICT_REL_DIR + "/lore_lines.json");
            if (Files.exists(loreLinesPath)) {
                JsonObject llRoot = JsonParser.parseString(Files.readString(loreLinesPath, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject llMap = unwrapLoreLinesRoot(llRoot);
                for (Map.Entry<String, JsonElement> e : llMap.entrySet()) {
                    if ("meta".equals(e.getKey())) continue;
                    String val = extractLoreLineZhTw(e.getValue());
                    if (val != null) {
                        hits.addAll(findScViolations("lore_lines[" + e.getKey() + "]", val));
                    }
                }
            }

            assertTrue(hits.isEmpty(),
                    "Detected " + hits.size() + " Simplified Chinese regression(s). First 10:\n"
                            + String.join("\n", hits.subList(0, Math.min(10, hits.size()))));
        }

        @Test
        @DisplayName("R2-T2-03: Zero Unresolved {STAT_*} Placeholders After TokenDictionary.resolveString()")
        void testZeroUnresolvedStatPlaceholders() throws IOException {
            TokenDictionary tokens = loadProdTokens();
            ItemDictionary items = loadProdItems();

            List<String> unresolved = new ArrayList<>();
            for (ItemTranslationRecord rec : items.getItems().values()) {
                for (int i = 0; i < rec.lore_template().size(); i++) {
                    String line = rec.lore_template().get(i);
                    String resolved = tokens.resolveString(line);
                    Matcher m = UNRESOLVED_STAT_PATTERN.matcher(resolved);
                    while (m.find()) {
                        unresolved.add("items[" + rec.id() + "].lore[" + i + "] unresolved " + m.group(0));
                    }
                }
            }

            assertTrue(unresolved.isEmpty(),
                    "Found " + unresolved.size() + " unresolved {STAT_*} placeholder(s). First 10:\n"
                            + String.join("\n", unresolved.subList(0, Math.min(10, unresolved.size()))));
        }

        @Test
        @DisplayName("R2-T2-04: Lore Line Classification & Effective CJK Coverage Thresholds")
        void testLoreLineClassificationAndCoverageThresholds() throws IOException {
            TokenDictionary tokens = loadProdTokens();
            ItemDictionary items = loadProdItems();
            LoreTelemetry t = computeLoreTelemetry(items, tokens);

            double totalRatio = t.resolvedCjkLoreLines() / (double) t.totalLoreLines();
            double translatableRatio = t.resolvedCjkLoreLines() / (double) t.translatableLines();

            System.out.printf(
                    "[R2-LoreTelemetry] items=%d, lore_items=%d, totalLore=%d, blankOrFmt=%d, numericSymbol=%d, " +
                    "placeholderOnly=%d, rawCjk=%d, englishOnly=%d, resolvedCjk=%d, translatable=%d, " +
                    "totalRatio=%.2f%%, translatableRatio=%.2f%%%n",
                    t.totalItems(), t.loreItems(), t.totalLoreLines(), t.blankOrFmtLines(), t.numericSymbolLines(),
                    t.placeholderOnlyLines(), t.rawCjkLines(), t.englishOnlyLines(), t.resolvedCjkLoreLines(),
                    t.translatableLines(), totalRatio * 100.0, translatableRatio * 100.0
            );

            assertEquals(t.totalLoreLines(),
                    t.blankOrFmtLines() + t.numericSymbolLines() + t.placeholderOnlyLines() + t.rawCjkLines() + t.englishOnlyLines(),
                    "Every lore line must belong to exactly one classification bucket");
            assertTrue(t.translatableLines() > 0, "Translatable lore lines must be positive");
            assertTrue(totalRatio >= 0.55,
                    String.format("Expected resolvedCjkLoreLines / totalLoreLines >= 55%%, got %.2f%% (%d/%d)",
                            totalRatio * 100.0, t.resolvedCjkLoreLines(), t.totalLoreLines()));
            assertTrue(translatableRatio >= 0.85,
                    String.format("Expected resolvedCjkLoreLines / translatableLines >= 85%%, got %.2f%% (%d/%d)",
                            translatableRatio * 100.0, t.resolvedCjkLoreLines(), t.translatableLines()));
        }
    }

    // =========================================================================
    // Tier 3: Multi-Module Production Integration Tests (2 tests)
    // =========================================================================
    @Nested
    @Tag("tier3")
    @DisplayName("Tier 3: Production Dictionary & Engine Integration")
    class Tier3RuntimeResolution {

        @Test
        @DisplayName("R2-T3-01: Production Item Rendering via TokenDictionary + LoreParser + ItemDictionary")
        void testProductionItemRenderingIntegration() throws IOException {
            TokenDictionary tokens = loadProdTokens();
            ItemDictionary items = loadProdItems();
            LoreParser parser = new LoreParser(tokens, new RomanNumeralParser());

            for (String itemId : List.of("BLAZEN_SPHERE", "NULL_SPHERE", "AATROX_BATPHONE", "6_ANNIVERSARY_BARN_SKIN", "HYPERION")) {
                ItemTranslationRecord rec = items.get(itemId);
                assertNotNull(rec, "ItemDictionary must contain " + itemId);
                assertTrue(containsCjk(rec.name_zh_tw()),
                        itemId + " name_zh_tw must contain CJK: " + rec.name_zh_tw());
                assertTrue(findScViolations(itemId + ".name_zh_tw", rec.name_zh_tw()).isEmpty(),
                        itemId + " name_zh_tw must have 0 SC regressions: " + rec.name_zh_tw());

                boolean hasCjkLore = false;
                for (String line : rec.lore_template()) {
                    String resolved = tokens.resolveString(line);
                    String rendered = parser.translateLoreLine(resolved);
                    assertFalse(UNRESOLVED_STAT_PATTERN.matcher(rendered).find(),
                            itemId + " rendered lore must not leak {STAT_*}: " + rendered);
                    assertTrue(findScViolations(itemId + ".lore", rendered).isEmpty(),
                            itemId + " rendered lore must not contain SC chars: " + rendered);
                    if (containsCjk(rendered)) {
                        hasCjkLore = true;
                    }
                }
                assertTrue(hasCjkLore, itemId + " must render CJK lore lines");
            }
        }

        @Test
        @DisplayName("R2-T3-02: Wiki [NPC] Name: Body & Option Lines via DialogueDictionary + TokenDictionary")
        void testWikiNpcAndOptionDialogueIntegration() throws IOException {
            TokenDictionary tokens = loadProdTokens();
            ItemDictionary items = loadProdItems();
            DialogueDictionary dialogues = loadProdDialogues(tokens, items);

            Path wikiPath = resolveProjectPath(WIKI_REL_PATH);
            JsonArray blocks = JsonParser.parseString(Files.readString(wikiPath, StandardCharsets.UTF_8)).getAsJsonArray();

            int testedNpcLines = 0;
            int translatedNpcLines = 0;
            for (JsonElement el : blocks) {
                String raw = el.getAsJsonObject().get("raw").getAsString();
                for (WikiCleanSubline subline : extractCleanWikiSublines(raw)) {
                    if (subline.cleanLine().startsWith("[NPC] ") && subline.cleanLine().contains(":")) {
                        String styledInput = "§e" + subline.cleanLine();
                        String out = dialogues.translate(styledInput);
                        assertTrue(out.startsWith("§e[NPC] "),
                                "Color prefix and [NPC] tag must be preserved: " + out);
                        assertTrue(findScViolations("wiki-npc", out).isEmpty(),
                                "Translated Wiki [NPC] line must have 0 SC chars: " + out);
                        if (!out.equals(styledInput) && containsCjk(out)) {
                            translatedNpcLines++;
                        }
                        testedNpcLines++;
                        if (testedNpcLines >= 25) break;
                    }
                }
                if (testedNpcLines >= 25) break;
            }
            assertTrue(testedNpcLines >= 25, "Must sample at least 25 real Wiki [NPC] lines, sampled " + testedNpcLines);
            assertTrue(translatedNpcLines >= 22,
                    "Expected at least 22/25 (>= 88%) sampled Wiki [NPC] lines to translate to CJK, got "
                            + translatedNpcLines + "/" + testedNpcLines);
        }
    }

    // =========================================================================
    // Tier 4: Real-World Production Telemetry & Session Workload (2 tests)
    // =========================================================================
    @Nested
    @Tag("tier4")
    @DisplayName("Tier 4: Full Production Audit & Simulated Workload")
    class Tier4TelemetryAndWorkloads {

        @Test
        @DisplayName("R2-T4-01: Full Production Quality Gate Telemetry Audit Across All Sources")
        void testFullProductionQualityTelemetryAudit() throws IOException {
            TokenDictionary tokens = loadProdTokens();
            ItemDictionary items = loadProdItems();
            DialogueDictionary dialogues = loadProdDialogues(tokens, items);

            assertTrue(tokens.size() >= 1050);
            assertTrue(items.size() >= 9500);
            assertTrue(dialogues.size() >= 4500);
            assertTrue(dialogues.patternCount() >= 31);

            LoreTelemetry lore = computeLoreTelemetry(items, tokens);
            assertTrue(lore.loreItems() >= 8900);
            assertTrue(lore.nameWithCjk() / (double) lore.totalItems() >= 0.995);
            assertTrue(lore.resolvedCjkLoreLines() / (double) lore.totalLoreLines() >= 0.55);
            assertTrue(lore.resolvedCjkLoreLines() / (double) lore.translatableLines() >= 0.85);

            JsonArray blocks = JsonParser.parseString(
                    Files.readString(resolveProjectPath(WIKI_REL_PATH), StandardCharsets.UTF_8)).getAsJsonArray();
            int totalBlocks = 0, coveredBlocks = 0, totalLines = 0, coveredLines = 0;
            for (JsonElement el : blocks) {
                List<WikiCleanSubline> subs = extractCleanWikiSublines(el.getAsJsonObject().get("raw").getAsString());
                if (subs.isEmpty()) continue;
                totalBlocks++;
                boolean any = false;
                for (WikiCleanSubline sub : subs) {
                    totalLines++;
                    if (isWikiSublineCovered(dialogues, sub)) {
                        coveredLines++;
                        any = true;
                    }
                }
                if (any) coveredBlocks++;
            }
            assertTrue(coveredLines / (double) totalLines >= 0.85);
            assertTrue(coveredBlocks / (double) totalBlocks >= 0.85);
        }

        @Test
        @DisplayName("R2-T4-02: Simulated 50-Item + 50-Wiki-Dialogue + Live-Capture Session Workload")
        void testSimulatedProductionTranslationWorkload() throws IOException {
            TokenDictionary tokens = loadProdTokens();
            ItemDictionary items = loadProdItems();
            DialogueDictionary dialogues = loadProdDialogues(tokens, items);
            LoreLineDictionary loreLines = new LoreLineDictionary();
            Path loreLinesPath = resolveProjectPath(DICT_REL_DIR + "/lore_lines.json");
            if (Files.exists(loreLinesPath)) {
                loreLines.loadFromFile(loreLinesPath);
            }
            LoreParser prodParser = new LoreParser(tokens, new RomanNumeralParser(), loreLines);
            DynamicItemCombiner prodCombiner = new DynamicItemCombiner(tokens, prodParser);
            ModConfig cfg = new ModConfig();
            cfg.setUntranslatedCapture(false);
            com.hypixel.translation.core.TranslationCore core = new com.hypixel.translation.core.TranslationCore(
                    tokens, items, dialogues, prodCombiner, prodParser,
                    new UntranslatedCaptureQueue(100),
                    new com.hypixel.translation.interception.InterceptionManager(),
                    new com.hypixel.translation.engine.TextTraverser(),
                    cfg
            );

            int itemsChecked = 0;
            for (ItemTranslationRecord rec : items.getItems().values()) {
                if (rec.lore_template().isEmpty()) continue;
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal("§6" + rec.name_en()));
                for (String rawLore : rec.lore_template()) {
                    tooltip.add(Component.literal(tokens.resolveString(rawLore)));
                }
                List<Component> out = prodCombiner.translateTooltip(null, tooltip, false);
                assertEquals(tooltip.size(), out.size(), "Tooltip line count preserved for " + rec.id());
                for (Component c : out) {
                    String s = c.getString();
                    assertFalse(UNRESOLVED_STAT_PATTERN.matcher(s).find(), "No {STAT_*} leak in " + rec.id());
                    assertTrue(findScViolations(rec.id(), s).isEmpty(), "No SC regression in " + rec.id());
                }
                itemsChecked++;
                if (itemsChecked >= 50) break;
            }
            assertEquals(50, itemsChecked, "Must exercise 50 lore-bearing items");

            JsonArray blocks = JsonParser.parseString(
                    Files.readString(resolveProjectPath(WIKI_REL_PATH), StandardCharsets.UTF_8)).getAsJsonArray();
            int dialoguesChecked = 0;
            for (JsonElement el : blocks) {
                List<WikiCleanSubline> subs = extractCleanWikiSublines(el.getAsJsonObject().get("raw").getAsString());
                if (subs.isEmpty()) continue;
                for (WikiCleanSubline sub : subs) {
                    String out = core.translateAndCapture(sub.cleanLine(), ComponentCategory.CHAT);
                    assertNotNull(out);
                    assertTrue(findScViolations("session-wiki", out).isEmpty());
                }
                dialoguesChecked++;
                if (dialoguesChecked >= 50) break;
            }
            assertEquals(50, dialoguesChecked, "Must exercise 50 Wiki dialogue blocks");

            for (String cap : List.of("My storage is full! :(", "Hypixel Store", "Pinging...")) {
                String out = core.translateAndCapture(cap, ComponentCategory.ENTITY_TAGS);
                assertTrue(containsCjk(out), "Live capture must resolve via TranslationCore: " + cap + " -> " + out);
            }
        }
    }
}
