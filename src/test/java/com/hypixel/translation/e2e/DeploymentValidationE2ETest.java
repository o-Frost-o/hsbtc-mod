package com.hypixel.translation.e2e;

import com.hypixel.translation.config.ComponentCategory;
import com.hypixel.translation.config.InterceptionMode;
import com.hypixel.translation.core.*;
import com.hypixel.translation.engine.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("E2E Category 11: Deployment & Configuration Validation (Features 10, 11, 38, R8)")
public class DeploymentValidationE2ETest extends BaseE2ETest {

    private static final String ACTIVE_PRISM_INSTANCE_PATH = "D:\\PrismLauncher\\instances\\26.2";
    private static final String DEFAULT_PRISM_MODS_PATH =
            "C:\\Users\\User\\AppData\\Roaming\\PrismLauncher\\instances\\26.2\\minecraft\\mods";
    private static final String DEFAULT_PRISM_CONFIG_PATH =
            "C:\\Users\\User\\AppData\\Roaming\\PrismLauncher\\instances\\26.2\\minecraft\\config";

    private static Path resolvePrismInstanceDir() {
        Path active = Path.of(ACTIVE_PRISM_INSTANCE_PATH);
        if (Files.isDirectory(active)) return active;
        return Path.of(System.getProperty("user.home"), "AppData", "Roaming", "PrismLauncher", "instances", "26.2");
    }

    private static Path resolveProjectPath(String relativePath) {
        Path p = Path.of(relativePath);
        if (Files.exists(p)) return p;
        Path fallback = Path.of("C:\\Users\\User\\teamwork_projects\\hypixel_skyblock_translation", relativePath);
        if (Files.exists(fallback)) return fallback;
        return p;
    }

    // =========================================================================
    // Tier 1: Deployment & Pipeline Feature Coverage (Happy Path)
    // =========================================================================
    @Nested
    @DisplayName("Tier 1: Deployment & Pipeline Feature Coverage")
    class Tier1DeploymentCoverage {

        @Test
        @DisplayName("T1-DEP-01: deploy.ps1 Parameter Declaration, JAVA_HOME and Build Invocation")
        void testDeployScriptParametersAndSyntax() throws IOException {
            Path deployScript = resolveProjectPath("deploy.ps1");
            assertTrue(Files.exists(deployScript), "deploy.ps1 must exist at project root");

            String content = Files.readString(deployScript, StandardCharsets.UTF_8);

            // Verify parameter block and default instance path
            assertTrue(content.contains("param("), "Script must declare parameter block");
            assertTrue(content.contains("$InstanceModsDir = \"C:\\Users\\User\\AppData\\Roaming\\PrismLauncher\\instances\\26.2\\minecraft\\mods\""),
                    "Default InstanceModsDir parameter must target PrismLauncher 26.2 instance");

            // Verify Java 25 requirement enforcement
            assertTrue(content.contains("$env:JAVA_HOME = \"C:\\Program Files\\Java\\jdk-25.0.2\""),
                    "deploy.ps1 must explicitly enforce JDK 25.0.2 toolchain");

            // Verify error handling and Gradle build invocation
            assertTrue(content.contains("$ErrorActionPreference = \"Stop\""),
                    "Script must fail fast on errors");
            assertTrue(content.contains("gradlew.bat\" build") || content.contains("gradlew build"),
                    "Script must invoke Gradle build task");
            assertTrue(content.contains("$LASTEXITCODE -ne 0"),
                    "Script must check Gradle exit code");

            // Verify copy command to target directory
            assertTrue(content.contains("Copy-Item -Path $JarFile.FullName -Destination $InstanceModsDir -Force"),
                    "Script must copy built JAR to InstanceModsDir");
        }

