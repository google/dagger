#!/bin/bash

set -ex

readonly GRADLE_PROJECTS=(
    "java/dagger/hilt/android/plugin"
    "javatests/artifacts/dagger/simple"
    "javatests/artifacts/dagger/simpleKotlin"
)
for project in "${GRADLE_PROJECTS[@]}"; do
    echo "Running gradle tests for $project"
    ./$project/gradlew -p $project build --no-daemon --stacktrace
    ./$project/gradlew -p $project test  --continue --no-daemon --stacktrace
done
