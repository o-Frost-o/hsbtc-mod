package com.hypixel.translation.e2e;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.config.ModConfig;
import com.hypixel.translation.core.DialogueDictionary;
import com.hypixel.translation.core.ItemDictionary;
import com.hypixel.translation.core.ItemTranslationRecord;
import com.hypixel.translation.core.TokenDictionary;
import com.hypixel.translation.core.UntranslatedCaptureQueue;
import com.hypixel.translation.engine.DynamicItemCombiner;
import com.hypixel.translation.engine.LoreParser;
import com.hypixel.translation.engine.RomanNumeralParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R4 E2E Sync, Build Hygiene & Deployment Suite (8 tests: 3 T1, 3 T2, 1 T3, 1 T4).
 * Verifies 3-way SHA-256 dictionary parity, JAR packaging parity, UTF-8 no-BOM JSON validity,
 * run_tests.ps1 and deploy.ps1 completeness, zero stray .class files in src/, and behavioral
 * parity between bundled and external config TranslationCore instances.
 */
@Tag("e2e")
@Tag("r4")
@DisplayName("R4: 3-Way Dictionary Sync, Test Runner & Deployment E2E Suite")
public class R4SyncAndDeployE2ETest extends BaseE2ETest {

    private static final String BUNDLED_DICT_REL = "src/main/resources/assets/hsbtr/dictionaries";
    private static final String PROJECT_CONFIG_DICT_REL = "config/hypixel_skyblock_translation";
    private static final Path ACTIVE_PRISM_CONFIG_DICT_DIR = Path.of(
            "D:\\PrismLauncher\\instances\\26.2\\minecraft\\config\\hypixel_skyblock_translation");
    private static final Path PRISM_CONFIG_DICT_DIR = Path.of(
            "C:\\Users\\User\\AppData\\Roaming\\PrismLauncher\\instances\\26.2\\minecraft\\config\\hypixel_skyblock_translation");
    private static final Path ACTIVE_PRISM_MOD_JAR = Path.of(
            "D:\\PrismLauncher\\instances\\26.2\\minecraft\\mods\\hypixel_skyblock_translation-1.0.0.jar");
    private static final Path PRISM_MOD_JAR = Path.of(
            "C:\\Users\\User\\AppData\\Roaming\\PrismLauncher\\instances\\26.2\\minecraft\\mods\\hypixel_skyblock_translation-1.0.0.jar");

    private static final List<String> CORE_DICT_FILES = List.of("tokens.json", "items.json", "dialogues.json");

    private static Path resolvePrismConfigDir() {
        if (Files.isDirectory(ACTIVE_PRISM_CONFIG_DICT_DIR)) return ACTIVE_PRISM_CONFIG_DICT_DIR;
        return PRISM_CONFIG_DICT_DIR;
    }

    private static Path resolveProjectPath(String relativePath) {
        Path p = Path.of(relativePath);
        if (Files.exists(p)) return p;
        Path fallback = Path.of("C:\\Users\\User\\teamwork_projects\\hypixel_skyblock_translation", relativePath);
        if (Files.exists(fallback)) return fallback;
        return p;
    }

    private static Path resolveModJar() {
        if (Files.exists(ACTIVE_PRISM_MOD_JAR)) return ACTIVE_PRISM_MOD_JAR;
        if (Files.exists(PRISM_MOD_JAR)) return PRISM_MOD_JAR;
        return resolveProjectPath("build/libs/hypixel_skyblock_translation-1.0.0.jar");
    }

