[CmdletBinding()]
param(
    [ValidateSet("msi", "exe", "app-image")]
    [string]$Type = "msi",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$wrapper = Join-Path $projectRoot "mvnw.cmd"
$inputDirectory = Join-Path $projectRoot "target\jpackage-input"
$outputDirectory = Join-Path $projectRoot "target\installer"
$expectedOutputDirectory = [IO.Path]::GetFullPath($outputDirectory)

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw "jpackage was not found. Install a JDK (not a JRE) that includes jpackage, then restart PowerShell."
}

if (-not $SkipBuild) {
    if (-not (Test-Path $wrapper)) {
        throw "Maven Wrapper was not found at $wrapper."
    }
    & $wrapper "-DskipTests" "package"
    if ($LASTEXITCODE -ne 0) {
        throw "The Maven build failed. Fix the build errors before packaging the installer."
    }
}

$mainJar = Join-Path $inputDirectory "i-need-air.jar"
if (-not (Test-Path $mainJar)) {
    throw "The packaged application was not found at $mainJar. Run without -SkipBuild first."
}

[xml]$pom = Get-Content (Join-Path $projectRoot "pom.xml")
$version = $pom.project.version -replace "-.*$", ""
if ($version -notmatch "^\d+(\.\d+){0,3}$") {
    throw "jpackage requires a numeric Windows installer version; found '$version'."
}

if ($Type -eq "msi" -and -not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
    throw "MSI creation requires WiX Toolset 3.x (candle.exe and light.exe). Install it, reopen PowerShell, then run this command again. Use -Type app-image to test the native app without WiX."
}

if (Test-Path $outputDirectory) {
    $resolvedOutputDirectory = (Resolve-Path -LiteralPath $outputDirectory).Path
    if (-not [string]::Equals($resolvedOutputDirectory, $expectedOutputDirectory, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clear an unexpected packaging directory: $resolvedOutputDirectory"
    }
    Remove-Item -LiteralPath $resolvedOutputDirectory -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$arguments = @(
    "--type", $Type,
    "--dest", $outputDirectory,
    "--name", "I Need Air",
    "--app-version", $version,
    "--vendor", "I Need Air",
    "--description", "Air-quality and weather destination planner.",
    "--input", $inputDirectory,
    "--main-jar", "i-need-air.jar",
    "--main-class", "com.ineedair.INeedAirApplication",
    "--java-options", "-Dfile.encoding=UTF-8"
)

if ($Type -ne "app-image") {
    $arguments += @(
        "--win-menu",
        "--win-shortcut",
        "--win-per-user-install",
        "--win-dir-chooser"
    )
}

Write-Host "Creating I Need Air $Type package..."
& jpackage @arguments
if ($LASTEXITCODE -ne 0) {
    throw "jpackage could not create the $Type package."
}

Write-Host "Done. Your release is in $outputDirectory"
