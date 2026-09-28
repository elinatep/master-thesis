#!/usr/bin/env bash
# rotate_secret.sh - give the study a new HANDOVER_SECRET.
#
#   ./scripts/rotate_secret.sh              # generate one
#   ./scripts/rotate_secret.sh --show       # print the current one and stop
#
# Rotating by hand means editing infra/.env, redeploying, and updating the header in Qualtrics -
# in that order, and remembering that the middle step is what makes it real. Miss it and the app
# keeps checking the old value while the file says otherwise, which surfaces as a 401 on every
# participant with nothing explaining why.
#
# This does the first step, says plainly that the app has not changed yet, and offers the second.
# The third is yours: the X-Handover-Secret header on the Web Service element in Survey 1.

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="$root/infra/.env"
show_only=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --show)     show_only=1; shift ;;
    --env-file) env_file="$2"; shift 2 ;;
    -h|--help)  sed -n '2,15p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

if [[ ! -f "$env_file" ]]; then
  echo "Env file not found: $env_file" >&2
  echo "It is a hidden file - Finder does not show it. It should be beside infra/.env.example." >&2
  exit 1
fi

read_var() {
  sed -n "s/^[[:space:]]*$1[[:space:]]*=//p" "$env_file" | head -n1 | sed 's/[[:space:]]*$//'
}

current="$(read_var HANDOVER_SECRET)"

if [[ $show_only -eq 1 ]]; then
  if [[ -z "$current" ]]; then
    echo "HANDOVER_SECRET is empty in $env_file."
    exit 1
  fi
  echo "$current"
  exit 0
fi

# uuidgen is on macOS and most Linux; openssl is the fallback. Both give something with no spaces,
# which is the point of rotating: a double space in a secret is invisible in the file AND in the
# Qualtrics field, so a mismatch caused by one cannot be seen anywhere.
if command -v uuidgen >/dev/null 2>&1; then
  new_secret="$(uuidgen | tr '[:upper:]' '[:lower:]')"
elif command -v openssl >/dev/null 2>&1; then
  new_secret="$(openssl rand -hex 16)"
else
  echo "Neither uuidgen nor openssl is available to generate a secret." >&2
  exit 1
fi

# Keep the previous file. .env.bak is gitignored (see the root .gitignore) - it holds a live
# credential and must never become an untracked file that `git add -A` sweeps up.
cp "$env_file" "$env_file.bak"

# Rewrite via a temp file rather than `sed -i`: the in-place flag takes an argument on macOS and
# not on GNU, so the same command corrupts the file on one of the two. Replace the line if it is
# there, append it if it is not, and leave every other line byte-for-byte alone.
tmp="$(mktemp)"
replaced=0
while IFS= read -r line || [[ -n "$line" ]]; do
  if [[ "$line" =~ ^[[:space:]]*HANDOVER_SECRET[[:space:]]*= ]]; then
    printf 'HANDOVER_SECRET=%s\n' "$new_secret" >> "$tmp"
    replaced=1
  else
    printf '%s\n' "$line" >> "$tmp"
  fi
done < "$env_file"
[[ $replaced -eq 1 ]] || printf 'HANDOVER_SECRET=%s\n' "$new_secret" >> "$tmp"

cat "$tmp" > "$env_file"     # preserve the original file's permissions
rm -f "$tmp"

echo "-------------------------------------------------------------------------------"
echo " New handover secret, written to infra/.env:"
echo
echo "   $new_secret"
echo
echo " Previous value saved to infra/.env.bak (gitignored)."
echo "-------------------------------------------------------------------------------"
echo
echo "THE APP IS STILL CHECKING THE OLD SECRET. Two things left:"
echo
echo "  1. Redeploy          ./scripts/release.sh"
echo "  2. Update Qualtrics  Survey 1 -> Survey Flow -> the Web Service element ->"
echo "                       the X-Handover-Secret header -> paste the value above"
echo
echo "Do them in that order. Between the redeploy and the Qualtrics edit the survey"
echo "would send the old secret and get a 401, so do not leave it half done while"
echo "anyone can reach Survey 1."
echo

read -r -p "Redeploy now? [y/N] " answer
if [[ "$answer" == "y" || "$answer" == "Y" ]]; then
  exec "$root/scripts/release.sh"
fi

echo "Not redeploying. Run ./scripts/release.sh when you are ready - until then the app"
echo "still has the old secret, and infra/.env no longer matches it."
