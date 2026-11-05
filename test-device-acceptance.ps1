$ErrorActionPreference = "Stop"
$testDirectory = Join-Path $PSScriptRoot ("build/acceptance/harness-tests/" + [guid]::NewGuid().ToString("N"))
[void][IO.Directory]::CreateDirectory($testDirectory)
$script:passed = 0
$script:assertions = 0
Start-Transcript -Path (Join-Path $testDirectory 'results.txt') | Out-Null

function Assert-Condition {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
    $script:assertions++
}

function New-SuiteXml {
    param([string]$SuiteId, [string]$State)
    return "<map><string name='suite_session_id'>$SuiteId</string><string name='suite_run_state'>$State</string><string name='ppg_suite_id'>$SuiteId</string><boolean name='ppg_final' value='true'/><string name='ppg_watch_suite_id'>$SuiteId</string><boolean name='ppg_watch_final' value='true'/><string name='ppg_phone_suite_id'>$SuiteId</string><boolean name='ppg_phone_final' value='true'/><string name='metadata_test_suite_id'>$SuiteId</string><boolean name='metadata_test_final' value='true'/></map>"
}

function Invoke-Regression {
    param(
        [string]$Name, [string[]]$Frames, [string]$Expected = "new",
        [string]$Previous = "old", [int]$ExitCode = 0, [scriptblock]$Verify
    )
    $mockState = @{ Frames = $Frames; CallCount = 0; ExitCode = $ExitCode }
    function adb {
        $frameIndex = [int][Math]::Floor($mockState.CallCount / 3)
        $mockState.CallCount++
        $global:LASTEXITCODE = $mockState.ExitCode
        if ($args[-1] -like '*/raw_sensor_results.xml') {
            return $mockState.Frames[[Math]::Min($frameIndex, $mockState.Frames.Count - 1)]
        }
        return '<map/>'
    }
    $caseDirectory = Join-Path $testDirectory $Name
    $null = & (Join-Path $PSScriptRoot 'collect-device-acceptance.ps1') -PhoneSerial mock-only `
        -ExpectedSuiteId $Expected -PreviousSuiteId $Previous -OutputDirectory $caseDirectory `
        -MaxPolls $Frames.Count -PollSeconds 0
    $runDirectory = @(Get-ChildItem -LiteralPath $caseDirectory -Directory)[0].FullName
    $rows = @(Get-Content -LiteralPath (Join-Path $runDirectory 'poll-summary.jsonl') | ForEach-Object { $_ | ConvertFrom-Json })
    & $Verify $rows $runDirectory
    $script:passed++
    Write-Output "PASS $Name"
}

