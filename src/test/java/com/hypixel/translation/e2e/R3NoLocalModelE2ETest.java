package com.hypixel.translation.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R3 E2E Constraint Verification Suite (7 tests: 3 T1, 2 T2, 1 T3, 1 T4).
 * Asserts zero local LLM or neural translation models, weights, dependencies,
 * or inference code exist across build files, Java sources, resources, config,
 * PowerShell/generator scripts, or the deployed mod JAR.
 */
@Tag("e2e")
@Tag("r3")
@DisplayName("R3: No Local Model Translation Constraint E2E Suite")
public class R3NoLocalModelE2ETest extends BaseE2ETest {

    private static final String ACTIVE_PRISM_JAR_PATH =
            "D:\\PrismLauncher\\instances\\26.2\\minecraft\\mods\\hypixel_skyblock_translation-1.0.0.jar";
    private static final String DEFAULT_PRISM_JAR_PATH =
            "C:\\Users\\User\\AppData\\Roaming\\PrismLauncher\\instances\\26.2\\minecraft\\mods\\hypixel_skyblock_translation-1.0.0.jar";

    private static final Set<String> MODEL_WEIGHT_EXTENSIONS = Set.of(
            ".gguf", ".onnx", ".safetensors", ".pt", ".pth", ".ckpt",
            ".tflite", ".mlmodel", ".llamafile"
    );

    private static final Set<String> MODEL_WEIGHT_FILENAMES = Set.of(
            "pytorch_model.bin", "model.bin", "spiece.model"
    );

    private static final Set<String> TEXT_SCAN_EXTENSIONS = Set.of(
            ".java", ".py", ".ps1", ".js", ".gradle", ".properties", ".json", ".txt", ".yml", ".yaml", ".toml"
    );

    private static final Set<String> EXCLUDED_DIR_NAMES = Set.of(
            ".gradle", "build", ".git", "cache", "logs", "__pycache__", ".agents", "test-libs"
    );

    // Scoped pattern: avoids bare \btorch\b so Minecraft "Torch" items never false-positive.
    private static final Pattern BANNED_LOCAL_MODEL_PATTERN = Pattern.compile(
            "\\bollama\\b"
                    + "|llama\\.cpp"
                    + "|llama_cpp"
                    + "|\\bctranslate2\\b"
                    + "|\\bargostranslate\\b"
                    + "|\\bonnxruntime\\b"
                    + "|\\bhuggingface\\b"
                    + "|\\bsentencepiece\\b"
                    + "|\\blmstudio\\b"
                    + "|\\bvllm\\b"
                    + "|\\bmarianmt\\b"
                    + "|\\bnllb\\b"
                    + "|\\b11434\\b"
                    + "|ai\\.djl"
                    + "|djl\\.ai"
                    + "|\\blangchain4j\\b"
                    + "|\\btensorflow\\b"
                    + "|(?:import\\s+torch|from\\s+torch\\b|\\bpytorch\\b|org\\.pytorch)"
                    + "|(?:import\\s+transformers|from\\s+transformers\\b|\\bAutoModelForSeq2SeqLM\\b|\\bAutoTokenizer\\b)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern BANNED_JAR_ENTRY_PATTERN = Pattern.compile(
            "ai/djl/|ai/onnxruntime/|dev/langchain4j/|org/pytorch/|org/tensorflow/"
                    + "|ollama|llama\\.cpp|llama_cpp|huggingface|sentencepiece|ctranslate2|argostranslate",
            Pattern.CASE_INSENSITIVE
    );

    private static Path resolveProjectPath(String relativePath) {
        Path p = Path.of(relativePath);
        if (Files.exists(p)) return p;
        Path fallback = Path.of("C:\\Users\\User\\teamwork_projects\\hypixel_skyblock_translation", relativePath);
        if (Files.exists(fallback)) return fallback;
        return p;
    }

    private static Path resolveProjectRoot() {
        Path p = Path.of("build.gradle");
        if (Files.exists(p)) return Path.of("").toAbsolutePath().normalize();
        Path fallback = Path.of("C:\\Users\\User\\teamwork_projects\\hypixel_skyblock_translation");
        if (Files.exists(fallback)) return fallback;
        return Path.of("").toAbsolutePath().normalize();
    }