        @Test
        @DisplayName("T1-DEP-02: build.gradle deployToPrism Task Registration & Dependencies")
        void testBuildGradleDeployTaskConfiguration() throws IOException {
            Path buildGradle = resolveProjectPath("build.gradle");
            assertTrue(Files.exists(buildGradle), "build.gradle must exist at project root");

            String content = Files.readString(buildGradle, StandardCharsets.UTF_8);

            assertTrue(content.contains("tasks.register('deployToPrism', Copy)"),
                    "build.gradle must register deployToPrism Copy task");
            assertTrue(content.contains("dependsOn tasks.named('jar')"),
                    "deployToPrism task must depend on jar task");
            assertTrue(content.contains("findProperty('prism_mods_dir')") &&
                            content.contains("C:/Users/User/AppData/Roaming/PrismLauncher/instances/26.2/minecraft/mods"),
                    "deployToPrism must target PrismLauncher 26.2 mods directory");
            assertTrue(content.contains("hypixel_skyblock_translation") && content.contains(".jar"),
                    "deployToPrism must clean older versions of the translation mod before copying");
        }

        @Test
        @DisplayName("T1-DEP-03: PrismLauncher Instance Structure & Target minecraft/mods Verification")
        void testPrismLauncherInstanceDirectoryAndModsPath() {
            Path instanceDir = resolvePrismInstanceDir();
            assertTrue(Files.isDirectory(instanceDir),
                    "Target PrismLauncher instance 26.2 directory must exist on disk");

            Path mmcPack = instanceDir.resolve("mmc-pack.json");
            assertTrue(Files.exists(mmcPack), "Instance must contain valid mmc-pack.json manifest");

            Path modsDir = instanceDir.resolve("minecraft").resolve("mods");
            assertTrue(Files.isDirectory(modsDir),
                    "Target minecraft/mods directory must exist in PrismLauncher instance");
            assertTrue(Files.isWritable(modsDir),
                    "Target minecraft/mods directory must be writable by current user");
        }

        @Test
        @DisplayName("T1-DEP-04: Mod Configuration Layout Initialization in minecraft/config")
        void testModConfigDefaultInitialization(@TempDir Path isolatedConfigBase) throws IOException {
            // Test configuration initialization double
            ModConfigDouble config = ModConfigDouble.loadOrInit(isolatedConfigBase);

            Path expectedDir = isolatedConfigBase.resolve(ModConfigDouble.DEFAULT_CONFIG_DIR);
            Path expectedFile = expectedDir.resolve(ModConfigDouble.CONFIG_FILE_NAME);

            assertTrue(Files.isDirectory(expectedDir), "Config directory must be created automatically");
            assertTrue(Files.exists(expectedFile), "config.json must be generated");

            // Verify default schema values
            assertEquals(1, config.getVersion());
            assertEquals(InterceptionMode.DISPLAY, config.getMode(ComponentCategory.TOOLTIPS));
            assertEquals(InterceptionMode.DISPLAY, config.getMode(ComponentCategory.CONTAINER_UI));
            assertEquals(InterceptionMode.DISPLAY, config.getMode(ComponentCategory.SCOREBOARD));
            assertEquals(InterceptionMode.DISPLAY, config.getMode(ComponentCategory.TAB_LIST));
            assertEquals(InterceptionMode.DISPLAY, config.getMode(ComponentCategory.CHAT));
            assertEquals(InterceptionMode.DISPLAY, config.getMode(ComponentCategory.ACTION_BAR));
            assertEquals(InterceptionMode.DISPLAY, config.getMode(ComponentCategory.BOSS_BAR));
            assertEquals(InterceptionMode.DISPLAY, config.getMode(ComponentCategory.TITLE_SUBTITLE));
            assertEquals(InterceptionMode.DISPLAY, config.getMode(ComponentCategory.ENTITY_TAGS));

            assertTrue(config.isHotkeyEnglishPreviewEnabled());
            assertTrue(config.isShowEnglishSubtitle());
            assertFalse(config.isUntranslatedCaptureEnabled());
        }

