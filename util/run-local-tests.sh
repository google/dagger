#!/bin/bash

set -ex

readonly TEST_PARAMS="$@"

# Run tests with bazel
bazel test $TEST_PARAMS //...

# Also run the gradle examples on the local maven snapshots.
readonly _SIMPLE_EXAMPLE_DIR=java/dagger/example/gradle/simple
readonly _ANDROID_EXAMPLE_DIR=java/dagger/example/gradle/android/simple

util/install-local-snapshot.sh
./$_SIMPLE_EXAMPLE_DIR/gradlew -p $_SIMPLE_EXAMPLE_DIR build --stacktrace
./$_ANDROID_EXAMPLE_DIR/gradlew -p $_ANDROID_EXAMPLE_DIR build --stacktrace

