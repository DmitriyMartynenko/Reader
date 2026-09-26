# Publishes a new app version to GitHub Releases, where the app looks for updates.
#   .\tools\release.ps1 -Version 1.2.0 -Notes "• Що змінилося"
# Needs: GitHub CLI logged in (gh auth login), keystore\release.jks, JDK 17.
param(
    [Parameter(Mandatory)] [string]$Version,
    [Parameter(Mandatory)] [string]$Notes
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

# Native tools write progress to stderr, which "Stop" would treat as a failure; judge them by exit code.
function Run([scriptblock]$Command) {
    $ErrorActionPreference = "Continue"
    & $Command 2>&1 | ForEach-Object { "$_" } | Out-Host
    if ($LASTEXITCODE) { throw "Команда завершилася з кодом ${LASTEXITCODE}: $Command" }
}
if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw "Версія має бути у форматі 1.2.3" }
if (-not (Test-Path "$root\keystore\release.jks")) { throw "Немає keystore\release.jks — без нього оновлення не встановиться поверх старої версії" }

# Bump the version the build reads.
$props = "$root\version.properties"
$lines = [IO.File]::ReadAllLines($props) -replace '^version=.*', "version=$Version"
[IO.File]::WriteAllLines($props, $lines, (New-Object Text.UTF8Encoding $false))

# Gradle breaks on the Cyrillic project path, so build through a drive letter. Always the same
# letter: Kotlin's incremental compilation gets confused when the project root changes.
$drive = "O:"
$mapped = (subst) -match [regex]::Escape("${drive}\: => $root")
if (-not $mapped) { subst $drive $root }
try {
    Push-Location "$drive\"
    if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot" }
    # Every book in books/ is packed again, which also refreshes the library index.
    foreach ($book in Get-ChildItem books -Directory) { Run { node tools\build-book.js $book.Name } }
    Run { .\gradlew.bat testDebugUnitTest assembleRelease --console=plain -q }
} finally {
    Pop-Location
    if (-not $mapped) { subst $drive /D }
}

$dist = "$root\dist"
New-Item -ItemType Directory -Force $dist | Out-Null
$apk = "$dist\Othello-Reader.apk"
Copy-Item "$root\app\build\outputs\apk\release\app-release.apk" $apk -Force

Push-Location $root
Run { git add -A }
if (git status --porcelain) { Run { git commit -m "Release v$Version" -m $Notes } }
Run { git tag "v$Version" }
Run { git push origin HEAD --tags }
Run { gh release create "v$Version" $apk --title "Бібліотека $Version" --notes $Notes }
Pop-Location
Write-Host "Опубліковано v$Version. Застосунки підхоплять оновлення протягом доби або одразу через Налаштування -> Перевірити."
