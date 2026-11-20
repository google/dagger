#!/bin/bash
# Deletes any incomplete staged Maven package version in the OSS Exit Gate
# Artifact Registry repository before retrying a release for the same version.
#
# Usage:
#   util/cleanup-exit-gate-staging.sh <version>

set -euo pipefail

if [ $# -ne 1 ] || [ -z "$1" ]; then
  echo "Usage: $0 <version>" >&2
  exit 1
fi
readonly VERSION="$1"
readonly PROJECT="oss-exit-gate-prod"
readonly LOCATION="us"
readonly REPOSITORY="dagger--mavencentral"

PKGS="$(gcloud artifacts packages list \
  --project="${PROJECT}" \
  --location="${LOCATION}" \
  --repository="${REPOSITORY}" \
  --format="value(name)" 2>/dev/null || true)"

for pkg in ${PKGS}; do
  pkg_id="$(basename "${pkg}")"
  if gcloud artifacts versions describe "${VERSION}" \
    --package="${pkg_id}" \
    --project="${PROJECT}" \
    --location="${LOCATION}" \
    --repository="${REPOSITORY}" >/dev/null 2>&1; then
    echo "Deleting incomplete staged ${pkg_id}@${VERSION} before retry..."
    gcloud artifacts versions delete "${VERSION}" \
      --package="${pkg_id}" \
      --project="${PROJECT}" \
      --location="${LOCATION}" \
      --repository="${REPOSITORY}" \
      --quiet
  fi
done
