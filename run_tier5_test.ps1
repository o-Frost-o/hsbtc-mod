$ErrorActionPreference = "Stop"
$workspace = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $workspace
$outDir = "$workspace/build/test-classes"
$mainClasses = "$workspace/build/classes/java/main"
$cp = (Get-Content "$workspace/build/classpath.txt" -Raw).Replace("\", "/") + ";$mainClasses"
$testFile = "$workspace/src/test/java/com/hypixel/translation/e2e/Tier5AdversarialHardeningTest.java"

Write-Host "Compiling Tier5AdversarialHardeningTest.java..."
& "C:\Program Files\Java\jdk-25.0.2\bin\javac.exe" -cp "$cp;$outDir" -d $outDir $testFile
if ($LASTEXITCODE -ne 0) {
    Write-Error "Compilation failed with code $LASTEXITCODE"
    exit $LASTEXITCODE
}

Write-Host "Running Tier5AdversarialHardeningTest..."
& "C:\Program Files\Java\jdk-25.0.2\bin\java.exe" -cp "$cp;$outDir" org.junit.platform.console.ConsoleLauncher `
    --select-class com.hypixel.translation.e2e.Tier5AdversarialHardeningTest
exit $LASTEXITCODE
