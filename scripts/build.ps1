$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
. (Join-Path $PSScriptRoot "java-tools.ps1")
$javac = Get-ProjectJavaTool "javac"
Assert-Java17OrNewer $javac "javac"
$classes = Join-Path $root "target\classes"
$lib = Join-Path $root "lib"

$cpArray = @()
Get-ChildItem -Path $lib -Filter "*.jar" | ForEach-Object { $cpArray += "lib/$($_.Name)" }
$cp = $cpArray -join ";"

New-Item -ItemType Directory -Force -Path $classes | Out-Null

$sources = Get-ChildItem -Path (Join-Path $root "src\main\java") -Recurse -Filter "*.java" |
    ForEach-Object {
        $relative = Resolve-Path -LiteralPath $_.FullName -Relative
        $relative.TrimStart(".", "\").Replace("\", "/")
    }

$argsFile = Join-Path $root "target\javac.args"
$classesArg = "target/classes"
$modulePathArg = "lib/javafx-base-21.0.2-win.jar;lib/javafx-controls-21.0.2-win.jar;lib/javafx-graphics-21.0.2-win.jar;lib/javafx-media-21.0.2-win.jar"
$javacArgs = @(
    "-encoding"
    "UTF-8"
    "--release"
    "17"
    "-cp"
    $cp
    "--module-path"
    $modulePathArg
    "--add-modules"
    "javafx.controls,javafx.media"
    "-d"
    $classesArg
) + ($sources | ForEach-Object { $_ })

Set-Content -Path $argsFile -Value $javacArgs -Encoding ASCII

& $javac "@$argsFile"

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Build complete: target\classes"
