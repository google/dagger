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
    ./$project/gradlew -p $project connectedAndroidTest --continue --no-daemon --stacktrace --configuration-cache
done

# Close logcat
if [ -n "$LOGCAT_PID" ] ; then kill $LOGCAT_PID; fi