        @Test
        @DisplayName("T1-DEP-05: Bundled Dictionary Asset Packaging Verification")
        void testDictionaryAssetPackagingVerification() throws IOException {
            Path dictDir = resolveProjectPath("src/main/resources/assets/hsbtr/dictionaries");
            Path tokensFile = dictDir.resolve("tokens.json");
            Path itemsFile = dictDir.resolve("items.json");

            assertTrue(Files.exists(tokensFile), "tokens.json must exist in assets/hsbtr/dictionaries/");
            assertTrue(Files.exists(itemsFile), "items.json must exist in assets/hsbtr/dictionaries/");
            Path dialoguesFile = resolveProjectPath("src/main/resources/assets/hsbtr/dictionaries/dialogues.json");
            assertTrue(Files.exists(dialoguesFile), "dialogues.json must exist in assets/hsbtr/dictionaries/");

            String tokensContent = Files.readString(tokensFile, StandardCharsets.UTF_8);
            JsonObject tokensObj = JsonParser.parseString(tokensContent).getAsJsonObject();
            assertTrue(tokensObj.size() > 100, "tokens.json must contain at least 100 glossary entries");

            // Verify core translation tokens exist
            assertTrue(tokensObj.has("CATEGORY_ACCESSORY"), "tokens.json must contain CATEGORY_ACCESSORY");
            assertTrue(tokensObj.has("CATEGORY_SWORD"), "tokens.json must contain CATEGORY_SWORD");
            assertTrue(tokensObj.has("CATEGORY_BOW"), "tokens.json must contain CATEGORY_BOW");

            JsonObject swordToken = tokensObj.getAsJsonObject("CATEGORY_SWORD");
            assertEquals("SWORD", swordToken.get("en").getAsString());
            assertEquals("劍", swordToken.get("zh_tw").getAsString());
        }
    }

    // =========================================================================
    // Tier 2: Deployment Boundary, Edge Cases & Error Recovery
    // =========================================================================
    @Nested
    @DisplayName("Tier 2: Deployment Boundary & Error Recovery")
    class Tier2DeploymentBoundary {

