#!/bin/bash

set -ex

readonly AGP_VERSION_INPUT=$1
readonly ANDROID_GRADLE_PROJECTS=(
    "java/dagger/example/gradle/android/simple"
    "javatests/artifacts/dagger-android/simple"
    "javatests/artifacts/hilt-android/simple"
    "javatests/artifacts/hilt-android/simpleKotlin"
)
for project in "${ANDROID_GRADLE_PROJECTS[@]}"; do
    echo "Running gradle tests for $project with AGP $AGP_VERSION_INPUT"
    AGP_VERSION=$AGP_VERSION_INPUT ./$project/gradlew -p $project buildDebug --no-daemon --stacktrace
    AGP_VERSION=$AGP_VERSION_INPUT ./$project/gradlew -p $project testDebug  --continue --no-daemon --stacktrace
done

# Run gradle tests in a project with configuration cache enabled
# TODO(danysantiago): Once AGP 4.2.0 is stable, remove these project and enable
# config cache in the other test projects.
readonly CONFIG_CACHE_PROJECT="javatests/artifacts/hilt-android/gradleConfigCache"
./$CONFIG_CACHE_PROJECT/gradlew -p $CONFIG_CACHE_PROJECT assembleDebug --no-daemon --stacktrace --configuration-cache
