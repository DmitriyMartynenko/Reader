# Publishes a new app version to GitHub Releases, where the app looks for updates.
#   .\tools\release.ps1 -Version 1.2.0 -Notes "• Що змінилося"
# Needs: GitHub CLI logged in (gh auth login), keystore\release.jks, JDK 17.
param(
    [Parameter(Mandatory)] [string]$Version,
    [Parameter(Mandatory)] [string]$Notes
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw "Версія має бути у форматі 1.2.3" }
if (-not (Test-Path "$root\keystore\release.jks")) { throw "Немає keystore\release.jks — без нього оновлення не встановиться поверх старої версії" }

# Bump the version the build reads.
$props = "$root\version.properties"
$lines = [IO.File]::ReadAllLines($props) -replace '^version=.*', "version=$Version"
[IO.File]::WriteAllLines($props, $lines, (New-Object Text.UTF8Encoding $false))

# Gradle breaks on the Cyrillic project path, so build through a temporary drive letter.
$drive = "R:"
subst $drive $root
try {
    Push-Location "$drive\"
    if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot" }
    node tools\build-data.js
    if ($LASTEXITCODE) { throw "build-data.js failed" }
    .\gradlew.bat testDebugUnitTest assembleRelease --console=plain -q
    if ($LASTEXITCODE) { throw "Gradle build failed" }
} finally {
    Pop-Location
    subst $drive /D
}

$dist = "$root\dist"
New-Item -ItemType Directory -Force $dist | Out-Null
$apk = "$dist\Othello-Reader.apk"
Copy-Item "$root\app\build\outputs\apk\release\app-release.apk" $apk -Force

Push-Location $root
git add -A
git commit -m "Release v$Version" -m $Notes
git tag "v$Version"
git push origin HEAD --tags
gh release create "v$Version" $apk --title "Отелло $Version" --notes $Notes
Pop-Location
Write-Host "Опубліковано v$Version. Застосунки підхоплять оновлення протягом доби або одразу через Налаштування -> Перевірити."
