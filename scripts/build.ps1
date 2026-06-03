$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
. (Join-Path $PSScriptRoot "java-tools.ps1")
$javac = Get-ProjectJavaTool "javac"
Assert-Java17OrNewer $javac "javac"
$classes = Join-Path $root "target\classes"
$lib = Join-Path $root "lib"

$gson = Join-Path $lib "gson-2.11.0.jar"
$javafxBase = Join-Path $lib "javafx-base-21.0.2-win.jar"
$javafxControls = Join-Path $lib "javafx-controls-21.0.2-win.jar"
$javafxGraphics = Join-Path $lib "javafx-graphics-21.0.2-win.jar"

New-Item -ItemType Directory -Force -Path $classes | Out-Null

$sources = Get-ChildItem -Path (Join-Path $root "src\main\java") -Recurse -Filter "*.java" |
    ForEach-Object {
        $relative = Resolve-Path -LiteralPath $_.FullName -Relative
        $relative.TrimStart(".", "\").Replace("\", "/")
    }

$argsFile = Join-Path $root "target\javac.args"
$classesArg = "target/classes"
$gsonArg = "lib/gson-2.11.0.jar"
$modulePathArg = "lib/javafx-base-21.0.2-win.jar;lib/javafx-controls-21.0.2-win.jar;lib/javafx-graphics-21.0.2-win.jar"
$javacArgs = @(
    "-encoding"
    "UTF-8"
    "--release"
    "17"
    "-cp"
    "`"$gsonArg`""
    "--module-path"
    "`"$modulePathArg`""
    "--add-modules"
    "javafx.controls"
    "-d"
    "`"$classesArg`""
) + ($sources | ForEach-Object { "`"$_`"" })

Set-Content -Path $argsFile -Value $javacArgs -Encoding ASCII

& $javac "@$argsFile"

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Build complete: target\classes"