    private static List<String> expectedDictionaryFiles(Path bundledDir) {
        List<String> files = new ArrayList<>(CORE_DICT_FILES);
        if (Files.exists(bundledDir.resolve("lore_lines.json"))) {
            files.add("lore_lines.json");
        }
        return files;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String sha256File(Path path) throws IOException {
        return sha256Hex(Files.readAllBytes(path));
    }

    private static void assertValidUtf8NoBomJson(Path file) throws IOException {
        assertTrue(Files.exists(file), "JSON file must exist: " + file);
        byte[] raw = Files.readAllBytes(file);
        assertFalse(raw.length >= 3
                        && (raw[0] & 0xFF) == 0xEF
                        && (raw[1] & 0xFF) == 0xBB
                        && (raw[2] & 0xFF) == 0xBF,
                "JSON file must NOT start with UTF-8 BOM: " + file);
        String text = new String(raw, StandardCharsets.UTF_8);
        assertFalse(text.isBlank(), "JSON file must not be blank: " + file);
        assertFalse(text.contains("\uFFFD"), "JSON file must not contain U+FFFD: " + file);
        JsonElement parsed = JsonParser.parseString(text);
        assertTrue(parsed.isJsonObject() || parsed.isJsonArray(),
                "JSON file must parse to a valid JSON object/array: " + file);
    }

    private static void assertRunTestsIncludesClass(String content, String cls) {
        assertTrue(content.contains(cls + ".java"),
                "run_tests.ps1 must compile " + cls + ".java");
        assertTrue(Pattern.compile("--select-class\\s+com\\.hypixel\\.translation\\.[a-z0-9_.]+\\." + Pattern.quote(cls) + "\\b")
                        .matcher(content).find(),
                "run_tests.ps1 must invoke --select-class for " + cls);
    }

    private static com.hypixel.translation.core.TranslationCore loadCoreFromDirectory(Path dictDir) throws IOException {
        TokenDictionary tokens = new TokenDictionary();
        tokens.loadFromFile(dictDir.resolve("tokens.json"));

        ItemDictionary items = new ItemDictionary();
        items.loadFromFile(dictDir.resolve("items.json"));

        DialogueDictionary dialogues = new DialogueDictionary(tokens, items);
        dialogues.loadFromFile(dictDir.resolve("dialogues.json"));

        LoreParser parser = new LoreParser(tokens, new RomanNumeralParser());
        DynamicItemCombiner combiner = new DynamicItemCombiner(tokens, parser);
        ModConfig cfg = new ModConfig();
        cfg.setUntranslatedCapture(false);

        return new com.hypixel.translation.core.TranslationCore(
                tokens, items, dialogues, combiner, parser,
                new UntranslatedCaptureQueue(100),
                new com.hypixel.translation.interception.InterceptionManager(),
                new com.hypixel.translation.engine.TextTraverser(),
                cfg
        );
    }

    // =========================================================================
    // Tier 1: 3-Way Parity, JAR Packaging & Runner Completeness (3 tests)
    // =========================================================================
    @Nested
    @Tag("tier1")
    @DisplayName("Tier 1: 3-Way Dictionary Parity, JAR Packaging & Test Runner")
    class Tier1SyncAndPackaging {

        @Test
        @DisplayName("R4-T1-01: 3-Way SHA-256 Dictionary Parity Across Bundled, Project Config & PrismLauncher Config")
        void testThreeWayDictionarySha256Parity() throws IOException {
            Path bundledDir = resolveProjectPath(BUNDLED_DICT_REL);
            Path projectConfigDir = resolveProjectPath(PROJECT_CONFIG_DICT_REL);

            Path prismConfigDir = resolvePrismConfigDir();
            assertTrue(Files.isDirectory(bundledDir), "Bundled dictionary dir must exist: " + bundledDir);
            assertTrue(Files.isDirectory(projectConfigDir), "Project config dictionary dir must exist: " + projectConfigDir);
            assertTrue(Files.isDirectory(prismConfigDir),
                    "PrismLauncher config dictionary dir must exist: " + prismConfigDir);

            for (String fileName : expectedDictionaryFiles(bundledDir)) {
                Path p1 = bundledDir.resolve(fileName);
                Path p2 = projectConfigDir.resolve(fileName);
                Path p3 = prismConfigDir.resolve(fileName);

                assertTrue(Files.exists(p1), "Missing bundled dictionary: " + p1);
                assertTrue(Files.exists(p2), "Missing project config dictionary: " + p2);
                assertTrue(Files.exists(p3), "Missing PrismLauncher config dictionary: " + p3);

                String h1 = sha256File(p1);
                String h2 = sha256File(p2);
                String h3 = sha256File(p3);

                assertEquals(h1, h2, fileName + " SHA-256 mismatch between bundled resources and project config/");
                assertEquals(h1, h3, fileName + " SHA-256 mismatch between bundled resources and PrismLauncher config/");
            }
        }

        @Test
        @DisplayName("R4-T1-02: Deployed JAR Bundles Up-to-Date Dictionaries Matching Source SHA-256")
        void testDeployedJarDictionaryParity() throws IOException {
            Path bundledDir = resolveProjectPath(BUNDLED_DICT_REL);
            Path modJar = resolveModJar();
            assertTrue(Files.exists(modJar), "Mod JAR must exist at PrismLauncher mods/ or build/libs/: " + modJar);

            try (JarFile jar = new JarFile(modJar.toFile())) {
                for (String fileName : expectedDictionaryFiles(bundledDir)) {
                    String entryPath = "assets/hsbtr/dictionaries/" + fileName;
                    JarEntry entry = jar.getJarEntry(entryPath);
                    assertNotNull(entry, "JAR " + modJar + " must contain " + entryPath);

                    byte[] jarBytes;
                    try (InputStream is = jar.getInputStream(entry)) {
                        jarBytes = is.readAllBytes();
                    }
                    String jarHash = sha256Hex(jarBytes);
                    String srcHash = sha256File(bundledDir.resolve(fileName));

                    assertEquals(srcHash, jarHash,
                            "JAR entry " + entryPath + " SHA-256 must match bundled source " + fileName);
                }
            }
        }

        @Test
        @DisplayName("R4-T1-03: run_tests.ps1 Includes SchemaNormalizerTest and All R1-R4 E2E Suites")
        void testRunTestsScriptIncludesAllRequiredSuites() throws IOException {
            Path runTests = resolveProjectPath("run_tests.ps1");
            assertTrue(Files.exists(runTests), "run_tests.ps1 must exist at project root");

            String content = Files.readString(runTests, StandardCharsets.UTF_8);
            List<String> requiredClasses = List.of(
                    "SchemaNormalizerTest",
                    "R1ComponentStyleE2ETest",
                    "R2DictionaryQualityE2ETest",
                    "R3NoLocalModelE2ETest",
                    "R4SyncAndDeployE2ETest",
                    "Tier1FeatureCoverageTest",
                    "Tier2BoundaryCornerTest",
                    "Tier3PairwiseCombinationsTest",
                    "Tier4RealWorldWorkloadTest",
                    "Tier5AdversarialHardeningTest",
                    "Tier5InterceptionWebAdversarialTest",
                    "DeploymentValidationE2ETest"
            );

            for (String cls : requiredClasses) {
                assertRunTestsIncludesClass(content, cls);
            }
        }
    }

    // =========================================================================
    // Tier 2: Hygiene, UTF-8 No-BOM JSON & deploy.ps1 Contracts (3 tests)
    // =========================================================================
    @Nested
    @Tag("tier2")
    @DisplayName("Tier 2: Source Tree Hygiene, UTF-8 JSON Purity & deploy.ps1 Sync")
    class Tier2HygieneAndEncoding {

        @Test
        @DisplayName("R4-T2-01: Zero Stray .class Files in src/main/java or src/test/java")
        void testZeroStrayClassFilesInSourceTrees() throws IOException {
            List<String> strayClasses = new ArrayList<>();
            for (String rel : List.of("src/main/java", "src/test/java")) {
                Path dir = resolveProjectPath(rel);
                if (!Files.isDirectory(dir)) continue;
                try (Stream<Path> walk = Files.walk(dir)) {
                    for (Path p : walk.filter(Files::isRegularFile).toList()) {
                        if (p.getFileName().toString().endsWith(".class")) {
                            strayClasses.add(p.toString());
                        }
                    }
                }
            }

            assertTrue(strayClasses.isEmpty(),
                    "Found stray .class files in src/ source trees:\n" + String.join("\n", strayClasses));
        }

        @Test
        @DisplayName("R4-T2-02: All 3-Way Synced Dictionaries & config.json Are Valid UTF-8 (No BOM) JSON")
        void testAllSyncedDictionariesValidUtf8NoBomJson() throws IOException {
            Path bundledDir = resolveProjectPath(BUNDLED_DICT_REL);
            Path projectConfigDir = resolveProjectPath(PROJECT_CONFIG_DICT_REL);

            Path prismConfigDir = resolvePrismConfigDir();
            for (String fileName : expectedDictionaryFiles(bundledDir)) {
                assertValidUtf8NoBomJson(bundledDir.resolve(fileName));
                assertValidUtf8NoBomJson(projectConfigDir.resolve(fileName));
                if (Files.isDirectory(prismConfigDir)) {
                    assertValidUtf8NoBomJson(prismConfigDir.resolve(fileName));
                }
            }

            Path projConfigJson = projectConfigDir.resolve("config.json");
            if (Files.exists(projConfigJson)) {
                assertValidUtf8NoBomJson(projConfigJson);
            }
            Path prismConfigJson = prismConfigDir.resolve("config.json");
            if (Files.exists(prismConfigJson)) {
                assertValidUtf8NoBomJson(prismConfigJson);
            }
        }

        @Test
        @DisplayName("R4-T2-03: deploy.ps1 Targets PrismLauncher 26.2 mods/ and Syncs Dictionaries")
        void testDeployScriptTargetsPrismAndSyncsDictionaries() throws IOException {
            Path deployPs1 = resolveProjectPath("deploy.ps1");
            assertTrue(Files.exists(deployPs1), "deploy.ps1 must exist");

            String content = Files.readString(deployPs1, StandardCharsets.UTF_8);
            assertTrue(content.contains("C:\\Users\\User\\AppData\\Roaming\\PrismLauncher\\instances\\26.2\\minecraft\\mods"),
                    "deploy.ps1 must target PrismLauncher 26.2 minecraft/mods");
            assertTrue(content.contains("gradlew.bat") && content.contains("build"),
                    "deploy.ps1 must invoke gradlew.bat build");
            assertTrue(content.contains("Copy-Item"),
                    "deploy.ps1 must copy built JAR and dictionaries");
            assertTrue(content.contains("config\\hypixel_skyblock_translation"),
                    "deploy.ps1 must target minecraft/config/hypixel_skyblock_translation");
            for (String dict : CORE_DICT_FILES) {
                assertTrue(content.contains(dict), "deploy.ps1 must reference dictionary " + dict);
            }
        }
    }

    // =========================================================================
    // Tier 3: Bundled vs External Runtime Parity (1 test)
    // =========================================================================
    @Nested
    @Tag("tier3")
    @DisplayName("Tier 3: Bundled vs External Config TranslationCore Parity")
    class Tier3RuntimeConfigParity {

        @Test
        @DisplayName("R4-T3-01: TranslationCore Loaded from Bundled vs PrismLauncher Config Yields Identical State & Output")
        void testBundledVsExternalConfigTranslationCoreParity() throws IOException {
            Path bundledDir = resolveProjectPath(BUNDLED_DICT_REL);
            Path prismConfigDir = resolvePrismConfigDir();
            assertTrue(Files.isDirectory(prismConfigDir),
                    "PrismLauncher external config dir must exist: " + prismConfigDir);

            com.hypixel.translation.core.TranslationCore bundledCore = loadCoreFromDirectory(bundledDir);
            com.hypixel.translation.core.TranslationCore externalCore = loadCoreFromDirectory(prismConfigDir);

            assertEquals(bundledCore.getTokenDictionary().size(), externalCore.getTokenDictionary().size(),
                    "TokenDictionary size must match between bundled and external config");
            assertEquals(bundledCore.getItemDictionary().size(), externalCore.getItemDictionary().size(),
                    "ItemDictionary size must match between bundled and external config");
            assertEquals(bundledCore.getDialogueDictionary().size(), externalCore.getDialogueDictionary().size(),
                    "DialogueDictionary exact entry count must match between bundled and external config");
            assertEquals(bundledCore.getDialogueDictionary().patternCount(), externalCore.getDialogueDictionary().patternCount(),
                    "DialogueDictionary pattern count must match between bundled and external config");

            for (String tokenId : List.of("STAT_DAMAGE", "STAT_INTELLIGENCE", "REFORGE_HEROIC", "UI_PURSE")) {
                assertEquals(
                        bundledCore.getTokenDictionary().resolveToken(tokenId),
                        externalCore.getTokenDictionary().resolveToken(tokenId),
                        "Token resolution mismatch for " + tokenId);
            }

            for (String itemId : List.of("HYPERION", "BLAZEN_SPHERE", "NULL_SPHERE", "AATROX_BATPHONE")) {
                ItemTranslationRecord r1 = bundledCore.getItemDictionary().get(itemId);
                ItemTranslationRecord r2 = externalCore.getItemDictionary().get(itemId);
                assertNotNull(r1, "Missing item in bundled dict: " + itemId);
                assertEquals(r1, r2, "Item record mismatch for " + itemId);
            }

            for (String text : List.of("My storage is full! :(", "Hypixel Store", "Pinging...", "Purse")) {
                assertEquals(
                        bundledCore.translateAndCapture(text, ComponentCategory.CHAT),
                        externalCore.translateAndCapture(text, ComponentCategory.CHAT),
                        "TranslationCore output mismatch for \"" + text + "\"");
            }
        }
    }

    // =========================================================================
    // Tier 4: Full Release-Readiness Manifest Gate (1 test)
    // =========================================================================
    @Nested
    @Tag("tier4")
    @DisplayName("Tier 4: Release Readiness Manifest Verification")
    class Tier4ReleaseReadinessManifest {

        @Test
        @DisplayName("R4-T4-01: Aggregate Release Gate (3-Way Parity + JAR Parity + run_tests.ps1 + Clean src/)")
        void testFullReleaseReadinessManifest() throws IOException {
            Path bundledDir = resolveProjectPath(BUNDLED_DICT_REL);
            Path projectConfigDir = resolveProjectPath(PROJECT_CONFIG_DICT_REL);
            Path prismConfigDir = resolvePrismConfigDir();
            Path modJar = resolveModJar();

            assertTrue(Files.exists(modJar), "Deployed/built mod JAR must exist: " + modJar);
            assertTrue(Files.isDirectory(prismConfigDir), "PrismLauncher config dir must exist");

            try (JarFile jar = new JarFile(modJar.toFile())) {
                for (String fileName : expectedDictionaryFiles(bundledDir)) {
                    String hBundled = sha256File(bundledDir.resolve(fileName));
                    String hProjCfg = sha256File(projectConfigDir.resolve(fileName));
                    String hPrismCfg = sha256File(prismConfigDir.resolve(fileName));

                    assertEquals(hBundled, hProjCfg, fileName + " bundled != project config");
                    assertEquals(hBundled, hPrismCfg, fileName + " bundled != PrismLauncher config");

                    JarEntry entry = jar.getJarEntry("assets/hsbtr/dictionaries/" + fileName);
                    assertNotNull(entry, "JAR missing " + fileName);
                    try (InputStream is = jar.getInputStream(entry)) {
                        assertEquals(hBundled, sha256Hex(is.readAllBytes()),
                                fileName + " bundled != JAR entry");
                    }
                }
            }

            String runTests = Files.readString(resolveProjectPath("run_tests.ps1"), StandardCharsets.UTF_8);
            for (String cls : List.of(
                    "SchemaNormalizerTest",
                    "R1ComponentStyleE2ETest",
                    "R2DictionaryQualityE2ETest",
                    "R3NoLocalModelE2ETest",
                    "R4SyncAndDeployE2ETest"
            )) {
                assertRunTestsIncludesClass(runTests, cls);
            }

            for (String rel : List.of("src/main/java", "src/test/java")) {
                try (Stream<Path> walk = Files.walk(resolveProjectPath(rel))) {
                    long stray = walk.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(".class"))
                            .count();
                    assertEquals(0L, stray, "Expected 0 stray .class files in " + rel);
                }
            }
        }
    }
}
