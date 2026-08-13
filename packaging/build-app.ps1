# Builds "University Driver" as a Windows app-image into %LOCALAPPDATA%\Programs, and
# refreshes a Desktop shortcut to it so it can still be launched with a double-click.
#
# Rebuild after any code change with:
#   .\packaging\build-app.ps1
#
# Requires JDK 21 (the same version university-core's Maven toolchain pins) - jpackage
# ships with the JDK itself, no extra install needed.

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Join-Path (Split-Path -Parent $ScriptDir) "university-core"
$AppName = "University Driver"
# %LOCALAPPDATA%\Programs, not Program Files: matches how per-user apps (e.g. VS Code)
# install without needing Administrator rights.
$DestDir = "$env:LOCALAPPDATA\Programs"

# Resolve a JDK 21 JAVA_HOME: prefer whatever JAVA_HOME is already set (it must be a
# JDK 21, since the `mvn package` below pins the same version via the Maven toolchain),
# else fall back to searching common per-vendor install locations.
function Resolve-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\jpackage.exe")) {
        return $env:JAVA_HOME
    }
    $searchRoots = @(
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Amazon Corretto",
        "C:\Program Files\Microsoft",
        "C:\Program Files\Java"
    )
    foreach ($root in $searchRoots) {
        if (-not (Test-Path $root)) { continue }
        $candidate = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match "21" } |
            Where-Object { Test-Path (Join-Path $_.FullName "bin\jpackage.exe") } |
            Select-Object -First 1
        if ($candidate) { return $candidate.FullName }
    }
    throw "Could not find a JDK 21 install (jpackage.exe). Set JAVA_HOME to one (e.g. Eclipse Temurin 21) and retry."
}

$JavaHome = Resolve-JavaHome

Write-Host "==> Building the shaded jar (mvn package)"
Push-Location $ProjectDir
try {
    & mvn -q clean package
    if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }
} finally {
    Pop-Location
}

$Jar = Join-Path $ProjectDir "target\university-core-1.0-SNAPSHOT.jar"
$WorkDir = Join-Path $env:TEMP ([System.Guid]::NewGuid())
$Stage = Join-Path $WorkDir "input"
$BuildDir = Join-Path $WorkDir "dist"

New-Item -ItemType Directory -Path $Stage -Force | Out-Null
Copy-Item $Jar $Stage

try {
    Write-Host "==> Running jpackage"
    & "$JavaHome\bin\jpackage.exe" `
        --type app-image `
        --input $Stage `
        --dest $BuildDir `
        --name $AppName `
        --main-jar (Split-Path -Leaf $Jar) `
        --main-class de.lino.thma.Launcher `
        --icon "$ScriptDir\AppIcon.ico" `
        --app-version 1.0 `
        --vendor "Lino Alessio Kauschinger"
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

    Write-Host "==> Installing the app to $DestDir"
    $DestApp = Join-Path $DestDir $AppName
    if (Test-Path $DestApp) { Remove-Item $DestApp -Recurse -Force }
    New-Item -ItemType Directory -Path $DestDir -Force | Out-Null
    Copy-Item (Join-Path $BuildDir $AppName) $DestApp -Recurse

    Write-Host "==> Refreshing the Desktop shortcut"
    $Shell = New-Object -ComObject WScript.Shell
    $ShortcutPath = Join-Path ([Environment]::GetFolderPath("Desktop")) "$AppName.lnk"
    $Shortcut = $Shell.CreateShortcut($ShortcutPath)
    $Shortcut.TargetPath = Join-Path $DestApp "$AppName.exe"
    $Shortcut.WorkingDirectory = $DestApp
    $Shortcut.IconLocation = Join-Path $DestApp "$AppName.exe"
    $Shortcut.Save()

    Write-Host "==> Done: $DestApp (Desktop shortcut refreshed)"
} finally {
    Remove-Item $WorkDir -Recurse -Force -ErrorAction SilentlyContinue
}
