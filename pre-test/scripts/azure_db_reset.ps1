# scripts/azure_db_reset.ps1 - wipe the pre-test's data on Azure, leaving the server alone.
#
#   ./scripts/azure_db_reset.ps1
#
# Drops both schemas in the pre-test's OWN database: insurance_portal (the seeded account) and
# behavioural (the study log). Each carries its own Flyway history table, so dropping them is
# enough - the next start rebuilds both from scratch.
#
# The server itself is left alone. Run this between pilot rounds so an analysis never mixes runs
# from two different versions of the arms.
#
# Requires psql on PATH and your IP allowed on the server's firewall.

param(
    [string]$EnvFile = "$PSScriptRoot/../infra/.env",
    [string]$ResourceGroup = 'rg-e2-feeling-heard',
    [string]$DbName = 'pretest_feeling_heard'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $EnvFile)) { throw "Env file not found: $EnvFile" }

$vars = @{}
Get-Content $EnvFile | Where-Object { $_ -match '^\s*[^#]\S*\s*=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    $vars[$name.Trim()] = $value.Trim()
}

$user = if ([string]::IsNullOrWhiteSpace($vars['DB_ADMIN_USER'])) { 'portaladmin' } else { $vars['DB_ADMIN_USER'] }
$password = $vars['DB_ADMIN_PASSWORD']
if ([string]::IsNullOrWhiteSpace($password)) { throw "DB_ADMIN_PASSWORD missing in $EnvFile" }

$server = (az postgres flexible-server list --resource-group $ResourceGroup --query "[0].fullyQualifiedDomainName" --output tsv)
if ([string]::IsNullOrWhiteSpace($server)) { throw "No Postgres server found in $ResourceGroup." }

Write-Host "About to DROP schemas insurance_portal and behavioural in $DbName on $server."
$confirm = Read-Host "This deletes every participant's data in the pre-test. Type the database name to confirm"
if ($confirm -ne $DbName) { Write-Host 'Aborted.'; exit 1 }

$env:PGPASSWORD = $password
psql --host=$server --username=$user --dbname=$DbName --set=sslmode=require `
     --command='DROP SCHEMA IF EXISTS behavioural CASCADE; DROP SCHEMA IF EXISTS insurance_portal CASCADE;'
Remove-Item Env:PGPASSWORD

Write-Host "==> Dropped. Restart the container app (or redeploy) to rebuild both schemas."
