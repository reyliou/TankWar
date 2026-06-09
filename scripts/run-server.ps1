$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
. (Join-Path $PSScriptRoot "java-tools.ps1")
$java = Get-ProjectJavaTool "java"
Assert-Java17OrNewer $java "java"
$classes = Join-Path $root "target\classes"
$port = if ($env:TANK_PORT) { $env:TANK_PORT } else { "7788" }
$serverClass = Join-Path $classes "com\tankgame\server\TankServer.class"

if ($env:TANK_REBUILD -eq "1" -or -not (Test-Path -LiteralPath $serverClass)) {
    Write-Host "Building project..."
    & (Join-Path $PSScriptRoot "build.ps1")
} else {
    Write-Host "Using compiled classes. Set TANK_REBUILD=1 to rebuild."
}

Write-Host "Starting server on port $port..."
$libDir = Join-Path $root "lib"
$cpArray = @($classes)
Get-ChildItem -Path $libDir -Filter "*.jar" | ForEach-Object { $cpArray += $_.FullName }
$cp = $cpArray -join ";"
& $java -cp $cp com.tankgame.server.TankServer $port