    private static Path resolveModJar() {
        Path activeJar = Path.of(ACTIVE_PRISM_JAR_PATH);
        if (Files.exists(activeJar)) return activeJar;
        Path prismJar = Path.of(DEFAULT_PRISM_JAR_PATH);
        if (Files.exists(prismJar)) return prismJar;
        return resolveProjectPath("build/libs/hypixel_skyblock_translation-1.0.0.jar");
    }

    private static boolean isExcludedPath(Path projectRoot, Path candidate) {
        Path norm = candidate.toAbsolutePath().normalize();
        Path rel = norm.startsWith(projectRoot) ? projectRoot.relativize(norm) : norm;
        String relUnix = rel.toString().replace('\\', '/');
        if (relUnix.equals("src/test") || relUnix.startsWith("src/test/")) {
            return true;
        }
        for (Path part : rel) {
            if (EXCLUDED_DIR_NAMES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isModelWeightFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (MODEL_WEIGHT_FILENAMES.contains(name)) {
            return true;
        }
        for (String ext : MODEL_WEIGHT_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isScannableTextFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String ext : TEXT_SCAN_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> scanFileForBannedPatterns(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > 25L * 1024 * 1024) {
            return List.of();
        }
        String content = Files.readString(file, StandardCharsets.UTF_8);
        Matcher m = BANNED_LOCAL_MODEL_PATTERN.matcher(content);
        List<String> hits = new ArrayList<>();
        while (m.find()) {
            hits.add(file + " matched \"" + m.group(0) + "\"");
        }
        return hits;
    }

    private static List<String> scanDirectoryTree(Path rootDir, Path projectRoot, boolean enforceProjectExclusions) throws IOException {
        if (!Files.exists(rootDir)) {
            return List.of();
        }
        List<String> violations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(rootDir)) {
            List<Path> files = walk.filter(Files::isRegularFile).toList();
            for (Path f : files) {
                if (enforceProjectExclusions && isExcludedPath(projectRoot, f)) {
                    continue;
                }
                if (!enforceProjectExclusions) {
                    boolean skip = false;
                    for (Path part : f) {
                        if (EXCLUDED_DIR_NAMES.contains(part.toString())) {
                            skip = true;
                            break;
                        }
                    }
                    if (skip) continue;
                }

                if (isModelWeightFile(f)) {
                    violations.add("Model weight artifact found: " + f);
                } else if (isScannableTextFile(f)) {
                    violations.addAll(scanFileForBannedPatterns(f));
                }
            }
        }
        return violations;
    }

    // =========================================================================
    // Tier 1: Build, Production Java Source & Model Artifact Checks (3 tests)
    // =========================================================================
    @Nested
    @Tag("tier1")
    @DisplayName("Tier 1: Build Config, Production Sources & Model Artifact Scans")
    class Tier1BuildAndSourceScans {

        @Test
        @DisplayName("R3-T1-01: Build Files, fabric.mod.json & classpath.txt Contain Zero ML/LLM Dependencies")
        void testBuildAndClasspathHaveZeroLocalModelDeps() throws IOException {
            List<String> targets = List.of(
                    "build.gradle",
                    "gradle.properties",
                    "settings.gradle",
                    "src/main/resources/fabric.mod.json",
                    "build/classpath.txt"
            );

            List<String> violations = new ArrayList<>();
            int scanned = 0;
            for (String rel : targets) {
                Path p = resolveProjectPath(rel);
                if (Files.exists(p)) {
                    scanned++;
                    violations.addAll(scanFileForBannedPatterns(p));
                }
            }

            assertTrue(scanned >= 4, "Must scan at least 4 build/manifest files");
            assertTrue(violations.isEmpty(),
                    "Found banned local LLM/ML dependency in build config:\n" + String.join("\n", violations));
        }

        @Test
        @DisplayName("R3-T1-02: Production Java Sources (src/main/java/**) Contain Zero Local LLM/ML Code")
        void testProductionJavaSourcesZeroLocalModelCode() throws IOException {
            Path mainJava = resolveProjectPath("src/main/java");
            assertTrue(Files.isDirectory(mainJava), "src/main/java must exist");

            List<String> violations = scanDirectoryTree(mainJava, resolveProjectRoot(), true);
            assertTrue(violations.isEmpty(),
                    "Found banned local LLM/ML signature in src/main/java:\n" + String.join("\n", violations));
        }

        @Test
        @DisplayName("R3-T1-03: Workspace & Resources Contain Zero Model Weight or Tokenizer Artifacts")
        void testWorkspaceZeroModelWeightArtifacts() throws IOException {
            Path projectRoot = resolveProjectRoot();
            List<String> modelFiles = new ArrayList<>();

            try (Stream<Path> walk = Files.walk(projectRoot)) {
                for (Path p : walk.filter(Files::isRegularFile).toList()) {
                    if (isExcludedPath(projectRoot, p)) continue;
                    if (isModelWeightFile(p)) {
                        modelFiles.add(p.toString());
                    }
                }
            }

            assertTrue(modelFiles.isEmpty(),
                    "Found prohibited model weight/tokenizer files:\n" + String.join("\n", modelFiles));
        }
    }

    // =========================================================================
    // Tier 2: Generator Scripts, PowerShell Scripts & Deployed JAR (2 tests)
    // =========================================================================
    @Nested
    @Tag("tier2")
    @DisplayName("Tier 2: Generator Scripts, PowerShell & Deployed JAR Verification")
    class Tier2ScriptsAndJarScans {

        @Test
        @DisplayName("R3-T2-01: PowerShell, Config & Generator Scripts Have Zero Local LLM Calls (No Torch False Positives)")
        void testScriptsAndConfigZeroLocalModelCalls() throws IOException {
            // Guard against the "Torch" item false-positive trap
            assertFalse(BANNED_LOCAL_MODEL_PATTERN.matcher("Dreamspire Torch | Redstone Torch | \"Torch\": \"\u706B\u628A\"").find(),
                    "Minecraft Torch item names must NOT trigger local-model regex");

            Path projectRoot = resolveProjectRoot();
            List<String> violations = new ArrayList<>();

            // Root .ps1 scripts
            try (Stream<Path> rootFiles = Files.list(projectRoot)) {
                for (Path p : rootFiles.filter(Files::isRegularFile).toList()) {
                    String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (name.endsWith(".ps1") || name.endsWith(".py") || name.endsWith(".gradle") || name.endsWith(".properties")) {
                        violations.addAll(scanFileForBannedPatterns(p));
                    }
                }
            }

            // config/** and src/main/resources/**
            violations.addAll(scanDirectoryTree(resolveProjectPath("config"), projectRoot, true));
            violations.addAll(scanDirectoryTree(resolveProjectPath("src/main/resources"), projectRoot, true));

            // scripts/** if present
            Path scriptsDir = resolveProjectPath("scripts");
            if (Files.isDirectory(scriptsDir)) {
                violations.addAll(scanDirectoryTree(scriptsDir, projectRoot, false));
            }

            assertTrue(violations.isEmpty(),
                    "Found banned local LLM/ML artifact or reference in scripts/config:\n" + String.join("\n", violations));
        }

        @Test
        @DisplayName("R3-T2-02: Built/Deployed Mod JAR Contains Zero Model Weights or ML Runtime Packages")
        void testModJarZeroModelWeightsOrMlPackages() throws IOException {
            Path modJar = resolveModJar();
            assertTrue(Files.exists(modJar), "Mod JAR must exist at PrismLauncher mods/ or build/libs/");

            List<String> badEntries = new ArrayList<>();
            try (JarFile jar = new JarFile(modJar.toFile())) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    String name = entry.getName();
                    String lower = name.toLowerCase(Locale.ROOT);

                    for (String ext : MODEL_WEIGHT_EXTENSIONS) {
                        if (lower.endsWith(ext)) {
                            badEntries.add("Model weight in JAR: " + name);
                        }
                    }
                    for (String fn : MODEL_WEIGHT_FILENAMES) {
                        if (lower.endsWith("/" + fn) || lower.equals(fn)) {
                            badEntries.add("Model binary in JAR: " + name);
                        }
                    }
                    if (BANNED_JAR_ENTRY_PATTERN.matcher(name).find()) {
                        badEntries.add("Prohibited ML/LLM package/resource in JAR: " + name);
                    }
                }
            }

            assertTrue(badEntries.isEmpty(),
                    "Found prohibited ML/LLM entries in " + modJar + ":\n" + String.join("\n", badEntries));
        }
    }

