#!/bin/bash

set -ex

readonly TEST_PARAMS="$@"

# Run tests with bazel
bazel test $TEST_PARAMS //...

# Also run the gradle examples on the local maven snapshots.
readonly _SIMPLE_EXAMPLE_DIR=java/dagger/example/gradle/simple
readonly _ANDROID_EXAMPLE_DIR=java/dagger/example/gradle/android/simple

util/install-local-snapshot.sh
./$_SIMPLE_EXAMPLE_DIR/gradlew -p $_SIMPLE_EXAMPLE_DIR build --stacktrace
./$_ANDROID_EXAMPLE_DIR/gradlew -p $_ANDROID_EXAMPLE_DIR build --stacktrace


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
