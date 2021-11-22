#!/bin/bash

set -ex

readonly GRADLE_PROJECT=$1

# Instrumentation tests log results to logcat, so enable it during test runs.
adb logcat *:S TestRunner:V & LOGCAT_PID=$!

echo "Running gradle Android emulator tests for $GRADLE_PROJECT"
./$GRADLE_PROJECT/gradlew -p $GRADLE_PROJECT connectedAndroidTest --continue --no-daemon --stacktrace --configuration-cache

# Close logcat
if [ -n "$LOGCAT_PID" ] ; then kill $LOGCAT_PID; fi