    // =========================================================================
    // Tier 3: Pipeline Determinism Integration Audit (1 test)
    // =========================================================================
    @Nested
    @Tag("tier3")
    @DisplayName("Tier 3: Data Pipeline Deterministic Execution Audit")
    class Tier3PipelineDeterminism {

        @Test
        @DisplayName("R3-T3-01: DataPipelineRunner & Pipeline Classes Are Strictly Deterministic Without Local LLM Hooks")
        void testDataPipelineDeterministicWithoutLocalModels() throws IOException {
            Path buildGradle = resolveProjectPath("build.gradle");
            String gradleText = Files.readString(buildGradle, StandardCharsets.UTF_8);
            assertTrue(gradleText.contains("runDataPipeline") && gradleText.contains("DataPipelineRunner"),
                    "build.gradle runDataPipeline must target DataPipelineRunner");

            Path pipelineDir = resolveProjectPath("src/main/java/com/hypixel/translation/pipeline");
            assertTrue(Files.isDirectory(pipelineDir), "Pipeline source directory must exist");

            List<String> violations = scanDirectoryTree(pipelineDir, resolveProjectRoot(), true);
            try (Stream<Path> walk = Files.walk(pipelineDir)) {
                for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String src = Files.readString(p, StandardCharsets.UTF_8);
                    assertFalse(src.contains("ProcessBuilder") && src.toLowerCase(Locale.ROOT).contains("python"),
                            "Pipeline must not shell out to local Python ML models: " + p);
                    assertFalse(src.contains("127.0.0.1:11434") || src.contains("localhost:11434"),
                            "Pipeline must not call local Ollama endpoint: " + p);
                }
            }

