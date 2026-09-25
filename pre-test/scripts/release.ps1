# scripts/release.ps1 - build the image tagged with the current git commit, push to ACR, deploy it.
#
#   ./scripts/release.ps1
#
# Tag = short git SHA, so every deployed revision maps to a known commit. containerImage is a
# required Bicep parameter with no default, so this is the only supported way to deploy - a run
# can never silently ship a stale image.
#
# The registry is created by the first deploy, so on a brand-new resource group there is nothing to
# push to yet: the script deploys once with a placeholder image to bring the platform up, then
# builds, pushes and deploys for real. Pass -Acr to skip the lookup. ACR Tasks are disabled on this
# subscription, so the build is a local docker build.

param(
    [string]$Acr,
    [string]$Repo = 'pretest-feeling-heard',
    [string]$ResourceGroup
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path "$PSScriptRoot/..").Path
$envFile = "$root/infra/.env"

if (-not (Test-Path $envFile)) {
    throw "infra/.env not found. Copy infra/.env.example to infra/.env and fill it in."
}

if ([string]::IsNullOrWhiteSpace($ResourceGroup)) {
    $line = Get-Content $envFile | Where-Object { $_ -match '^\s*RESOURCE_GROUP\s*=' } | Select-Object -First 1
    $ResourceGroup = if ($line) { ($line -split '=', 2)[1].Trim() } else { 'rg-e2-feeling-heard' }
}
Write-Host "==> Resource group: $ResourceGroup" 

$sha = (git -C $root rev-parse --short HEAD).Trim()
if ([string]::IsNullOrWhiteSpace($sha)) { throw 'Could not read git commit SHA.' }

if (git -C $root status --porcelain) {
    Write-Warning "Working tree is dirty - image '$sha' will not fully reflect the committed state."
}

if ([string]::IsNullOrWhiteSpace($Acr)) {
    $Acr = (az acr list --resource-group $ResourceGroup --query "[0].name" --output tsv)
}

if ([string]::IsNullOrWhiteSpace($Acr)) {
    # First run against an empty resource group: the image has to be pushed to a registry that does
    # not exist yet. Create the platform on its own first, then carry on with the real build.
    # Only ever happens once; later runs find the registry and skip straight past.
    Write-Host '==> No registry yet - creating the platform first (one-off, ~5-10 minutes)'
    & "$PSScriptRoot/deploy.ps1" -PlatformOnly -ResourceGroup $ResourceGroup
    $Acr = (az acr list --resource-group $ResourceGroup --query "[0].name" --output tsv)
    if ([string]::IsNullOrWhiteSpace($Acr)) {
        throw "Bootstrap finished but no registry appeared in $ResourceGroup. Check the deployment output above."
    }
}
Write-Host "==> Registry: $Acr" 

$image = "$Acr.azurecr.io/${Repo}:$sha"

# The build resolves the portal from the local registries (docker/settings.xml and the frontend's
# .npmrc both name host.docker.internal), so those have to be running in the portal repository.
# --platform linux/amd64 is load-bearing on an ARM machine: Azure Container Apps cannot run an
# arm64 image, and the symptom is a container that starts and immediately dies.
Write-Host "==> Building $image"
docker build --platform linux/amd64 -f "$root/docker/Dockerfile" -t $image $root

Write-Host '==> Logging in + pushing to ACR'
az acr login --name $Acr
docker push $image

Write-Host '==> Deploying'
& "$PSScriptRoot/deploy.ps1" -ContainerImage $image -ResourceGroup $ResourceGroup

Write-Host "==> Done. Deployed image: $image"
