$ScriptRoot = $PSScriptRoot
$ProjectRoot = Split-Path -Parent $ScriptRoot

function Get-ProjectJavaTool {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ToolName
    )

    $toolExe = "$ToolName.exe"
    foreach ($jdkFolder in @("jdk-21", "jdk-17", "jdk")) {
        $projectJdkTool = Join-Path $ProjectRoot "$jdkFolder\bin\$toolExe"
        if (Test-Path -LiteralPath $projectJdkTool) {
            return $projectJdkTool
        }
    }

    if ($env:JAVA_HOME) {
        $javaHomeTool = Join-Path $env:JAVA_HOME "bin\$toolExe"
        if (Test-Path -LiteralPath $javaHomeTool) {
            return $javaHomeTool
        }
    }

    $pathTool = Get-Command $ToolName -ErrorAction SilentlyContinue
    if ($pathTool) {
        return $pathTool.Source
    }

    throw "Cannot find $toolExe. Install JDK 17/21, set JAVA_HOME, or put jdk-17, jdk-21, or jdk in the project root."
}

function Get-JavaMajorVersion {
    param(
        [Parameter(Mandatory = $true)]
        [string]$JavaTool
    )

    $oldErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $escapedJavaTool = $JavaTool.Replace('"', '""')
        $versionOutput = cmd /c "`"$escapedJavaTool`" -version 2>&1" | Out-String
    } finally {
        $ErrorActionPreference = $oldErrorActionPreference
    }

    if ($versionOutput -match 'version "([0-9]+)(\.([0-9]+))?') {
        if ($matches[1] -eq "1" -and $matches[3]) {
            return [int]$matches[3]
        }
        return [int]$matches[1]
    }
    if ($versionOutput -match 'javac ([0-9]+)(\.([0-9]+))?') {
        if ($matches[1] -eq "1" -and $matches[3]) {
            return [int]$matches[3]
        }
        return [int]$matches[1]
    }

    throw "Cannot read Java version from: $JavaTool"
}

function Assert-Java17OrNewer {
    param(
        [Parameter(Mandatory = $true)]
        [string]$JavaTool,

        [Parameter(Mandatory = $false)]
        [string]$ToolLabel = "java"
    )

    $major = Get-JavaMajorVersion $JavaTool
    if ($major -lt 17) {
        throw "$ToolLabel is Java $major. This game needs Java 17 or newer. Install JDK 17/21, set JAVA_HOME, or put jdk-17, jdk-21, or jdk in the project root."
    }
}
