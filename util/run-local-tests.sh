#!/bin/bash

set -ex

readonly TEST_PARAMS="$@"

# Run tests with bazel
bazel test $TEST_PARAMS //...

# Install into local maven.
util/install-local-snapshot.sh

pushd examples/maven && mvn compile && popd

readonly GRADLE_PROJECTS=(
    "java/dagger/example/gradle/simple"
    "java/dagger/hilt/android/plugin"
    "javatests/artifacts/dagger/simple"
)
for project in "${GRADLE_PROJECTS[@]}"; do
    echo "Running gradle tests for $project"
    ./$project/gradlew -p $project build --no-daemon --stacktrace
    ./$project/gradlew -p $project test --no-daemon --stacktrace
done


# Run gradle tests with different versions of Android Gradle Plugin
# At least latest stable and upcoming versions, this list can't be too long
# or else we timeout CI job.
readonly AGP_VERSIONS=("4.2.0-beta01" "4.1.0")
readonly ANDROID_GRADLE_PROJECTS=(
    "java/dagger/example/gradle/android/simple"
    "javatests/artifacts/dagger-android/simple"
    "javatests/artifacts/hilt-android/simple"
    "javatests/artifacts/hilt-android/simpleKotlin"
)
for version in "${AGP_VERSIONS[@]}"; do
    for project in "${ANDROID_GRADLE_PROJECTS[@]}"; do
        echo "Running gradle tests for $project with AGP $version"
        AGP_VERSION=$version ./$project/gradlew -p $project buildDebug --no-daemon --stacktrace
        AGP_VERSION=$version ./$project/gradlew -p $project testDebug --no-daemon --stacktrace
    done
done

# Run gradle tests in a project with configuration cache enabled
# TODO(user): Once AGP 4.2.0 is stable, remove these project and enable
# config cache in the other test projects.
readonly CONFIG_CACHE_PROJECT="javatests/artifacts/hilt-android/gradleConfigCache"
./$CONFIG_CACHE_PROJECT/gradlew -p $CONFIG_CACHE_PROJECT assembleDebug --no-daemon --stacktrace --configuration-cache

verify_version_file() {
  local m2_repo=$1
  local group_path=com/google/dagger
  local artifact_id=$2
  local type=$3
  local version="LOCAL-SNAPSHOT"
  local temp_dir=$(mktemp -d)
  local content
  if [ $type = "jar" ]; then
    unzip $m2_repo/$group_path/$artifact_id/$version/$artifact_id-$version.jar \
      META-INF/com.google.dagger_$artifact_id.version \
      -d $temp_dir
  elif [ $type = "aar" ]; then
    unzip $m2_repo/$group_path/$artifact_id/$version/$artifact_id-$version.aar \
      classes.jar \
      -d $temp_dir
    unzip $temp_dir/classes.jar \
      META-INF/com.google.dagger_$artifact_id.version \
      -d $temp_dir
  fi
  local content=$(cat $temp_dir/META-INF/com.google.dagger_${artifact_id}.version)
  if [[ $content != $version ]]; then
    echo "Version file failed verification for artifact: $artifact_id"
    exit 1
  fi
}

# Verify tracking version file
readonly LOCAL_REPO=$(mvn help:evaluate \
  -Dexpression=settings.localRepository -q -DforceStdout)
verify_version_file $LOCAL_REPO "dagger" "jar"
verify_version_file $LOCAL_REPO "dagger-android" "aar"
