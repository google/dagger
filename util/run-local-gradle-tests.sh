#!/bin/bash

set -ex

readonly GRADLE_PROJECTS=(
    "javatests/artifacts/dagger"
    "javatests/artifacts/dagger-ksp"
)
for project in "${GRADLE_PROJECTS[@]}"; do
    echo "Running gradle tests for $project"
    ./$project/gradlew -p $project build --no-daemon --stacktrace
    ./$project/gradlew -p $project test  --continue --no-daemon --stacktrace
done
