#!/bin/bash

set -eu

readonly MVN_GOAL="$1"
readonly VERSION_NAME="$2"
shift 2
readonly EXTRA_MAVEN_ARGS=("$@")

# Builds and deploy the Gradle plugin.
_deploy_plugin() {
  local plugindir=java/dagger/hilt/android/plugin
  ./$plugindir/gradlew -p $plugindir --no-daemon clean \
    publishAllPublicationsToMavenRepository -PPublishVersion="$VERSION_NAME"
  local outdir=$plugindir/main/build/repo/com/google/dagger/hilt-android-gradle-plugin/$VERSION_NAME
  local markerOutDir=$plugindir/main/build/repo/com/google/dagger/hilt/android/com.google.dagger.hilt.android.gradle.plugin/$VERSION_NAME
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
  mvn "$MVN_GOAL" \
    -Dfile="$(find $markerOutDir -name "*-$suffix.pom")" \
    -DpomFile="$(find $markerOutDir -name "*-$suffix.pom")" \
    "${EXTRA_MAVEN_ARGS[@]:+${EXTRA_MAVEN_ARGS[@]}}"
}

# Gradle Plugin is built with Gradle, but still deployed via Maven (mvn)
_deploy_plugin
