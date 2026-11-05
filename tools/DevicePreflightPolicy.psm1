function Get-WatchScanReadiness {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$PowerDump,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$ActivityDump
    )

    $screenAwake = $PowerDump -match '(?m)^\s*mWakefulness=Awake\s*$' -or
        $PowerDump -match '(?m)^\s*mInteractive=true\s*$'
    $measurementPageForeground = $ActivityDump -match '(?m)^\s*ResumedActivity:.*com\.jaysato\.xiaomihealthbridge/\.WearMeasurePageActivity\b' -or
        $ActivityDump -match '(?m)^\s*topResumedActivity=.*com\.jaysato\.xiaomihealthbridge/\.WearMeasurePageActivity\b'

    [pscustomobject]@{
        screen_awake = $screenAwake
        measurement_page_foreground = $measurementPageForeground
        ready = $screenAwake -and $measurementPageForeground
    }
}

Export-ModuleMember -Function Get-WatchScanReadiness