            assertTrue(violations.isEmpty(),
                    "Pipeline sources must contain zero local-model violations:\n" + String.join("\n", violations));
        }
    }

    // =========================================================================
    // Tier 4: Full End-to-End R3 Release Audit (1 test)
    // =========================================================================
    @Nested
    @Tag("tier4")
    @DisplayName("Tier 4: End-to-End Zero-Local-Model Release Audit")
    class Tier4FullR3Audit {

        @Test
        @DisplayName("R3-T4-01: Aggregate R3 Audit Across Workspace, Scripts, Config, Classpath & JAR")
        void testFullEndToEndZeroLocalModelAudit() throws IOException {
            Path projectRoot = resolveProjectRoot();
            List<String> allViolations = new ArrayList<>();

            // 1. Build & classpath
            for (String rel : List.of("build.gradle", "gradle.properties", "settings.gradle", "build/classpath.txt")) {
                Path p = resolveProjectPath(rel);
                if (Files.exists(p)) {
                    allViolations.addAll(scanFileForBannedPatterns(p));
                }
            }

            // 2. Production sources, resources & config
            allViolations.addAll(scanDirectoryTree(resolveProjectPath("src/main"), projectRoot, true));
            allViolations.addAll(scanDirectoryTree(resolveProjectPath("config"), projectRoot, true));

            // 3. Root PowerShell scripts
            try (Stream<Path> rootFiles = Files.list(projectRoot)) {
                for (Path p : rootFiles.filter(f -> f.toString().toLowerCase(Locale.ROOT).endsWith(".ps1")).toList()) {
                    allViolations.addAll(scanFileForBannedPatterns(p));
                }
            }

            // 4. scripts dir (if present)
            Path scriptsDir = resolveProjectPath("scripts");
            if (Files.isDirectory(scriptsDir)) {
                allViolations.addAll(scanDirectoryTree(scriptsDir, projectRoot, false));
            }

            // 5. Deployed / built JAR
            Path modJar = resolveModJar();
            assertTrue(Files.exists(modJar), "Mod JAR must exist for R3 audit");
            try (JarFile jar = new JarFile(modJar.toFile())) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry je = entries.nextElement();
                    if (!je.isDirectory() && BANNED_JAR_ENTRY_PATTERN.matcher(je.getName()).find()) {
                        allViolations.add("JAR entry violation: " + je.getName());
                    }
                }
            }

            System.out.printf("[R3-Audit] Scanned projectRoot=%s, modJar=%s, violations=%d%n",
                    projectRoot, modJar, allViolations.size());
            assertTrue(allViolations.isEmpty(),
                    "R3 Zero-Local-Model Audit failed with violations:\n" + String.join("\n", allViolations));
        }
    }
}
