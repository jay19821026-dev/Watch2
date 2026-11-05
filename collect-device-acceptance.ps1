param(
    [Parameter(Mandatory = $true)]
    [string]$PhoneSerial,

    [string]$ExpectedSuiteId,
    [string]$PreviousSuiteId,

    [string]$OutputDirectory = "build/acceptance",

    [ValidateRange(1, 10000)]
    [int]$MaxPolls = 12,

    [ValidateRange(0, 3600)]
    [int]$PollSeconds = 5
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($ExpectedSuiteId) -and [string]::IsNullOrWhiteSpace($PreviousSuiteId)) {
    throw "Provide ExpectedSuiteId or the verified PreviousSuiteId before collecting a new suite."
}
$packageName = "com.jaysato.xiaomihealthbridge"
$preferenceNames = @("raw_sensor_results", "watch_feature_snapshot", "sync_status")
$runId = "{0}-{1}" -f (Get-Date -Format "yyyyMMdd-HHmmss-fff"), ([guid]::NewGuid().ToString("N").Substring(0, 8))
$baseDirectory = if ([IO.Path]::IsPathRooted($OutputDirectory)) {
    $OutputDirectory
} else {
    Join-Path $PSScriptRoot $OutputDirectory
}
$runDirectory = Join-Path $baseDirectory $runId
$rawDirectory = Join-Path $runDirectory "raw"
$summaryPath = Join-Path $runDirectory "poll-summary.jsonl"
[void][IO.Directory]::CreateDirectory($rawDirectory)

function Read-SharedPreferencesXml {
    param([Parameter(Mandatory = $true)][string]$Text)

    $settings = [System.Xml.XmlReaderSettings]::new()
    $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    $stringReader = [IO.StringReader]::new($Text)
    $xmlReader = [System.Xml.XmlReader]::Create($stringReader, $settings)
    try {
        $document = [System.Xml.XmlDocument]::new()
        $document.XmlResolver = $null
        $document.Load($xmlReader)
        if ($document.DocumentElement.LocalName -ne "map") {
            throw "Unexpected SharedPreferences XML root"
        }

        $values = @{}
        foreach ($node in $document.DocumentElement.ChildNodes) {
            if ($node.NodeType -ne [System.Xml.XmlNodeType]::Element) { continue }
            $name = $node.GetAttribute("name")
            if ([string]::IsNullOrWhiteSpace($name)) { continue }
            $value = if ($node.LocalName -eq "string") { $node.InnerText } else { $node.GetAttribute("value") }
            $values[$name] = [pscustomobject]@{ Type = $node.LocalName; Value = $value }
        }
        return $values
    } finally {
        $xmlReader.Dispose()
        $stringReader.Dispose()
    }
}

function Get-TerminalSuiteEntries {
    param([hashtable]$Values, [string]$PreferenceName)

    $entries = [System.Collections.Generic.List[object]]::new()
    foreach ($key in $Values.Keys) {
        if ($key -notmatch '^(?!metadata_)(.+)_suite_id$') { continue }
        $kind = $Matches[1]
        if ($kind -match '_(watch|phone)$') { continue }
        $suiteValue = $Values[$key]
        $finalKey = "${kind}_final"
        if ($suiteValue.Type -ne "string" -or [string]::IsNullOrWhiteSpace($suiteValue.Value)) { continue }
        if (-not $Values.ContainsKey($finalKey)) { continue }
        $finalValue = $Values[$finalKey]
        if ($finalValue.Type -ne "boolean" -or $finalValue.Value -ne "true") { continue }
        $entries.Add([pscustomobject]@{
            SuiteId = $suiteValue.Value
            Kind = $kind
            Preference = $PreferenceName
        })
    }
    return $entries
}

