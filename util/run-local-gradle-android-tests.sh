#!/bin/bash

set -ex

readonly AGP_VERSION_INPUT=$1
readonly ANDROID_GRADLE_PROJECTS=(
    "javatests/artifacts/dagger-android/simple"
    "javatests/artifacts/hilt-android/simple"
    "javatests/artifacts/hilt-android/simpleKotlin"
)
for project in "${ANDROID_GRADLE_PROJECTS[@]}"; do
    echo "Running gradle tests for $project with AGP $AGP_VERSION_INPUT"
    AGP_VERSION=$AGP_VERSION_INPUT ./$project/gradlew -p $project assembleDebug --no-daemon --stacktrace --configuration-cache
    AGP_VERSION=$AGP_VERSION_INPUT ./$project/gradlew -p $project testDebug  --continue --no-daemon --stacktrace --configuration-cache
done
