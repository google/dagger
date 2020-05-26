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
  java/dagger/hilt/android/artifact.aar \
  java/dagger/hilt/android/pom.xml \
  java/dagger/hilt/android/artifact-src.jar \
  java/dagger/hilt/android/artifact-javadoc.jar

_deploy \
  java/dagger/hilt/android/testing/artifact.aar \
  java/dagger/hilt/android/testing/pom.xml \
  java/dagger/hilt/android/testing/artifact-src.jar \
  java/dagger/hilt/android/testing/artifact-javadoc.jar

_deploy \
  java/dagger/hilt/android/processor/artifact.jar \
  java/dagger/hilt/android/processor/pom.xml \
  java/dagger/hilt/android/processor/artifact-src.jar \
  java/dagger/hilt/android/processor/artifact-javadoc.jar

# Builds and deploy the Gradle plugin.
_deploy_plugin() {
  local plugindir=java/dagger/hilt/android/plugin
  ./$plugindir/gradlew -p $plugindir --no-daemon clean \
    publishAllPublicationsToMavenRepository -PPublishVersion="$VERSION_NAME"
  local outdir=$plugindir/build/repo/com/google/dagger/hilt-android-gradle-plugin/$VERSION_NAME
  # When building '-SNAPSHOT' versions in gradle, the filenames replaces
  # '-SNAPSHOT' with timestamps, so we need to disambiguate by finding each file
  # to deploy. See: https://stackoverflow.com/questions/54182823/
  local suffix
  if [[ "$VERSION_NAME" == *"-SNAPSHOT" ]]; then
    # Gets the timestamp part out of the name to be used as suffix.
    # Timestamp format is ########.######-#.
    suffix=$(find $outdir -name "*.pom" | grep -Eo '[0-9]{8}\.[0-9]{6}-[0-9]{1}')
  else
    suffix=$VERSION_NAME
  fi
  mvn "$MVN_GOAL" \
    -Dfile="$(find $outdir -name "*-$suffix.jar")" \
    -DpomFile="$(find $outdir -name "*-$suffix.pom")" \
    -Dsources="$(find $outdir -name "*-$suffix-sources.jar")" \
    -Djavadoc="$(find $outdir -name "*-$suffix-javadoc.jar")" \
    "${EXTRA_MAVEN_ARGS[@]:+${EXTRA_MAVEN_ARGS[@]}}"
}

# Gradle Plugin is built with Gradle, but still deployed via Maven (mvn)
_deploy_plugin
