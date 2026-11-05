<#!
.SYNOPSIS
Submits a bounded, sanitized technical review question to a configured local AI reviewer.

.DESCRIPTION
This helper is for architecture and QA review only. It deliberately accepts a short question,
never reads device exports, APKs, source trees, credentials, or health records, and writes the
response to an explicitly selected local Markdown file.

Hermes defaults to the verified local Ollama endpoint. Grokbot is opt-in because no endpoint or
request schema has been verified on this host; provide both -Endpoint and -Model when available.
#>
[CmdletBinding()]
param(
    [ValidateSet('Hermes', 'Grokbot')]
    [string]$Provider = 'Hermes',
    [Parameter(Mandatory)]
    [ValidateLength(1, 6000)]
    [string]$Question,
    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string]$OutputPath,
    [string]$Endpoint,
    [string]$Model,
    [ValidateSet('Ollama', 'OpenAICompatible')]
    [string]$Protocol = 'Ollama',
    [ValidateRange(5, 120)]
    [int]$TimeoutSeconds = 45
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Provider -eq 'Hermes') {
    if ([string]::IsNullOrWhiteSpace($Endpoint)) { $Endpoint = 'http://127.0.0.1:11434/api/generate' }
    if ([string]::IsNullOrWhiteSpace($Model)) { $Model = 'hermes3:8b' }
} elseif ([string]::IsNullOrWhiteSpace($Endpoint) -or [string]::IsNullOrWhiteSpace($Model)) {
    throw 'Grokbot requires a verified -Endpoint and -Model. No default endpoint is assumed.'
}

$uri = [Uri]$Endpoint
if ($uri.Scheme -notin @('https', 'http')) { throw 'Endpoint must use HTTP or HTTPS.' }
if (-not $uri.IsLoopback) { throw 'This local-review helper only accepts loopback endpoints.' }
if ($Question -match '(?i)(?:api[_ -]?key|access[_ -]?token|password|secret|private[_ -]?key|imei\s*[:=]|serial\s*[:=]|(?:android_id|device_id)\s*[:=])') {
    throw 'Question appears to include a credential or device identifier. Send a sanitized technical summary instead.'
}
if ($Question -match '(?i)(?:\b(?:0x)?[0-9a-f]{24,}\b|(?:\d+[,.]){8,}\d+|(?:192\.168\.|10\.|172\.(?:1[6-9]|2\d|3[01])\.)\d+)') {
    throw 'Question appears to include raw numeric payloads, hashes, or network/device data. Summarize counts and states instead.'
}

$request = if ($Protocol -eq 'Ollama') {
    @{ model = $Model; prompt = $Question; stream = $false; options = @{ temperature = 0; num_predict = 400 } }
} else {
    @{ model = $Model; messages = @(@{ role = 'user'; content = $Question }); stream = $false; temperature = 0; max_tokens = 400 }
}
$body = $request | ConvertTo-Json -Depth 6 -Compress
$response = Invoke-RestMethod -Method Post -Uri $uri -ContentType 'application/json' -Body $body -TimeoutSec $TimeoutSeconds
$answer = if ($Protocol -eq 'Ollama') {
    if ($null -eq $response.PSObject.Properties['response']) { throw 'Missing Ollama response field.' }
    [string]$response.response
} else {
    if ($null -eq $response.PSObject.Properties['choices'] -or $response.choices.Count -eq 0) { throw 'Missing chat completion choices.' }
    [string]$response.choices[0].message.content
}
if ([string]::IsNullOrWhiteSpace($answer) -or $answer.Length -gt 16000) { throw 'Empty or oversized review response.' }

$parent = Split-Path -Parent $OutputPath
if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
@(
    '# External AI review (untrusted)',
    '',
    "- Provider: $Provider",
    "- Model: $Model",
    "- Endpoint class: $($uri.Scheme)://$($uri.Host):$($uri.Port)$($uri.AbsolutePath)",
    '- Input: sanitized technical question only; no device export, source, credentials, or health data supplied.',
    '- Status: review input only; verify against source, tests, and hardware evidence before adoption.',
    '',
    '## Question',
    $Question,
    '',
    '## Response',
    $answer.Trim()
) | Set-Content -Path $OutputPath -Encoding utf8

Write-Output "Wrote review to $OutputPath"
