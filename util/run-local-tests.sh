#!/bin/bash

set -ex


# Run local bazel tests
bazel test --test_output=errors //...

# Install local maven artifacts.
util/install-local-snapshot.sh

# Run local mvn tests
pushd examples/maven && mvn compile && popd

# Run local gradle tests
util/run-local-gradle-tests.sh
util/run-local-gradle-android-tests.sh "4.1.0"
util/run-local-gradle-android-tests.sh "4.2.0-beta01"

