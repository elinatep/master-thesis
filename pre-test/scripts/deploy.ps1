# scripts/deploy.ps1 - deploy the pre-test onto the shared platform, loading params from infra/.env
#
# Usage:   ./scripts/deploy.ps1 -ContainerImage <acr>.azurecr.io/pretest-feeling-heard:<sha>
# Reads:   infra/.env  (gitignored - copy infra/.env.example and fill in)
#
# Resource-GROUP scoped, not subscription scoped, and that is the point: the registry, identity,
# logs, Container Apps environment and Postgres server are owned by levels-of-ai-help. This
# deployment references them and creates only its own database and container app, so the two
# studies cannot overwrite each other's platform.
#
# Prefer ./scripts/release.ps1, which builds and tags the image before calling this.

param(
    [string]$EnvFile = "$PSScriptRoot/../infra/.env",
    [Parameter(Mandatory = $true)][string]$ContainerImage,
    [string]$ResourceGroup
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $EnvFile)) {
    throw "Env file not found: $EnvFile. Copy infra/.env.example to infra/.env and fill in the values."
}

# Parse KEY=VALUE lines (skip blanks and # comments)
$vars = @{}
Get-Content $EnvFile | Where-Object { $_ -match '^\s*[^#]\S*\s*=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    $vars[$name.Trim()] = $value.Trim()
}

if ([string]::IsNullOrWhiteSpace($ResourceGroup)) {
    $ResourceGroup = if ([string]::IsNullOrWhiteSpace($vars['RESOURCE_GROUP'])) { 'rg-insurance-portal' } else { $vars['RESOURCE_GROUP'] }
}

$dbPassword = $vars['DB_ADMIN_PASSWORD']
if ([string]::IsNullOrWhiteSpace($dbPassword)) {
    throw "DB_ADMIN_PASSWORD missing or empty in $EnvFile. It is the password the SHARED Postgres server was created with."
}

# Each element is a single key=value token, so secret special-chars stay intact
$params = @("dbAdminPassword=$dbPassword", "containerImage=$ContainerImage")

if (-not [string]::IsNullOrWhiteSpace($vars['DB_ADMIN_USER'])) {
    $params += "dbAdminUser=$($vars['DB_ADMIN_USER'])"
}

if (-not [string]::IsNullOrWhiteSpace($vars['HANDOVER_SECRET'])) {
    Write-Host 'Handover secret: SET (Qualtrics must send X-Handover-Secret)'
    $params += "handoverSecret=$($vars['HANDOVER_SECRET'])"
} else {
    Write-Warning 'Handover secret: EMPTY - anyone who finds the URL can mint a token and enter the study in an arm of their choosing. Set HANDOVER_SECRET before a real run.'
}

$clientId     = $vars['AUTH_CLIENT_ID']
$clientSecret = $vars['AUTH_CLIENT_SECRET']
if (-not [string]::IsNullOrWhiteSpace($clientId) -and -not [string]::IsNullOrWhiteSpace($clientSecret)) {
    Write-Warning 'Easy Auth: ON - this blocks anonymous Prolific participants. Clear both AUTH_ values before a real run.'
    $params += "authClientId=$clientId"
    $params += "authClientSecret=$clientSecret"
} else {
    Write-Host 'Easy Auth: OFF (anonymous access - the real study mode)'
}

Write-Host "==> Deploying to resource group $ResourceGroup"

az deployment group create `
    --name pretest-feeling-heard `
    --resource-group $ResourceGroup `
    --template-file "$PSScriptRoot/../infra/main.bicep" `
    --parameters $params `
    --query "properties.outputs" `
    --output table
