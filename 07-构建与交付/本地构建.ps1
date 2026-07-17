$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$toolchainRoot = Join-Path (Split-Path -Parent $repoRoot) ".toolchains"
$sdkRoot = Join-Path $toolchainRoot "android-sdk"
$jdkRoot = Get-ChildItem -LiteralPath (Join-Path $toolchainRoot "jdk17") -Directory |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName "bin\java.exe") } |
    Select-Object -First 1

if ($null -eq $jdkRoot) {
    throw "Project JDK 17 was not found: $toolchainRoot\jdk17"
}

if (-not (Test-Path -LiteralPath $sdkRoot)) {
    throw "Project Android SDK was not found: $sdkRoot"
}

# Gradle 9.4.1 can corrupt non-ASCII project paths in the forked test
# process argument file on Windows. A temporary ASCII junction keeps the
# repository in place while giving the test process an ASCII classpath.
$linkRoot = Join-Path $env:TEMP "offline-landlord-build"
if (Test-Path -LiteralPath $linkRoot) {
    $linkItem = Get-Item -LiteralPath $linkRoot
    if ($linkItem.LinkType -ne "Junction" -or [string]$linkItem.Target -ne $repoRoot) {
        throw "The temporary build path is already used by another file: $linkRoot"
    }
} else {
    New-Item -ItemType Junction -Path $linkRoot -Target $repoRoot | Out-Null
}

$env:JAVA_HOME = $jdkRoot.FullName
$env:ANDROID_HOME = $sdkRoot

& (Join-Path $linkRoot "gradlew.bat") -p $linkRoot :android-app:testDebugUnitTest
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& (Join-Path $linkRoot "gradlew.bat") -p $linkRoot :android-app:assembleDebug
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$sourceApk = Join-Path $repoRoot "android-app\build\outputs\apk\debug\android-app-debug.apk"
$existingDeliveryApk = Get-ChildItem -LiteralPath $PSScriptRoot -Filter "*-v0.2.0-bot-debug.apk" |
    Select-Object -First 1
if ($null -ne $existingDeliveryApk) {
    $deliveryApk = $existingDeliveryApk.FullName
} else {
    $deliveryApk = Join-Path $PSScriptRoot "offline-landlord-v0.2.0-bot-debug.apk"
}
Copy-Item -LiteralPath $sourceApk -Destination $deliveryApk -Force

Write-Host "Tests and APK build completed successfully."
Write-Host "APK: $deliveryApk"
Write-Host "SHA-256: $((Get-FileHash -LiteralPath $deliveryApk -Algorithm SHA256).Hash)"
