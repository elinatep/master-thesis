# scripts/pilot.ps1 - walk one arm of the pre-test yourself, as a participant would.
#
#   ./scripts/pilot.ps1 S4                    # fresh run in arm S4
#   ./scripts/pilot.ps1 S1 -Participant me-1  # fresh run in arm S1, with an id you choose
#   ./scripts/pilot.ps1 -UrlOnly              # just print the app URL and stop
#
# Nothing to fill in: the app URL comes from Azure and the handover secret from infra/.env, which
# are the same two values the deploy used. Doing this by hand means pasting a URL and a secret into
# a curl command, and a mistyped secret fails with 401 in a way that reads like a broken app.
#
# Each run gets a NEW participant id unless you pass one. That is deliberate: reusing an id resumes
# the earlier session, which in S4 means arriving with an attempt already spent - the failure you
# were trying to see does not happen, and nothing says why.

param(
    [Parameter(Position = 0)]
    [string]$Arm,
    [string]$Participant,
    [string]$EnvFile = "$PSScriptRoot/../infra/.env",
    [switch]$UrlOnly
)

$ErrorActionPreference = 'Stop'

$root    = Resolve-Path "$PSScriptRoot/.."
$appName = 'pretest-feeling-heard'

if (-not (Test-Path $EnvFile)) {
    throw "Env file not found: $EnvFile. This is the same infra/.env the deploy used - run from the pre-test directory."
}

$vars = @{}
Get-Content $EnvFile | Where-Object { $_ -match '^\s*[^#]\S*\s*=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    $vars[$name.Trim()] = $value.Trim()
}

$resourceGroup = if ([string]::IsNullOrWhiteSpace($vars['RESOURCE_GROUP'])) { 'rg-e2-feeling-heard' } else { $vars['RESOURCE_GROUP'] }
$secret        = $vars['HANDOVER_SECRET']

# The arms are the files in configuration/arms, so this list cannot drift from what the app loads.
$knownArms = @(Get-ChildItem "$root/configuration/arms/*.json" -ErrorAction SilentlyContinue |
    ForEach-Object { $_.BaseName })

Write-Host '==> Asking Azure for the app URL'
$fqdn = az containerapp show `
    --resource-group $resourceGroup `
    --name $appName `
    --query properties.configuration.ingress.fqdn `
    --output tsv 2>$null

if ([string]::IsNullOrWhiteSpace($fqdn)) {
    Write-Host "Could not find the container app '$appName' in resource group '$resourceGroup'." -ForegroundColor Red
    Write-Host 'Either the deploy has not run yet, or you are logged into a different subscription.'
    Write-Host "Check with:  az containerapp list -g $resourceGroup -o table"
    exit 1
}

$app = "https://$($fqdn.Trim())"
Write-Host "    $app"

if ($UrlOnly) {
    Write-Host ''
    Write-Host $app
    exit 0
}

if ([string]::IsNullOrWhiteSpace($Arm)) {
    Write-Host ''
    Write-Host "Which arm? Pass one of: $($knownArms -join ' ')"
    Write-Host '  ./scripts/pilot.ps1 S4'
    exit 2
}

# Match case-insensitively but send the id as configured, so 's4' works and the app still gets 'S4'.
# An unknown arm is rejected here rather than by the server, so the message can list the real ones.
$matched = $knownArms | Where-Object { $_ -ieq $Arm } | Select-Object -First 1
if (-not $matched) {
    Write-Host "Unknown arm '$Arm'. Configured arms are: $($knownArms -join ' ')" -ForegroundColor Red
    exit 2
}
$Arm = $matched

if ([string]::IsNullOrWhiteSpace($Participant)) {
    $Participant = "pilot-$($Arm.ToLower())-$(Get-Date -Format 'HHmmss')"
}

Write-Host "==> Registering a handover: arm=$Arm participant=$Participant"

$body = @{
    participantId = $Participant
    arm           = $Arm
    name          = 'Joe Smith'
    callbackUrl   = 'https://mtecethz.eu.qualtrics.com'
} | ConvertTo-Json -Compress

$headers = @{ 'Content-Type' = 'application/json' }
if (-not [string]::IsNullOrWhiteSpace($secret)) {
    $headers['X-Handover-Secret'] = $secret
} else {
    Write-Host "    (no HANDOVER_SECRET in $EnvFile - sending an unauthenticated register)"
}

try {
    $response = Invoke-RestMethod -Method Post -Uri "$app/api/handover" -Headers $headers -Body $body
} catch {
    $status = $null
    if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
    Write-Host ''
    Write-Host "Registering the handover failed$(if ($status) { " (HTTP $status)" })." -ForegroundColor Red
    Write-Host $_.ErrorDetails.Message
    switch ($status) {
        401 {
            Write-Host ''
            Write-Host '401 means the secret did not match. The app has the value HANDOVER_SECRET had at'
            Write-Host 'the last deploy; if you changed it in infra/.env since, redeploy:'
            Write-Host '  ./scripts/release.ps1'
        }
        400 {
            Write-Host ''
            Write-Host "400 usually means the arm is not one the app loaded. It knows: $($knownArms -join ' ')"
        }
        default {
            Write-Host ''
            Write-Host 'If there was no response at all, the app may still be starting after a deploy. Wait a minute.'
        }
    }
    exit 1
}

$entry = "$app/?t=$($response.token)"

Write-Host ''
Write-Host '-------------------------------------------------------------------------------'
Write-Host " Arm:         $Arm"
Write-Host " Participant: $Participant"
Write-Host ''
Write-Host ' Entry link (this is what Qualtrics sends a participant to):'
Write-Host ''
Write-Host "   $entry"
Write-Host ''
Write-Host " You should land on the Salvena dashboard, file a claim, and end on the arm's"
Write-Host ' outcome screen. Afterwards, check what was recorded at:'
Write-Host ''
Write-Host "   $app/data"
Write-Host '-------------------------------------------------------------------------------'

Start-Process $entry
