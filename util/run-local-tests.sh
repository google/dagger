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
util/run-local-gradle-tests.sh "java/dagger/hilt/android/plugin"
util/run-local-gradle-tests.sh "javatests/artifacts/dagger/simple"
util/run-local-gradle-tests.sh "javatests/artifacts/dagger/simpleKotlin"

util/run-local-gradle-android-tests.sh "javatests/artifacts/dagger-android/simple" "4.1.0"
util/run-local-gradle-android-tests.sh "javatests/artifacts/hilt-android/simple" "4.2.0"
util/run-local-gradle-android-tests.sh "javatests/artifacts/hilt-android/simpleKotlin" "7.0.0"


