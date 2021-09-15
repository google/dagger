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
    # Enable config cache if AGP is 4.2.0 or greater.
    # Note that this is a lexicographical comparison.
    if [[ "$AGP_VERSION_INPUT" > "4.1.0" ]]
    then
      CONFIG_CACHE_ARG="--configuration-cache"
    else
      CONFIG_CACHE_ARG=""
    fi
    AGP_VERSION=$AGP_VERSION_INPUT ./$project/gradlew -p $project buildDebug --no-daemon --stacktrace $CONFIG_CACHE_ARG
    AGP_VERSION=$AGP_VERSION_INPUT ./$project/gradlew -p $project testDebug  --continue --no-daemon --stacktrace $CONFIG_CACHE_ARG
done