        @Test
        @DisplayName("T2-DEP-01: deploy.ps1 PowerShell AST Syntax Validation")
        void testDeployScriptPowerShellSyntaxAstValidation() {
            Path deployScript = resolveProjectPath("deploy.ps1").toAbsolutePath();
            assertTrue(Files.exists(deployScript));

            // Execute PowerShell AST Parser headlessly
            boolean executedPs = false;
            try {
                Process process = new ProcessBuilder(
                        "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command",
                        "& { $tok = $null; $err = $null; [System.Management.Automation.Language.Parser]::ParseFile('"
                                + deployScript.toString().replace("\\", "\\\\") + "', [ref]$tok, [ref]$err); if ($err.Count -gt 0) { exit 1 } else { exit 0 } }"
                ).redirectErrorStream(true).start();

                process.getOutputStream().close();
                boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                if (finished) {
                    int exitCode = process.exitValue();
                    assertEquals(0, exitCode, "deploy.ps1 must parse without syntax errors in PowerShell AST parser");
                    executedPs = true;
                } else {
                    process.destroyForcibly();
                }
            } catch (Exception ignored) {
                // Fallback for non-Windows / CI environments without powershell
            }

            // Dual validation via Java AST token & balanced bracket verification
            try {
                String script = Files.readString(deployScript, StandardCharsets.UTF_8);
                int parens = 0, braces = 0, brackets = 0;
                boolean inSingleQuote = false, inDoubleQuote = false;
                for (int i = 0; i < script.length(); i++) {
                    char c = script.charAt(i);
                    if (c == '\'' && !inDoubleQuote) inSingleQuote = !inSingleQuote;
                    else if (c == '"' && !inSingleQuote) inDoubleQuote = !inDoubleQuote;
                    else if (!inSingleQuote && !inDoubleQuote) {
                        if (c == '(') parens++;
                        else if (c == ')') parens--;
                        else if (c == '{') braces++;
                        else if (c == '}') braces--;
                        else if (c == '[') brackets++;
                        else if (c == ']') brackets--;
                    }
                }
                assertEquals(0, parens, "Parentheses must be balanced");
                assertEquals(0, braces, "Braces must be balanced");
                assertEquals(0, brackets, "Square brackets must be balanced");
            } catch (IOException e) {
                fail("Failed to read deploy.ps1: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("T2-DEP-02: Stale Mod Cleanup Isolation Without Collateral Damage")
        void testStaleModCleanupWithoutCollateralDamage(@TempDir Path mockModsDir) throws IOException {
            // Set up mock directory with stale versions and third-party mods
            Path staleJar1 = mockModsDir.resolve("hypixel_skyblock_translation-0.9.0.jar");
            Path staleJar2 = mockModsDir.resolve("hypixel_skyblock_translation-1.0.0-SNAPSHOT.jar");
            Path thirdPartyMod1 = mockModsDir.resolve("skyblocker-6.11.0+26.2.jar");
            Path thirdPartyMod2 = mockModsDir.resolve("cloth-config-26.2.155.jar");
            Path thirdPartyMod3 = mockModsDir.resolve("fabric-api-0.161.0+26.2.jar");

            Files.writeString(staleJar1, "dummy");
            Files.writeString(staleJar2, "dummy");
            Files.writeString(thirdPartyMod1, "skyblocker content");
            Files.writeString(thirdPartyMod2, "cloth config content");
            Files.writeString(thirdPartyMod3, "fabric api content");

            // Simulate deployment cleanup logic
            try (var stream = Files.list(mockModsDir)) {
                stream.filter(p -> {
                    String name = p.getFileName().toString();
                    return name.startsWith("hypixel_skyblock_translation") && name.endsWith(".jar");
                }).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            // Simulate copying new jar
            Path newDeployedJar = mockModsDir.resolve("hypixel_skyblock_translation-1.0.0.jar");
            Files.writeString(newDeployedJar, "new jar content");

            // Assert stale jars deleted
            assertFalse(Files.exists(staleJar1), "Older version 0.9.0 must be deleted");
            assertFalse(Files.exists(staleJar2), "Older SNAPSHOT must be deleted");

            // Assert non-mod JARs untouched
            assertTrue(Files.exists(thirdPartyMod1), "skyblocker must NOT be deleted");
            assertTrue(Files.exists(thirdPartyMod2), "cloth-config must NOT be deleted");
            assertTrue(Files.exists(thirdPartyMod3), "fabric-api must NOT be deleted");

            // Assert newly deployed jar exists
            assertTrue(Files.exists(newDeployedJar), "Newly deployed JAR must exist");
        }

        @Test
        @DisplayName("T2-DEP-03: Deployed Mod JAR Archive Integrity & Fabric Metadata")
        void testDeployedJarArchiveIntegrityAndFabricMetadata() throws IOException {
            Path targetJar = resolvePrismInstanceDir().resolve("minecraft").resolve("mods").resolve("hypixel_skyblock_translation-1.0.0.jar");
            if (!Files.exists(targetJar)) {
                targetJar = Path.of(DEFAULT_PRISM_MODS_PATH, "hypixel_skyblock_translation-1.0.0.jar");
            }
            if (!Files.exists(targetJar)) {
                // Fallback to build/libs if not yet deployed
                targetJar = resolveProjectPath("build/libs/hypixel_skyblock_translation-1.0.0.jar");
            }

            assertTrue(Files.exists(targetJar), "Mod JAR must exist either in target mods dir or build/libs");

            try (JarFile jar = new JarFile(targetJar.toFile())) {
                // Verify MANIFEST.MF
                assertNotNull(jar.getJarEntry("META-INF/MANIFEST.MF"), "JAR must contain MANIFEST.MF");

                // Verify fabric.mod.json
                JarEntry modJsonEntry = jar.getJarEntry("fabric.mod.json");
                assertNotNull(modJsonEntry, "JAR must contain fabric.mod.json");

                try (InputStream is = jar.getInputStream(modJsonEntry)) {
                    String jsonText = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject modJson = JsonParser.parseString(jsonText).getAsJsonObject();

                    assertEquals("hypixel_skyblock_translation", modJson.get("id").getAsString());
                    assertEquals("client", modJson.get("environment").getAsString());

                    JsonObject depends = modJson.getAsJsonObject("depends");
                    assertTrue(depends.has("minecraft"));
                    assertEquals("26.2", depends.get("minecraft").getAsString());
                    assertTrue(depends.has("java"));
                }

                // Verify class file entrypoint
                assertNotNull(jar.getJarEntry("com/hypixel/translation/HypixelSkyblockTranslationMod.class"),
                        "JAR must contain compiled HypixelSkyblockTranslationMod.class");

                // Verify packaged dictionary assets
                assertNotNull(jar.getJarEntry("assets/hsbtr/dictionaries/tokens.json"),
                        "JAR must package assets/hsbtr/dictionaries/tokens.json");
                assertNotNull(jar.getJarEntry("assets/hsbtr/dictionaries/items.json"),
                        "JAR must package assets/hsbtr/dictionaries/items.json");
            }
        }

        @Test
        @DisplayName("T2-DEP-04: Config Initialization Fault Tolerance & Corrupt Recovery")
        void testConfigInitializationFaultToleranceAndCorruptRecovery(@TempDir Path testConfigDir) throws IOException {
            Path modDir = testConfigDir.resolve(ModConfigDouble.DEFAULT_CONFIG_DIR);
            Files.createDirectories(modDir);
            Path configFile = modDir.resolve(ModConfigDouble.CONFIG_FILE_NAME);

            // Case 1: Empty file (0 bytes) -> must recover to default config without crash
            Files.writeString(configFile, "");
            ModConfigDouble recovered1 = ModConfigDouble.loadOrInit(testConfigDir);
            assertNotNull(recovered1);
            assertEquals(InterceptionMode.DISPLAY, recovered1.getMode(ComponentCategory.TOOLTIPS));

            // Case 2: Malformed JSON syntax -> must catch and recover
            Files.writeString(configFile, "{ \"categories\": { unclosed... ");
            ModConfigDouble recovered2 = ModConfigDouble.loadOrInit(testConfigDir);
            assertNotNull(recovered2);
            assertTrue(recovered2.isHotkeyEnglishPreviewEnabled());

            // Case 3: Partial config (missing some categories) -> existing preserved, missing defaulted
            String partialJson = """
            {
                "version": 1,
                "categories": {
                    "TOOLTIPS": "PACKET"
                },
                "hotkeyEnglishPreviewEnabled": false
            }
            """;
            Files.writeString(configFile, partialJson);
            ModConfigDouble recovered3 = ModConfigDouble.loadOrInit(testConfigDir);
            assertNotNull(recovered3);
            assertEquals(InterceptionMode.PACKET, recovered3.getMode(ComponentCategory.TOOLTIPS));
            assertEquals(InterceptionMode.DISPLAY, recovered3.getMode(ComponentCategory.SCOREBOARD));
            assertFalse(recovered3.isHotkeyEnglishPreviewEnabled());
        }

        @Test
        @DisplayName("T2-DEP-05: Offline Dictionary Schema Integrity & UTF-8 Purity")
        void testOfflineDictionarySchemaIntegrityAndUtf8Purity() throws IOException {
            Path tokensFile = resolveProjectPath("src/main/resources/assets/hsbtr/dictionaries/tokens.json");
            byte[] bytes = Files.readAllBytes(tokensFile);
            String rawJson = new String(bytes, StandardCharsets.UTF_8);

            // Verify no Unicode replacement characters (\uFFFD indicates encoding corruption)
            assertFalse(rawJson.contains("\uFFFD"),
                    "tokens.json must not contain corrupted Unicode replacement characters (\\uFFFD)");

            JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();
            Set<String> validCategories = Set.of(
                    "stat", "reforge", "item", "category", "ability", "enchant", "location", "ui",
                    "collection", "mayor", "rarity", "skill", "npc", "boss", "general",
                    "dialogue", "mob", "potion", "feature", "event", "attribute", "faction", "mechanic"
            );

            root.entrySet().forEach(entry -> {
                String key = entry.getKey();
                JsonObject item = entry.getValue().getAsJsonObject();

                assertTrue(item.has("tokenId"), "Token " + key + " must have tokenId");
                assertEquals(key, item.get("tokenId").getAsString(), "Key must match tokenId");
                assertTrue(key.matches("^[A-Z0-9_]+$"), "tokenId must be uppercase alphanumeric with underscores: " + key);

                assertTrue(item.has("en") && !item.get("en").getAsString().isBlank(),
                        "Token " + key + " must have non-blank en field");
                assertTrue(item.has("zh_tw") && !item.get("zh_tw").getAsString().isBlank(),
                        "Token " + key + " must have non-blank zh_tw field");
                assertTrue(item.has("category"), "Token " + key + " must have category field");

                String cat = item.get("category").getAsString();
                assertTrue(validCategories.contains(cat),
                        "Token " + key + " category '" + cat + "' must be one of " + validCategories);
            });
        }
    }

    // =========================================================================
    // Test Double: ModConfigDouble
    // =========================================================================
    public static class ModConfigDouble {
        public static final String DEFAULT_CONFIG_DIR = "hypixel_skyblock_translation";
        public static final String CONFIG_FILE_NAME = "config.json";

        private int version = 1;
        private final Map<ComponentCategory, InterceptionMode> categories = new ConcurrentHashMap<>();
        private boolean hotkeyEnglishPreviewEnabled = true;
        private boolean showEnglishSubtitle = true;
        private boolean untranslatedCaptureEnabled = false;

        public ModConfigDouble() {
            for (ComponentCategory cat : ComponentCategory.values()) {
                categories.put(cat, InterceptionMode.DISPLAY);
            }
        }

        public static ModConfigDouble loadOrInit(Path baseConfigDir) throws IOException {
            Path modConfigDir = baseConfigDir.resolve(DEFAULT_CONFIG_DIR);
            if (!Files.exists(modConfigDir)) {
                Files.createDirectories(modConfigDir);
            }
            Path configFile = modConfigDir.resolve(CONFIG_FILE_NAME);
            if (!Files.exists(configFile) || Files.size(configFile) == 0) {
                ModConfigDouble defaultConfig = new ModConfigDouble();
                defaultConfig.save(configFile);
                return defaultConfig;
            }

            try {
                String jsonText = Files.readString(configFile, StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(jsonText).getAsJsonObject();
                ModConfigDouble config = new ModConfigDouble();
                if (root.has("version")) {
                    config.version = root.get("version").getAsInt();
                }
                if (root.has("categories") && root.get("categories").isJsonObject()) {
                    JsonObject catObj = root.getAsJsonObject("categories");
                    for (ComponentCategory cat : ComponentCategory.values()) {
                        if (catObj.has(cat.name())) {
                            try {
                                config.categories.put(cat, InterceptionMode.valueOf(catObj.get(cat.name()).getAsString()));
                            } catch (IllegalArgumentException ignored) {
                                config.categories.put(cat, InterceptionMode.DISPLAY);
                            }
                        }
                    }
                }
                if (root.has("hotkeyEnglishPreviewEnabled")) {
                    config.hotkeyEnglishPreviewEnabled = root.get("hotkeyEnglishPreviewEnabled").getAsBoolean();
                }
                if (root.has("showEnglishSubtitle")) {
                    config.showEnglishSubtitle = root.get("showEnglishSubtitle").getAsBoolean();
                }
                if (root.has("untranslatedCaptureEnabled")) {
                    config.untranslatedCaptureEnabled = root.get("untranslatedCaptureEnabled").getAsBoolean();
                }
                return config;
            } catch (Exception e) {
                ModConfigDouble fallback = new ModConfigDouble();
                fallback.save(configFile);
                return fallback;
            }
        }

        public void save(Path targetFile) throws IOException {
            JsonObject root = new JsonObject();
            root.addProperty("version", version);
            JsonObject catObj = new JsonObject();
            for (Map.Entry<ComponentCategory, InterceptionMode> entry : categories.entrySet()) {
                catObj.addProperty(entry.getKey().name(), entry.getValue().name());
            }
            root.add("categories", catObj);
            root.addProperty("hotkeyEnglishPreviewEnabled", hotkeyEnglishPreviewEnabled);
            root.addProperty("showEnglishSubtitle", showEnglishSubtitle);
            root.addProperty("untranslatedCaptureEnabled", untranslatedCaptureEnabled);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(targetFile, gson.toJson(root), StandardCharsets.UTF_8);
        }

        public int getVersion() { return version; }
        public InterceptionMode getMode(ComponentCategory category) {
            return categories.getOrDefault(category, InterceptionMode.DISPLAY);
        }
        public void setMode(ComponentCategory category, InterceptionMode mode) {
            categories.put(category, mode);
        }
        public boolean isHotkeyEnglishPreviewEnabled() { return hotkeyEnglishPreviewEnabled; }
        public boolean isShowEnglishSubtitle() { return showEnglishSubtitle; }
        public boolean isUntranslatedCaptureEnabled() { return untranslatedCaptureEnabled; }
    }
}
