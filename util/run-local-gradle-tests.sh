#!/bin/bash

set -ex

readonly GRADLE_PROJECT=$1

echo "Running gradle tests for $GRADLE_PROJECT"
./$GRADLE_PROJECT/gradlew -p $GRADLE_PROJECT build --no-daemon --stacktrace
./$GRADLE_PROJECT/gradlew -p $GRADLE_PROJECT test  --continue --no-daemon --stacktrace