$savedExitCode = $global:LASTEXITCODE
try {
    $identityDirectory = Join-Path $testDirectory 'missing-identity'
    $identityMock = @{ CallCount = 0 }
    function adb {
        $identityMock.CallCount++
        throw 'Missing identity must be rejected before adb'
    }
    $identityError = $null
    try {
        $null = & (Join-Path $PSScriptRoot 'collect-device-acceptance.ps1') -PhoneSerial mock-only `
            -ExpectedSuiteId ' ' -PreviousSuiteId '' -OutputDirectory $identityDirectory -MaxPolls 1 -PollSeconds 0
    } catch {
        $identityError = $_.Exception.Message
    }
    Assert-Condition ($identityError -eq 'Provide ExpectedSuiteId or the verified PreviousSuiteId before collecting a new suite.') 'Missing identity was not explicitly rejected'
    Assert-Condition ($identityMock.CallCount -eq 0) 'Missing identity invoked adb'
    Assert-Condition (-not (Test-Path -LiteralPath $identityDirectory)) 'Missing identity created collection artifacts'
    $script:passed++
    Write-Output 'PASS missing-identity'
    Invoke-Regression -Name empty-snapshot -Frames @('', '   ') -Verify {
        param($Rows, $RunDirectory)
        Assert-Condition ($Rows.Count -eq 2 -and @($Rows | Where-Object terminated).Count -eq 0) 'Empty snapshot stopped collection'
        Assert-Condition (@($Rows | Where-Object { $_.preferences[0].status -ne 'empty_snapshot' -or $_.preferences[0].exit_code -ne 0 -or $null -ne $_.preferences[0].raw_xml }).Count -eq 0) 'Empty snapshot classification incorrect'
        Assert-Condition (@(Get-ChildItem -LiteralPath (Join-Path $RunDirectory raw) -Filter 'raw_sensor_results-*.xml').Count -eq 0) 'Empty snapshot wrote raw XML'
    }
    Invoke-Regression -Name unknown-runstate -Frames @((New-SuiteXml new unexpected), (New-SuiteXml new unexpected)) -Verify {
        param($Rows)
        Assert-Condition ($Rows.Count -eq 2 -and @($Rows | Where-Object { $_.terminated -or $_.expected_suite_terminal }).Count -eq 0) 'Unknown runstate was treated as terminal'
        Assert-Condition (@($Rows | Where-Object { $_.observed_run_state -ne 'unexpected' -or $_.observed_suite_id -ne 'new' -or $_.preferences[0].status -ne 'ok' }).Count -eq 0) 'Unknown runstate observation was lost'
    }
    $missingFieldCases = @(
        @{ Name = 'missing-suite-id'; Xml = "<map><string name='suite_run_state'>completed</string></map>"; SuiteId = $null; State = 'completed' },
        @{ Name = 'missing-runstate'; Xml = "<map><string name='suite_session_id'>new</string></map>"; SuiteId = 'new'; State = $null },
        @{ Name = 'missing-suite-fields'; Xml = '<map/>'; SuiteId = $null; State = $null }
    )
    foreach ($missingFieldCase in $missingFieldCases) {
        Invoke-Regression -Name $missingFieldCase.Name -Frames @($missingFieldCase.Xml, $missingFieldCase.Xml) -Verify {
            param($Rows)
            Assert-Condition ($Rows.Count -eq 2 -and @($Rows | Where-Object { $_.terminated -or $_.expected_suite_terminal }).Count -eq 0) 'Missing suite fields stopped collection'
            Assert-Condition (@($Rows | Where-Object { $_.preferences[0].status -ne 'ok' -or $_.observed_suite_id -ne $missingFieldCase.SuiteId -or $_.observed_run_state -ne $missingFieldCase.State }).Count -eq 0) 'Missing suite fields were fabricated or misclassified'
        }
    }
    Invoke-Regression -Name poll-exhaustion -Frames @((New-SuiteXml new running), (New-SuiteXml new running), (New-SuiteXml new running)) -Verify {
        param($Rows, $RunDirectory)
        Assert-Condition ($Rows.Count -eq 3 -and ($Rows.poll -join ',') -eq '1,2,3') 'Poll exhaustion did not respect MaxPolls'
        Assert-Condition ($mockState.CallCount -eq 9) 'Poll exhaustion performed an unexpected number of adb calls'
        Assert-Condition (@($Rows | Where-Object { $_.terminated -or $_.expected_suite_terminal }).Count -eq 0) 'Poll exhaustion fabricated suite termination'
        Assert-Condition (@(Get-ChildItem -LiteralPath (Join-Path $RunDirectory raw) -Filter '*.xml').Count -eq 9) 'Poll exhaustion did not preserve all snapshots'
    }
    Invoke-Regression -Name running-final -Frames @((New-SuiteXml new running), (New-SuiteXml new running)) -Verify {
        param($Rows)
        Assert-Condition ($Rows.Count -eq 2 -and -not $Rows[-1].terminated) 'Channel final stopped running suite'
        Assert-Condition ($Rows[0].terminal_suites.Count -eq 1 -and $Rows[0].terminal_suites[0].kind -eq 'ppg') ("Metadata channels leaked into terminal entries: " + ($Rows[0].terminal_suites | ConvertTo-Json -Compress))
    }
    Invoke-Regression -Name completed -Frames @((New-SuiteXml new running), (New-SuiteXml new completed), (New-SuiteXml new running)) -Verify {
        param($Rows)
        Assert-Condition ($Rows.Count -eq 2 -and $Rows[-1].terminated) 'Matching completed suite did not stop'
    }
    Invoke-Regression -Name old-suite -Expected old -Frames @((New-SuiteXml old completed), (New-SuiteXml old completed)) -Verify {
        param($Rows)
        Assert-Condition ($Rows.Count -eq 2 -and -not $Rows[-1].terminated) 'Previous suite stopped collection'
    }
    Invoke-Regression -Name different-suite -Frames @((New-SuiteXml other completed), (New-SuiteXml other partial)) -Verify {
        param($Rows)
        Assert-Condition ($Rows.Count -eq 2 -and @($Rows | Where-Object terminated).Count -eq 0) 'Different suite terminal stopped collection'
    }
    Invoke-Regression -Name incomplete-channels -Frames @("<map><string name='suite_session_id'>new</string><string name='suite_run_state'>completed</string><string name='ppg_suite_id'>new</string><boolean name='ppg_final' value='false'/></map>", (New-SuiteXml new running)) -Verify {
        param($Rows)
        Assert-Condition ($Rows.Count -eq 1 -and $Rows[0].terminated) 'Suite terminal incorrectly requires channel completion'
        Assert-Condition ($Rows[0].terminal_suites.Count -eq 0) 'Suite terminal fabricated channel completion'
    }
    foreach ($state in @('partial', 'cancelled', 'timed_out', 'interrupted', 'failed')) {
        Invoke-Regression -Name $state -Frames @((New-SuiteXml new $state), (New-SuiteXml new running)) -Verify {
            param($Rows)
            Assert-Condition ($Rows.Count -eq 1 -and $Rows[0].terminated) 'Terminal state did not stop'
        }
    }
    Invoke-Regression -Name latch -Expected '' -Frames @((New-SuiteXml old completed), (New-SuiteXml new running), (New-SuiteXml other completed), (New-SuiteXml new completed), (New-SuiteXml new running)) -Verify {
        param($Rows)
        Assert-Condition ($Rows.Count -eq 4 -and $Rows[-1].terminated) 'New suite latch did not persist'
        Assert-Condition ([string]::IsNullOrWhiteSpace($Rows[0].expected_suite_id) -and $Rows[1].expected_suite_id -eq 'new' -and -not $Rows[2].terminated) 'Previous or unrelated suite affected latch'
    }
    Invoke-Regression -Name native-failure -ExitCode 1 -Frames @((New-SuiteXml new completed)) -Verify {
        param($Rows, $RunDirectory)
        Assert-Condition (@(Get-ChildItem -LiteralPath (Join-Path $RunDirectory raw) -Filter '*.xml').Count -eq 0) 'Native failure wrote XML'
        Assert-Condition (@($Rows[0].preferences | Where-Object { $_.status -ne 'transport_unavailable' -or $_.exit_code -ne 1 -or $null -ne $_.raw_xml }).Count -eq 0 -and -not $Rows[0].terminated) 'Native failure classification incorrect'
    }
    Invoke-Regression -Name invalid-xml -Frames @('<map><broken>') -Verify {
        param($Rows)
        Assert-Condition ($Rows[0].preferences[0].status -eq 'invalid_snapshot' -and (Test-Path -LiteralPath $Rows[0].preferences[0].raw_xml) -and -not $Rows[0].terminated) 'Invalid XML classification incorrect'
    }
    Write-Output "Results: $script:passed passed, 0 failed; $script:assertions assertions (local mock adb only)."
    Write-Output ("Run results: " + (Join-Path $testDirectory 'results.txt'))
} finally {
    $global:LASTEXITCODE = $savedExitCode
    Stop-Transcript | Out-Null
}
