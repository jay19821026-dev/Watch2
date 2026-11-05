param(
    [Parameter(Mandatory = $true)][string]$PhoneSerial,
    [Parameter(Mandatory = $true)][string]$WatchSerial,
    [ValidateRange(1, 100)][int]$MinimumWatchBattery = 20,
    [string]$OutputPath = "build/acceptance/device-preflight.json",
    [string]$AdbPath
)

$ErrorActionPreference = 'Stop'
$policyModule = Join-Path $PSScriptRoot 'tools/DevicePreflightPolicy.psm1'
Import-Module $policyModule -Force
$packageName = 'com.jaysato.xiaomihealthbridge'
$adbCommand = if ($AdbPath) { $AdbPath } elseif (Get-Command adb -ErrorAction SilentlyContinue) {
    (Get-Command adb).Source
} else {
    Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
}
if (-not (Test-Path -LiteralPath $adbCommand)) { throw "ADB executable not found: $adbCommand" }

function Invoke-AdbText([string]$serial, [string[]]$arguments) {
    $text = & $adbCommand -s $serial @arguments 2>$null
    [pscustomobject]@{ ExitCode = $LASTEXITCODE; Text = [string]::Join("`n", [string[]]@($text)) }
}

function Get-DeviceEvidence([string]$serial) {
    $state = (& $adbCommand devices | Select-String "^$([regex]::Escape($serial))\s+" | ForEach-Object { ($_ -split '\s+')[1] } | Select-Object -First 1)
    if ($state -ne 'device') { return [pscustomobject]@{ serial = $serial; state = ($state ?? 'missing') } }
    $battery = Invoke-AdbText $serial @('shell', 'dumpsys', 'battery')
    $package = Invoke-AdbText $serial @('shell', 'dumpsys', 'package', $packageName)
    $level = [regex]::Match($battery.Text, '(?m)^\s*level:\s*(\d+)').Groups[1].Value
    $powered = [regex]::IsMatch($battery.Text, '(?m)^\s*(AC|USB|Wireless) powered:\s*true')
    $version = [regex]::Match($package.Text, 'versionName=([^\r\n]+)').Groups[1].Value.Trim()
    $screen = Invoke-AdbText $serial @('shell', 'dumpsys', 'power')
    $activities = Invoke-AdbText $serial @('shell', 'dumpsys', 'activity', 'activities')
    $readiness = Get-WatchScanReadiness -PowerDump $screen.Text -ActivityDump $activities.Text
    [pscustomobject]@{
        serial = $serial
        state = 'device'
        battery_percent = if ($level) { [int]$level } else { $null }
        charging = $powered
        app_version = $version
        screen_awake = $readiness.screen_awake
        measurement_page_foreground = $readiness.measurement_page_foreground
    }
}

$phone = Get-DeviceEvidence $PhoneSerial
$watch = Get-DeviceEvidence $WatchSerial
$aligned = $phone.app_version -and $watch.app_version -and $phone.app_version -eq $watch.app_version
$ready = $phone.state -eq 'device' -and $watch.state -eq 'device' -and
    -not $watch.charging -and $watch.battery_percent -ge $MinimumWatchBattery -and $aligned -and
    $watch.screen_awake -and $watch.measurement_page_foreground
$result = [ordered]@{
    observed_at_utc = [DateTime]::UtcNow.ToString('o')
    package = $packageName
    scan_safe = $ready
    blocking_reasons = @(
        if ($phone.state -ne 'device') { 'phone_transport_unavailable' }
        if ($watch.state -ne 'device') { 'watch_transport_unavailable' }
        if ($watch.state -eq 'device' -and $watch.charging) { 'watch_charging' }
        if ($watch.state -eq 'device' -and $null -ne $watch.battery_percent -and $watch.battery_percent -lt $MinimumWatchBattery) { 'watch_battery_below_threshold' }
        if ($phone.state -eq 'device' -and $watch.state -eq 'device' -and -not $aligned) { 'app_versions_unaligned_or_missing' }
        if ($watch.state -eq 'device' -and -not $watch.screen_awake) { 'watch_screen_not_awake' }
        if ($watch.state -eq 'device' -and -not $watch.measurement_page_foreground) { 'watch_measurement_page_not_foreground' }
    )
    phone = $phone
    watch = $watch
}
$directory = Split-Path -Parent $OutputPath
if ($directory) { New-Item -ItemType Directory -Force -Path $directory | Out-Null }
$result | ConvertTo-Json -Depth 4 | Set-Content -Path $OutputPath -Encoding utf8
$result | ConvertTo-Json -Depth 4
if (-not $ready) { exit 2 }
