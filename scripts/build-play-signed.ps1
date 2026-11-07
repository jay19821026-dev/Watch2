param(
    [Parameter(Mandatory = $true)][string]$SigningDirectory,
    [Parameter(Mandatory = $true)][int]$MobileVersionCode,
    [Parameter(Mandatory = $true)][int]$WearVersionCode
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$keystore = Join-Path $SigningDirectory 'healthbridge-upload.jks'
$storeSecret = Join-Path $SigningDirectory 'storepass.dpapi'
$keySecret = Join-Path $SigningDirectory 'keypass.dpapi'
foreach ($required in @($keystore, $storeSecret, $keySecret)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing signing input: $required" }
}
if ($MobileVersionCode -le 155 -or $WearVersionCode -le 155 -or $MobileVersionCode -eq $WearVersionCode) {
    throw 'Play version codes must be distinct and greater than 155.'
}

try {
    $env:RELEASE_STORE_FILE = $keystore
    $env:RELEASE_STORE_PASSWORD = ConvertFrom-SecureString (ConvertTo-SecureString (Get-Content -LiteralPath $storeSecret -Raw)) -AsPlainText
    $env:RELEASE_KEY_ALIAS = 'healthbridge-upload'
    $env:RELEASE_KEY_PASSWORD = ConvertFrom-SecureString (ConvertTo-SecureString (Get-Content -LiteralPath $keySecret -Raw)) -AsPlainText
    Push-Location $repoRoot
    try {
        & .\gradlew.bat :mobile:bundleRelease :wear:bundleRelease "-PPLAY_MOBILE_VERSION_CODE=$MobileVersionCode" "-PPLAY_WEAR_VERSION_CODE=$WearVersionCode" --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
} finally {
    Remove-Item Env:RELEASE_STORE_FILE,Env:RELEASE_STORE_PASSWORD,Env:RELEASE_KEY_ALIAS,Env:RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
}
