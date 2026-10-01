# archive-build.ps1
# Change control for JogginApp debug builds.
#
# Copies the freshly-built debug APK into .\builds\ named by the app's versionName
# (read from app\build.gradle), then keeps only the newest 4 archived versions so
# we can revert to a recent build if needed.
#
# Usage (from the project root):
#   .\gradlew.bat assembleDebug
#   powershell -ExecutionPolicy Bypass -File .\archive-build.ps1
#
# Or just run archive-build.ps1 after any build.

$ErrorActionPreference = 'Stop'

$root      = Split-Path -Parent $MyInvocation.MyCommand.Path
$apk       = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
$buildsDir = Join-Path $root 'builds'
$gradle    = Join-Path $root 'app\build.gradle'
$keep      = 4   # how many versions to retain

if (-not (Test-Path $apk)) {
    Write-Error "No debug APK found at $apk. Build first with: .\gradlew.bat assembleDebug"
    exit 1
}

# Read versionName from app\build.gradle (e.g. versionName "1.26")
$versionName = (Select-String -Path $gradle -Pattern 'versionName\s+"([^"]+)"').Matches[0].Groups[1].Value
if ([string]::IsNullOrWhiteSpace($versionName)) {
    Write-Error "Could not read versionName from $gradle"
    exit 1
}

if (-not (Test-Path $buildsDir)) { New-Item -ItemType Directory -Path $buildsDir | Out-Null }

$dest = Join-Path $buildsDir "joggin-v$versionName.apk"
Copy-Item -Path $apk -Destination $dest -Force
Write-Host "Archived build -> $dest"

# Prune: keep only the newest $keep archived APKs (by last write time)
$archived = Get-ChildItem -Path $buildsDir -Filter 'joggin-v*.apk' | Sort-Object LastWriteTime -Descending
if ($archived.Count -gt $keep) {
    $archived | Select-Object -Skip $keep | ForEach-Object {
        Remove-Item $_.FullName -Force
        Write-Host "Pruned old build -> $($_.Name)"
    }
}

Write-Host ""
Write-Host "Current archived builds (newest first):"
Get-ChildItem -Path $buildsDir -Filter 'joggin-v*.apk' | Sort-Object LastWriteTime -Descending |
    ForEach-Object { Write-Host ("  {0}  ({1:yyyy-MM-dd HH:mm})" -f $_.Name, $_.LastWriteTime) }
