$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
. (Join-Path $PSScriptRoot "java-tools.ps1")
$java = Get-ProjectJavaTool "java"
Assert-Java17OrNewer $java "java"
$classes = Join-Path $root "target\classes"
$lib = Join-Path $root "lib"

$gson = Join-Path $lib "gson-2.11.0.jar"
$javafxBase = Join-Path $lib "javafx-base-21.0.2-win.jar"
$javafxControls = Join-Path $lib "javafx-controls-21.0.2-win.jar"
$javafxGraphics = Join-Path $lib "javafx-graphics-21.0.2-win.jar"
$javafxMedia = Join-Path $lib "javafx-media-21.0.2-win.jar"
$port = if ($env:TANK_PORT) { $env:TANK_PORT } else { "7788" }
$serverHost = if ($env:TANK_HOST) { $env:TANK_HOST } else { "127.0.0.1" }
$clientClass = Join-Path $classes "com\tankgame\client\TankClientApp.class"

if ($env:TANK_REBUILD -eq "1" -or -not (Test-Path -LiteralPath $clientClass)) {
    Write-Host "Building project..."
    & (Join-Path $PSScriptRoot "build.ps1")
} else {
    Write-Host "Using compiled classes. Set TANK_REBUILD=1 to rebuild."
}

Write-Host "Starting client..."
& $java `
    "-Dtank.host=$serverHost" `
    "-Dtank.port=$port" `
    -cp "$classes;$gson" `
    --module-path "$javafxBase;$javafxControls;$javafxGraphics;$javafxMedia" `
    --add-modules javafx.controls,javafx.media `
    com.tankgame.client.TankClientApp
