#!/bin/bash
#
# Runs the given command inside Dagger's local OCI CI environment using
# rootless podman.
#
# Usage:
#   util/podman/podman.sh [options] [--] <command> [args...]
#
# Options:
#   --workspace_dir=<dir>  Host repository directory to mount at /workspace
#                          (default: repository root enclosing this script).
#   --cache_dir=<dir>      Host directory to mount as the container's /root
#                          cache (default: $HOME/.cache/dagger-podman).
#
# Examples:
#   util/podman/podman.sh ./util/run-local-tests.sh
#   util/podman/podman.sh -- bazel test //javatests/dagger/internal/codegen/...

set -eu

readonly SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORKSPACE_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
CACHE_DIR="$HOME/.cache/dagger-podman"

while [[ $# -gt 0 && "$1" == -* ]]; do
  case "$1" in
    --workspace_dir=*) WORKSPACE_DIR="${1#*=}"; shift ;;
    --cache_dir=*) CACHE_DIR="${1#*=}"; shift ;;
    --) shift; break ;;
    *)
      echo "Unknown option: $1" >&2
      echo "Usage: podman.sh [options] [--] <command> [args...]" >&2
      exit 1
      ;;
  esac
done

if [[ $# -eq 0 ]]; then
  echo "Usage: podman.sh [options] [--] <command> [args...]" >&2
  exit 1
fi

readonly DOCKERFILE="$SCRIPT_DIR/Dockerfile"
readonly IMAGE_HASH="$(sha256sum "$DOCKERFILE" | cut -c1-12)"
readonly IMAGE_NAME="dagger-ci:${IMAGE_HASH}"

mkdir -p "$CACHE_DIR"

# Remove container on exit (--rm), forward signals cleanly (--init), disable
# Podman's default 2048 PID limit (--pids-limit=-1) for multi-core Bazel/Gradle
# builds, and attach a TTY (-t) only when stdin and stdout are terminals.
PODMAN_RUN_ARGS=(--rm --init --pids-limit=-1 -i)
if [[ -t 0 && -t 1 ]]; then
  PODMAN_RUN_ARGS+=(-t)
fi

# Build the container image if an image matching the current Dockerfile hash
# does not exist locally.
if ! podman image inspect "$IMAGE_NAME" > /dev/null 2>&1; then
  podman build -t "$IMAGE_NAME" -f "$DOCKERFILE" "$SCRIPT_DIR"
fi

# In rootless Podman, container root (UID 0, home /root) maps directly to the
# calling host user, so mounting $CACHE_DIR at /root shares one persistent host
# cache across Bazel, Gradle, and Maven while keeping host files owned by $USER.
echo "Running in Podman (${IMAGE_NAME}): $*"
exec podman run "${PODMAN_RUN_ARGS[@]}" \
  -v "$CACHE_DIR:/root" \
  -v "$WORKSPACE_DIR:/workspace" \
  -w /workspace \
  "$IMAGE_NAME" \
  "$@"
