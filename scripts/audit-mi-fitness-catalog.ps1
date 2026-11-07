param(
    [string]$Serial,
    [string]$LogFile,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($LogFile) {
    $lines = Get-Content -LiteralPath $LogFile
} else {
    if (-not (Test-Path -LiteralPath $Adb)) { throw "adb not found: $Adb" }
    $args = @()
    if ($Serial) { $args += @('-s', $Serial) }
    $args += @('logcat', '-d', '-v', 'brief')
    $lines = & $Adb @args
    if ($LASTEXITCODE -ne 0) { throw 'adb logcat failed' }
}

$json = ''
$collecting = $false
$catalog = $null
foreach ($line in $lines) {
    if ($line -notmatch '^\s*[A-Z]/EcoDeviceModel[^:]*:\s*(.*)$') { continue }
    $payload = $Matches[1]
    if ($payload -match 'mProducts are\s*(\[.*)$') {
        $json = $Matches[1]
        $collecting = $true
    } elseif ($collecting) {
        $json += $payload
    } else {
        continue
    }
    if ($json -notmatch '\]\s*$') { continue }
    try {
        $parsed = ConvertFrom-Json -InputObject $json -ErrorAction Stop
        if ($parsed -is [array] -and $parsed.Count -gt 0) { $catalog = $parsed }
        $collecting = $false
        $json = ''
    } catch {
        # The OEM log splits one JSON array across several consecutive lines.
    }
}

if (-not $catalog) { throw 'No complete EcoDeviceModel product catalog found in this log. Reopen Mi Fitness device management and retry.' }

$keys = @('ecg', 'electrocardiogram', 'electrocardiogram_global', 'temperature')
$rows = @($catalog | Where-Object { $_.pd_name -match 'Xiaomi Watch (2 Pro|5)$' })
if ($rows.Count -eq 0) { throw 'Catalog parsed, but no expected watch models were found.' }
Write-Output "Catalog products: $($catalog.Count)"
foreach ($row in $rows) {
    $features = @($row.feature)
    $result = [ordered]@{ Name = $row.pd_name; Model = $row.model; ProductId = $row.pd_id }
    foreach ($key in $keys) { $result[$key] = $features -contains $key }
    [pscustomobject]$result
}
