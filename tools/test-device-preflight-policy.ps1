$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'DevicePreflightPolicy.psm1') -Force

function Assert-Equal([bool]$Expected, [bool]$Actual, [string]$Name) {
    if ($Expected -ne $Actual) { throw "$Name expected $Expected, got $Actual" }
}

$activity = 'ResumedActivity: ActivityRecord{abc u0 com.jaysato.xiaomihealthbridge/.WearMeasurePageActivity t1}'
$awake = "mWakefulness=Awake`n"
$dozing = "mWakefulness=Dozing`n"
$systemActivity = 'ResumedActivity: ActivityRecord{abc u0 com.xiaomi.wearos.sysui/.SysUiActivity t1}'

Assert-Equal $true (Get-WatchScanReadiness $awake $activity).ready 'awake measurement page'
Assert-Equal $false (Get-WatchScanReadiness $dozing $activity).ready 'dozing measurement page'
Assert-Equal $false (Get-WatchScanReadiness $awake $systemActivity).ready 'awake system page'
Assert-Equal $false (Get-WatchScanReadiness $awake '').ready 'missing foreground activity'
Write-Output 'Device preflight policy tests passed (4 cases).'
