#!/bin/bash

set -ex

readonly GRADLE_PROJECTS=(
    "java/dagger/hilt/android/plugin"
    "javatests/artifacts/dagger"
)
for project in "${GRADLE_PROJECTS[@]}"; do
    echo "Running gradle tests for $project"
    ./$project/gradlew -p $project build --stacktrace
    ./$project/gradlew -p $project test  --continue --stacktrace
done
