# scripts/release.ps1 - build the image tagged with the current git commit, push to ACR, deploy it.
#
#   ./scripts/release.ps1
#
# Tag = short git SHA, so every deployed revision maps to a known commit. containerImage is a
# required Bicep parameter with no default, so this is the only supported way to deploy - a run
# can never silently ship a stale image.
#
# The registry belongs to the shared platform, so it is looked up rather than hard-coded; pass
# -Acr to override. ACR Tasks are disabled on this subscription, so the build is local docker.

param(
    [string]$Acr,
    [string]$Repo = 'pretest-feeling-heard',
    [string]$ResourceGroup = 'rg-insurance-portal'
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path "$PSScriptRoot/..").Path

$sha = (git -C $root rev-parse --short HEAD).Trim()
if ([string]::IsNullOrWhiteSpace($sha)) { throw 'Could not read git commit SHA.' }

if (git -C $root status --porcelain) {
    Write-Warning "Working tree is dirty - image '$sha' will not fully reflect the committed state."
}

if ([string]::IsNullOrWhiteSpace($Acr)) {
    Write-Host "==> Looking up the shared registry in $ResourceGroup"
    $Acr = (az acr list --resource-group $ResourceGroup --query "[0].name" --output tsv)
    if ([string]::IsNullOrWhiteSpace($Acr)) {
        throw "No container registry found in $ResourceGroup. Has the shared platform been deployed from levels-of-ai-help?"
    }
    Write-Host "    found: $Acr"
}

$image = "$Acr.azurecr.io/${Repo}:$sha"

# The build resolves the portal from the local registries (docker/settings.xml and the frontend's
# .npmrc both name host.docker.internal), so those have to be running in the portal repository.
Write-Host "==> Building $image"
docker build -f "$root/docker/Dockerfile" -t $image $root

Write-Host '==> Logging in + pushing to ACR'
az acr login --name $Acr
docker push $image

Write-Host '==> Deploying'
& "$PSScriptRoot/deploy.ps1" -ContainerImage $image -ResourceGroup $ResourceGroup

Write-Host "==> Done. Deployed image: $image"
