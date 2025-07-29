#!/bin/bash

set -ex

# These jobs should match .github/workflows/ci.yml. We can't run this script
# directly in Github since it's too slow for a single job.

# Run local bazel tests
bazel test --test_output=errors //...

# Install local maven artifacts.
util/install-local-snapshot.sh

# Run local mvn tests
pushd examples/maven && mvn compile && popd

# Run local gradle tests
util/run-local-gradle-tests.sh
util/run-local-gradle-android-tests.sh "8.1.1"

# TODO: this script is not up-to-date with Dagger github actions
