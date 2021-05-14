#!/bin/bash

set -ex

# Instrumentation tests log results to logcat, so enable it during test runs.
adb logcat *:S TestRunner:V & LOGCAT_PID=$!

readonly GRADLE_PROJECTS=(
    "javatests/artifacts/hilt-android/simple"
    "javatests/artifacts/hilt-android/simpleKotlin"
)
for project in "${GRADLE_PROJECTS[@]}"; do
    echo "Running gradle Android emulator tests for $project"
    ./$project/gradlew -p $project connectedAndroidTest --continue --no-daemon --stacktrace
done

# Run emulator tests in a project with configuration cache enabled
# TODO(danysantiago): Once AGP 4.2.0 is stable, remove these project and enable
# config cache in the other test projects.
readonly CONFIG_CACHE_PROJECT="javatests/artifacts/hilt-android/gradleConfigCache"
./$CONFIG_CACHE_PROJECT/gradlew -p $CONFIG_CACHE_PROJECT connectedAndroidTest --continue --no-daemon --stacktrace --configuration-cache

# Close logcat
if [ -n "$LOGCAT_PID" ] ; then kill $LOGCAT_PID; fi
