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
# Requires psql on PATH, and your own IP allowed on the server's firewall. The deployment opens the
# firewall to Azure services only - that is what lets the container app in, and it deliberately does
# not let your machine in. Both are checked below before anything is dropped, because both otherwise
# fail AFTER the confirmation prompt, which reads as though the wipe half-happened.

param(
    [string]$EnvFile = "$PSScriptRoot/../infra/.env",
    [string]$ResourceGroup = 'rg-e2-feeling-heard',
    [string]$DbName = 'pretest_feeling_heard',
    [string]$AppName = 'pretest-feeling-heard'
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

if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
    Write-Host 'psql is not on PATH - this script needs it to talk to Postgres.' -ForegroundColor Red
    Write-Host 'Install the PostgreSQL client tools, or:  winget install PostgreSQL.PostgreSQL'
    exit 1
}

# The deployment's only firewall rule is AllowAllAzureServices, which lets the container app in and
# your machine stay out. Without a rule for this machine psql just hangs until it times out, well
# after the confirmation prompt - so check now, while nothing has been dropped.
$myIp = try { (Invoke-RestMethod -Uri 'https://api.ipify.org' -TimeoutSec 10).Trim() } catch { '' }
if ([string]::IsNullOrWhiteSpace($myIp)) {
    Write-Warning 'Could not work out this machine''s public IP; skipping the firewall check. If psql hangs below, that is why.'
} else {
    $serverName = (az postgres flexible-server list --resource-group $ResourceGroup --query "[0].name" --output tsv)
    # --server-name, not --name: in the firewall-rule subgroup --name is the RULE name, and the
    # server passed to it is rejected outright.
    $ruleName = "laptop-$($myIp -replace '\.', '-')"
    $allowed = az postgres flexible-server firewall-rule list `
        --resource-group $ResourceGroup --server-name $serverName `
        --query "[?startIpAddress=='$myIp'] | length(@)" --output tsv 2>$null
    if ($allowed -ne '1') {
        Write-Host "Your IP ($myIp) is not allowed on $serverName's firewall, so psql cannot connect."
        Write-Host 'Adding a rule opens the database to this IP until you remove it.'
        $answer = Read-Host "Add a firewall rule for $myIp? [y/N]"
        if ($answer -eq 'y') {
            az postgres flexible-server firewall-rule create `
                --resource-group $ResourceGroup --server-name $serverName `
                --rule-name $ruleName `
                --start-ip-address $myIp --end-ip-address $myIp `
                --output none
            Write-Host 'Rule added. Remove it when you are done:'
            Write-Host "  az postgres flexible-server firewall-rule delete -g $ResourceGroup --server-name $serverName --rule-name $ruleName --yes"
        } else {
            Write-Host 'Not adding it. psql will not be able to connect.' -ForegroundColor Red
            exit 1
        }
    }
}

Write-Host ''
Write-Host "About to DROP schemas insurance_portal and behavioural in $DbName on $server."
Write-Host "This deletes every participant's data in the pre-test."
Write-Host 'Export it first if you have not: the CSV button at <appUrl>/data.'
Write-Host ''
$confirm = Read-Host "Type the database name to confirm ($DbName)"
if ($confirm -ne $DbName) { Write-Host 'Aborted.'; exit 1 }

$env:PGPASSWORD = $password
psql --host=$server --username=$user --dbname=$DbName --set=sslmode=require `
     --command='DROP SCHEMA IF EXISTS behavioural CASCADE; DROP SCHEMA IF EXISTS insurance_portal CASCADE;'
Remove-Item Env:PGPASSWORD

Write-Host '==> Dropped.'

# Restart the app rather than telling the reader to. Dropping the schemas leaves the running
# container pointed at tables that no longer exist: Flyway and the portal's seeding both run at
# startup, so until it restarts every request fails on a missing relation. Leaving that as a note
# at the end of the output means the study is broken in a way nothing announces - and /data keeps
# serving whatever the browser already had, which reads as "the wipe did not work".
Write-Host '==> Restarting the container app so it rebuilds both schemas and re-seeds the account'

$revision = az containerapp revision list `
    --resource-group $ResourceGroup --name $AppName `
    --query "[?properties.active].name | [0]" --output tsv 2>$null

if ([string]::IsNullOrWhiteSpace($revision)) {
    Write-Host "Could not find an active revision of '$AppName' to restart." -ForegroundColor Red
    Write-Host 'Restart it yourself, or the app will keep failing on missing tables:'
    Write-Host "  az containerapp revision restart -g $ResourceGroup -n $AppName --revision <name>"
    exit 1
}

az containerapp revision restart `
    --resource-group $ResourceGroup --name $AppName --revision $revision --output none

Write-Host "==> Restarted ($revision). Give it about a minute, then reload <appUrl>/data with a hard"
Write-Host '    refresh (Ctrl-Shift-R). It should show 0 sessions.'

