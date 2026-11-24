param(
    [string]$InstanceModsDir = "C:\Users\User\AppData\Roaming\PrismLauncher\instances\26.2\minecraft\mods"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -Path $InstanceModsDir) -and (Test-Path -Path "D:\PrismLauncher\instances\26.2\minecraft\mods")) {
    $InstanceModsDir = "D:\PrismLauncher\instances\26.2\minecraft\mods"
}

# Ensure JDK 25 is used regardless of system environment variables
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.2"

$ProjectRoot = $PSScriptRoot
Set-Location -Path $ProjectRoot

Write-Host "[1/4] Pre-building JAR, cleaning stale target JARs, and syncing dictionaries..." -ForegroundColor Cyan
& "$ProjectRoot\gradlew.bat" jar
if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle jar task failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}
if (Test-Path -Path $InstanceModsDir) {
    Get-ChildItem -Path $InstanceModsDir -Filter "hypixel_skyblock_translation*.jar" | Remove-Item -Force
} else {
    New-Item -ItemType Directory -Force -Path $InstanceModsDir | Out-Null
}
$PreJarFile = Get-ChildItem -Path "$ProjectRoot\build\libs" -Filter "hypixel_skyblock_translation*.jar" |
    Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-dev.jar" } |
    Select-Object -First 1
if ($PreJarFile) {
    Copy-Item -Path $PreJarFile.FullName -Destination $InstanceModsDir -Force
}

$MinecraftDir = Split-Path -Parent $InstanceModsDir
$TargetConfigDir = Join-Path $MinecraftDir "config\hypixel_skyblock_translation"
if (-not (Test-Path -Path $TargetConfigDir)) {
    New-Item -ItemType Directory -Force -Path $TargetConfigDir | Out-Null
}

$TargetConfigFile = Join-Path $TargetConfigDir "config.json"
if (-not (Test-Path -Path $TargetConfigFile)) {
    $DefaultConfig = @"
{
  "enableAltShiftPreview": true,
  "showEnglishSubtitle": false,
  "untranslatedCapture": true,
  "categories": {
    "TOOLTIPS": "DISPLAY",
    "CONTAINER_UI": "DISPLAY",
    "SCOREBOARD": "DISPLAY",
    "TAB_LIST": "DISPLAY",
    "CHAT": "DISPLAY",
    "ACTION_BAR": "DISPLAY",
    "BOSS_BAR": "DISPLAY",
    "TITLE_SUBTITLE": "DISPLAY",
    "ENTITY_TAGS": "DISPLAY"
  }
}
"@
    $Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($TargetConfigFile, $DefaultConfig, $Utf8NoBom)
    Write-Host "Initialized default config.json at $TargetConfigFile" -ForegroundColor Green
}

$DictSourceDir = Join-Path $ProjectRoot "src\main\resources\assets\hsbtr\dictionaries"
$ProjectConfigDir = Join-Path $ProjectRoot "config\hypixel_skyblock_translation"
if (-not (Test-Path -Path $ProjectConfigDir)) {
    New-Item -ItemType Directory -Force -Path $ProjectConfigDir | Out-Null
}
foreach ($DictFileName in @("tokens.json", "items.json", "dialogues.json", "lore_lines.json", "menus.json")) {
    $SrcDict = Join-Path $DictSourceDir $DictFileName
    $ProjDstDict = Join-Path $ProjectConfigDir $DictFileName
    $DstDict = Join-Path $TargetConfigDir $DictFileName
    if (Test-Path -Path $SrcDict) {
        Copy-Item -Path $SrcDict -Destination $ProjDstDict -Force
        Copy-Item -Path $SrcDict -Destination $DstDict -Force
        Write-Host "Synchronized $DictFileName to $ProjDstDict and $DstDict" -ForegroundColor Green
    }
}

Write-Host "[2/4] Building and verifying mod JAR with Gradle..." -ForegroundColor Cyan
& "$ProjectRoot\gradlew.bat" build
if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle build failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

Write-Host "[3/4] Deploying new JAR to PrismLauncher instance..." -ForegroundColor Cyan
$JarFile = Get-ChildItem -Path "$ProjectRoot\build\libs" -Filter "hypixel_skyblock_translation*.jar" |
    Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-dev.jar" } |
    Select-Object -First 1

if (-not $JarFile) {
    Write-Error "Could not find built JAR in $ProjectRoot\build\libs!"
    exit 1
}

Copy-Item -Path $JarFile.FullName -Destination $InstanceModsDir -Force
Write-Host "Successfully deployed $($JarFile.Name) to $InstanceModsDir!" -ForegroundColor Green
Write-Host "[4/4] Deployment and verification complete." -ForegroundColor Green
