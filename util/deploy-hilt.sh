#!/bin/bash

set -eu

readonly MVN_GOAL="$1"
readonly VERSION_NAME="$2"
shift 2
readonly EXTRA_MAVEN_ARGS=("$@")

# Builds and deploys the given artifacts to a configured maven goal.
# @param {string} library the library to deploy.
# @param {string} pomfile the pom file to deploy.
# @param {string} srcjar the sources jar of the library. This is an optional
# parameter, if provided then javadoc must also be provided.
# @param {string} javadoc the java doc jar of the library. This is an optional
# parameter, if provided then srcjar must also be provided.
_deploy() {
  local library=$1
  local pomfile=$2
  local srcjar=$3
  local javadoc=$4
  bash $(dirname $0)/deploy-library.sh \
      "$library" \
      "$pomfile" \
      "$srcjar" \
      "$javadoc" \
      "$MVN_GOAL" \
      "$VERSION_NAME" \
      "${EXTRA_MAVEN_ARGS[@]:+${EXTRA_MAVEN_ARGS[@]}}"
}

_deploy \
  java/dagger/hilt/android/artifact.jar \
  java/dagger/hilt/android/pom.xml \
  java/dagger/hilt/android/artifact-src.jar \
  java/dagger/hilt/android/artifact-javadoc.jar

_deploy \
  java/dagger/hilt/android/testing/artifact.jar \
  java/dagger/hilt/android/testing/pom.xml \
  java/dagger/hilt/android/testing/artifact-src.jar \
  java/dagger/hilt/android/testing/artifact-javadoc.jar

_deploy \
  java/dagger/hilt/android/processor/artifact.jar \
  java/dagger/hilt/android/processor/pom.xml \
  java/dagger/hilt/android/processor/artifact-src.jar \
  java/dagger/hilt/android/processor/artifact-javadoc.jar

# Gradle Plugin is built with Gradle, but still deployed via Maven (mvn)
readonly _HILT_GRADLE_PLUGIN_DIR=java/dagger/hilt/android/plugin
./$_HILT_GRADLE_PLUGIN_DIR/gradlew -p $_HILT_GRADLE_PLUGIN_DIR --no-daemon \
  jar generatePomFileForPluginPublication -PPublishVersion="$VERSION_NAME"
mvn "$MVN_GOAL" \
  -Dfile=$_HILT_GRADLE_PLUGIN_DIR/build/libs/plugin.jar \
  -DpomFile=$_HILT_GRADLE_PLUGIN_DIR/build/publications/plugin/pom-default.xml \
  "${extra_maven_args[@]:+${extra_maven_args[@]}}"