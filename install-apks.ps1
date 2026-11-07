param(
    [string]$WatchAddress,
    [ValidateSet("Debug", "Release")]
    [string]$Variant = "Debug",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$adb = Join-Path $projectRoot ".tooling\android-sdk\platform-tools\adb.exe"
$gradlew = Join-Path $projectRoot "gradlew.bat"
$buildToolsRoot = Join-Path $projectRoot ".tooling\android-sdk\build-tools"
$latestBuildTools = Get-ChildItem -LiteralPath $buildToolsRoot -Directory | Sort-Object Name -Descending | Select-Object -First 1
$aapt = if ($latestBuildTools) { Join-Path $latestBuildTools.FullName "aapt.exe" } else { $null }
$apksigner = if ($latestBuildTools) { Join-Path $latestBuildTools.FullName "apksigner.bat" } else { $null }
$appId = "net.ailisa.healthbridge"

foreach ($path in @($adb, $gradlew, $aapt, $apksigner)) {
    if (-not $path -or -not (Test-Path -LiteralPath $path)) {
        throw "找不到必要工具：$path"
    }
}

if (-not $SkipBuild) {
    Write-Host "建置手機與手錶 $Variant APK"
    & $gradlew ":mobile:assemble$Variant" ":wear:assemble$Variant"
    if ($LASTEXITCODE -ne 0) { throw "Gradle 建置失敗；未安裝任何 APK。" }
}

function Find-VariantApk([string]$module, [string]$variant) {
    $variantDirectory = Join-Path $projectRoot "$module\build\outputs\apk\$($variant.ToLowerInvariant())"
    if ($variant -eq "Debug") {
        $apk = Join-Path $variantDirectory "$($module)-debug.apk"
        if (Test-Path -LiteralPath $apk) { return $apk }
    } else {
        $apk = Get-ChildItem -LiteralPath $variantDirectory -Filter "$($module)-release*.apk" -File |
            Where-Object { $_.Name -notmatch "unsigned" } | Select-Object -First 1
        if ($apk) { return $apk.FullName }
        $unsigned = Get-ChildItem -LiteralPath $variantDirectory -Filter "$($module)-release-unsigned.apk" -File -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($unsigned) {
            throw "$module Release APK 尚未簽章，Android 無法安裝。請配置授權的 RELEASE_STORE_* 簽章資料；若是個人側載，使用預設 Debug 變體。"
        }
    }
    throw "找不到 $module $variant APK：$variantDirectory"
}

function Read-ApkIdentity([string]$apkPath, [string]$expectedLabel) {
    $badging = & $aapt dump badging $apkPath
    if ($LASTEXITCODE -ne 0) { throw "無法讀取 APK manifest：$apkPath" }
    $packageLine = ($badging | Select-String -Pattern "^package:").Line
    $labelLine = ($badging | Select-String -SimpleMatch "application-label:'$expectedLabel'").Line
    $versionNameMatch = [regex]::Match($packageLine, "versionName='([^']+)'")
    $versionCodeMatch = [regex]::Match($packageLine, "versionCode='([^']+)'")
    if ($packageLine -notmatch "name='$([regex]::Escape($appId))'" -or
        -not $versionNameMatch.Success -or -not $versionCodeMatch.Success -or -not $labelLine) {
        throw "APK 身分檢查失敗：$apkPath"
    }

    $signature = & $apksigner verify --print-certs $apkPath 2>&1
    if ($LASTEXITCODE -ne 0) { throw "APK 未通過簽章驗證：$apkPath；未安裝。" }

    return [pscustomobject]@{
        Path = $apkPath
        VersionName = $versionNameMatch.Groups[1].Value
        VersionCode = $versionCodeMatch.Groups[1].Value
    }
}

$mobile = Read-ApkIdentity (Find-VariantApk "mobile" $Variant) "AILISA Health Bridge"
$wear = Read-ApkIdentity (Find-VariantApk "wear" $Variant) "AILISA Health Bridge"
if ($mobile.VersionName -ne $wear.VersionName -or $mobile.VersionCode -ne $wear.VersionCode) {
    throw "手機與手錶版本不一致；手機 $($mobile.VersionName)/$($mobile.VersionCode)，手錶 $($wear.VersionName)/$($wear.VersionCode)。未安裝。"
}

if ($WatchAddress) {
    Write-Host "連接手錶 ADB：$WatchAddress"
    & $adb connect $WatchAddress
}

$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" } |
    ForEach-Object { ($_ -split "\s+")[0] }
if (-not $devices) {
    throw "沒有可用的 ADB 裝置。請開啟 USB／Wi-Fi 偵錯並在裝置上允許這部電腦。"
}

$phoneSerials = @()
$watchSerials = @()
foreach ($serial in $devices) {
    $characteristics = (& $adb -s $serial shell getprop ro.build.characteristics).Trim()
    if ($characteristics -match "watch") { $watchSerials += $serial } else { $phoneSerials += $serial }
}

foreach ($serial in $phoneSerials) {
    Write-Host "安裝手機 $($mobile.VersionName) ($Variant) 至 $serial"
    & $adb -s $serial install -r $mobile.Path
    if ($LASTEXITCODE -ne 0) { throw "手機安裝失敗：$serial；現有資料未由腳本刪除。" }
    $installedInfo = (& $adb -s $serial shell dumpsys package $appId) -join "`n"
    if ($installedInfo -notmatch "versionName=$([regex]::Escape($mobile.VersionName))(\s|$)" -or
        $installedInfo -notmatch "versionCode=$($mobile.VersionCode)(\s|$)") {
        throw "手機安裝後版本核對失敗：$serial"
    }
}
if ($phoneSerials.Count -eq 0) { Write-Warning "未找到手機；略過手機 APK。" }

foreach ($serial in $watchSerials) {
    Write-Host "安裝手錶 $($wear.VersionName) ($Variant) 至 $serial"
    & $adb -s $serial install -r $wear.Path
    if ($LASTEXITCODE -ne 0) { throw "手錶安裝失敗：$serial；現有資料未由腳本刪除。" }
    $installedInfo = (& $adb -s $serial shell dumpsys package $appId) -join "`n"
    if ($installedInfo -notmatch "versionName=$([regex]::Escape($wear.VersionName))(\s|$)" -or
        $installedInfo -notmatch "versionCode=$($wear.VersionCode)(\s|$)") {
        throw "手錶安裝後版本核對失敗：$serial"
    }
}
if ($watchSerials.Count -eq 0) { Write-Warning "未找到 Wear OS 手錶；略過手錶 APK。" }

Write-Host "安裝與版本核對完成：$($mobile.VersionName) ($Variant)"
