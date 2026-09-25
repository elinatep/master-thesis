#!/usr/bin/env bash
# release.sh - build the image tagged with the current git commit, push it, deploy it.
#
#   ./scripts/release.sh
#
# Tag = short git SHA, so every deployed revision maps to a known commit. containerImage has no
# default in the Bicep, so this is the only supported way to deploy - a run can never silently ship
# a stale image.
#
# On a brand-new resource group there is no registry to push to yet, so the platform is created on
# its own first. That happens once; later runs find the registry and skip straight past.
#
# The build resolves the insurance portal from the two local registries (docker/settings.xml and
# the frontend's .npmrc both name host.docker.internal), so those must be running in the portal
# repository before this will work.

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="$root/infra/.env"
acr=""
repo="pretest-feeling-heard"
resource_group=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --acr)            acr="$2"; shift 2 ;;
    --repo)           repo="$2"; shift 2 ;;
    --resource-group) resource_group="$2"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

if [[ ! -f "$env_file" ]]; then
  echo "infra/.env not found. Copy infra/.env.example to infra/.env and fill it in." >&2
  exit 1
fi

if [[ -z "$resource_group" ]]; then
  resource_group="$(sed -n 's/^[[:space:]]*RESOURCE_GROUP[[:space:]]*=//p' "$env_file" | head -n1 | sed 's/[[:space:]]*$//')"
  [[ -n "$resource_group" ]] || resource_group="rg-e2-feeling-heard"
fi
echo "==> Resource group: $resource_group"

sha="$(git -C "$root" rev-parse --short HEAD)"
[[ -n "$sha" ]] || { echo "Could not read git commit SHA." >&2; exit 1; }

if [[ -n "$(git -C "$root" status --porcelain)" ]]; then
  echo "WARNING: working tree is dirty - image '$sha' will not fully reflect the committed state."
fi

if [[ -z "$acr" ]]; then
  acr="$(az acr list --resource-group "$resource_group" --query "[0].name" --output tsv 2>/dev/null || true)"
fi

if [[ -z "$acr" ]]; then
  echo "==> No registry yet - creating the platform first (one-off, ~10 minutes)"
  "$root/scripts/deploy.sh" --platform-only --resource-group "$resource_group"
  acr="$(az acr list --resource-group "$resource_group" --query "[0].name" --output tsv)"
  [[ -n "$acr" ]] || { echo "Bootstrap finished but no registry appeared in $resource_group." >&2; exit 1; }
fi
echo "==> Registry: $acr"

image="$acr.azurecr.io/$repo:$sha"

# --platform linux/amd64 is load-bearing on an Apple Silicon Mac: Docker would otherwise build an
# arm64 image, which Azure Container Apps cannot run. The failure is a container that starts and
# immediately dies, which is a miserable thing to debug.
echo "==> Building $image"
docker build --platform linux/amd64 -f "$root/docker/Dockerfile" -t "$image" "$root"

echo "==> Logging in + pushing to ACR"
az acr login --name "$acr"
docker push "$image"

echo "==> Deploying"
"$root/scripts/deploy.sh" --image "$image" --resource-group "$resource_group"

echo "==> Done. Deployed image: $image"