function Get-DeviceSnapshot {
    param([Parameter(Mandatory = $true)][string]$PreferenceName, [int]$PollNumber)

    $remotePath = "/data/user/0/$packageName/shared_prefs/$PreferenceName.xml"
    $adbArguments = @("-s", $PhoneSerial, "exec-out", "run-as", $packageName, "cat", $remotePath)
    try {
        $lines = & adb @adbArguments 2>$null
        $nativeExitCode = $LASTEXITCODE
    } catch {
        return [pscustomobject]@{ Name = $PreferenceName; Status = "transport_unavailable"; ExitCode = $null; RawPath = $null; Values = $null }
    }
    if ($nativeExitCode -ne 0) {
        return [pscustomobject]@{ Name = $PreferenceName; Status = "transport_unavailable"; ExitCode = $nativeExitCode; RawPath = $null; Values = $null }
    }

    $text = [string]::Join([Environment]::NewLine, [string[]]@($lines))
    if ([string]::IsNullOrWhiteSpace($text)) {
        return [pscustomobject]@{ Name = $PreferenceName; Status = "empty_snapshot"; ExitCode = $nativeExitCode; RawPath = $null; Values = $null }
    }

    $rawPath = Join-Path $rawDirectory ("{0}-poll-{1:D4}.xml" -f $PreferenceName, $PollNumber)
    [IO.File]::WriteAllText($rawPath, $text, [Text.UTF8Encoding]::new($false))
    try {
        $values = Read-SharedPreferencesXml -Text $text
    } catch {
        return [pscustomobject]@{ Name = $PreferenceName; Status = "invalid_snapshot"; ExitCode = $nativeExitCode; RawPath = $rawPath; Values = $null }
    }
    return [pscustomobject]@{ Name = $PreferenceName; Status = "ok"; ExitCode = $nativeExitCode; RawPath = $rawPath; Values = $values }
}

$latchedSuiteId = $ExpectedSuiteId
for ($poll = 1; $poll -le $MaxPolls; $poll++) {
    $snapshots = foreach ($preferenceName in $preferenceNames) {
        Get-DeviceSnapshot -PreferenceName $preferenceName -PollNumber $poll
    }

    $terminalEntries = [System.Collections.Generic.List[object]]::new()
    foreach ($snapshot in $snapshots) {
        if ($snapshot.Status -eq "ok") {
            foreach ($entry in (Get-TerminalSuiteEntries -Values $snapshot.Values -PreferenceName $snapshot.Name)) {
                $terminalEntries.Add($entry)
            }
        }
    }
    $suiteSnapshot = $snapshots | Where-Object { $_.Name -eq "raw_sensor_results" -and $_.Status -eq "ok" } | Select-Object -First 1
    $observedSuiteId = $null
    $observedRunState = $null
    if ($null -ne $suiteSnapshot) {
        $observedSuiteId = $suiteSnapshot.Values["suite_session_id"].Value
        $observedRunState = $suiteSnapshot.Values["suite_run_state"].Value
    }
    if ([string]::IsNullOrWhiteSpace($latchedSuiteId) -and
        -not [string]::IsNullOrWhiteSpace($observedSuiteId) -and
        $observedSuiteId -ne $PreviousSuiteId) {
        $latchedSuiteId = $observedSuiteId
    }
    $expectedTerminal = -not [string]::IsNullOrWhiteSpace($latchedSuiteId) -and
        $latchedSuiteId -ne $PreviousSuiteId -and $observedSuiteId -eq $latchedSuiteId -and
        @("completed", "partial", "cancelled", "timed_out", "interrupted", "failed") -contains $observedRunState

    $summary = [pscustomobject]@{
        run_id = $runId
        poll = $poll
        observed_at = [DateTime]::UtcNow.ToString("o")
        preferences = @($snapshots | ForEach-Object {
            [pscustomobject]@{ name = $_.Name; status = $_.Status; exit_code = $_.ExitCode; raw_xml = $_.RawPath }
        })
        terminal_suites = @($terminalEntries | ForEach-Object {
            [pscustomobject]@{ suite_id = $_.SuiteId; kind = $_.Kind; preference = $_.Preference }
        })
        expected_suite_terminal = $expectedTerminal
        expected_suite_id = $latchedSuiteId
        observed_suite_id = $observedSuiteId
        observed_run_state = $observedRunState
        terminated = $expectedTerminal
    }
    $jsonLine = $summary | ConvertTo-Json -Depth 6 -Compress
    [IO.File]::AppendAllText($summaryPath, $jsonLine + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))

    if ($expectedTerminal) { break }
    if ($poll -lt $MaxPolls -and $PollSeconds -gt 0) { Start-Sleep -Seconds $PollSeconds }
}

Write-Output ("Acceptance snapshots saved locally: {0}" -f $runDirectory)
