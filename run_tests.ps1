# Run all 4-Tier E2E and Component Tests
$ErrorActionPreference = "Stop"

$workspace = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $workspace
$outDir = "$workspace/build/test-classes"
$mainClasses = "$workspace/build/classes/java/main"
$cp = (Get-Content "$workspace/build/classpath.txt" -Raw).Replace("\", "/") + ";$mainClasses"

$files = @(
    "$workspace/src/test/java/com/hypixel/translation/e2e/BaseE2ETest.java",
    "$workspace/src/test/java/com/hypixel/translation/e2e/Tier1FeatureCoverageTest.java",
    "$workspace/src/test/java/com/hypixel/translation/e2e/Tier2BoundaryCornerTest.java",
    "$workspace/src/test/java/com/hypixel/translation/e2e/Tier3PairwiseCombinationsTest.java",
    "$workspace/src/test/java/com/hypixel/translation/e2e/Tier4RealWorldWorkloadTest.java",
    "$workspace/src/test/java/com/hypixel/translation/e2e/Tier5AdversarialHardeningTest.java",
    "$workspace/src/test/java/com/hypixel/translation/e2e/Tier5InterceptionWebAdversarialTest.java",
    "$workspace/src/test/java/com/hypixel/translation/e2e/DeploymentValidationE2ETest.java",
    "$workspace/src/test/java/com/hypixel/translation/e2e/R1ComponentStyleE2ETest.java",
    "$workspace/src/test/java/com/hypixel/translation/e2e/R2DictionaryQualityE2ETest.java",
    "$workspace/src/test/java/com/hypixel/translation/e2e/R3NoLocalModelE2ETest.java",
    "$workspace/src/test/java/com/hypixel/translation/e2e/R4SyncAndDeployE2ETest.java",
    "$workspace/src/test/java/com/hypixel/translation/core/TokenDictionaryTest.java",
    "$workspace/src/test/java/com/hypixel/translation/core/CsvSyncTest.java",
    "$workspace/src/test/java/com/hypixel/translation/core/UntranslatedCaptureQueueTest.java",
    "$workspace/src/test/java/com/hypixel/translation/pipeline/SchemaNormalizerTest.java",
    "$workspace/src/test/java/com/hypixel/translation/engine/DynamicItemCombinerTest.java",
    "$workspace/src/test/java/com/hypixel/translation/engine/LoreParserTest.java",
    "$workspace/src/test/java/com/hypixel/translation/engine/TextTraverserTest.java",
    "$workspace/src/test/java/com/hypixel/translation/config/ModConfigTest.java",
    "$workspace/src/test/java/com/hypixel/translation/interception/InterceptionTranslatorsTest.java",
    "$workspace/src/test/java/com/hypixel/translation/interception/MenuLocalizationTest.java",
    "$workspace/src/test/java/com/hypixel/translation/command/ClientCommandsHandlerTest.java",
    "$workspace/src/test/java/com/hypixel/translation/config/ModMenuIntegrationTest.java"
)

$filesFormatted = ($files | ForEach-Object { $_.Replace("\", "/") }) -join "`n"
$javacArgs = "-cp`n$cp;$outDir`n-d`n$outDir`n$filesFormatted"
Set-Content -Path "$workspace/build/javac_all_suite_args.txt" -Value $javacArgs

Write-Host "Compiling 4-Tier E2E & Component Test Suite..."
& "C:\Program Files\Java\jdk-25.0.2\bin\javac.exe" "@$workspace/build/javac_all_suite_args.txt"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Compilation failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

Write-Host "Running 5-Tier E2E & Component Test Suite..."
& "C:\Program Files\Java\jdk-25.0.2\bin\java.exe" -cp "$cp;$outDir" org.junit.platform.console.ConsoleLauncher `
    --select-class com.hypixel.translation.e2e.Tier1FeatureCoverageTest `
    --select-class com.hypixel.translation.e2e.Tier2BoundaryCornerTest `
    --select-class com.hypixel.translation.e2e.Tier3PairwiseCombinationsTest `
    --select-class com.hypixel.translation.e2e.Tier4RealWorldWorkloadTest `
    --select-class com.hypixel.translation.e2e.Tier5AdversarialHardeningTest `
    --select-class com.hypixel.translation.e2e.Tier5InterceptionWebAdversarialTest `
    --select-class com.hypixel.translation.e2e.DeploymentValidationE2ETest `
    --select-class com.hypixel.translation.e2e.R1ComponentStyleE2ETest `
    --select-class com.hypixel.translation.e2e.R2DictionaryQualityE2ETest `
    --select-class com.hypixel.translation.e2e.R3NoLocalModelE2ETest `
    --select-class com.hypixel.translation.e2e.R4SyncAndDeployE2ETest `
    --select-class com.hypixel.translation.core.TokenDictionaryTest `
    --select-class com.hypixel.translation.core.CsvSyncTest `
    --select-class com.hypixel.translation.core.UntranslatedCaptureQueueTest `
    --select-class com.hypixel.translation.pipeline.SchemaNormalizerTest `
    --select-class com.hypixel.translation.engine.DynamicItemCombinerTest `
    --select-class com.hypixel.translation.engine.LoreParserTest `
    --select-class com.hypixel.translation.engine.TextTraverserTest `
    --select-class com.hypixel.translation.config.ModConfigTest `
    --select-class com.hypixel.translation.interception.InterceptionTranslatorsTest `
    --select-class com.hypixel.translation.interception.MenuLocalizationTest `
    --select-class com.hypixel.translation.command.ClientCommandsHandlerTest `
    --select-class com.hypixel.translation.config.ModMenuIntegrationTest
exit $LASTEXITCODE
