#!/bin/bash

set -ex

readonly JAVA_VERSION_INPUT=$1
readonly GRADLE_PROJECTS=(
    "java/dagger/example/gradle/simple"
    "java/dagger/hilt/android/plugin"
    "javatests/artifacts/dagger/simple"
)
for project in "${GRADLE_PROJECTS[@]}"; do
    echo "Running gradle tests for $project with JAVA $JAVA_VERSION_INPUT"
    JAVA_VERSION=$JAVA_VERSION_INPUT ./$project/gradlew -p $project build --no-daemon --stacktrace
    JAVA_VERSION=$JAVA_VERSION_INPUT ./$project/gradlew -p $project test --no-daemon --stacktrace
done
