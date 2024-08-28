#!/bin/bash

set -eu

readonly M2_DAGGER_REPO=~/.m2/repository/com/google/dagger
readonly JDK8="52"

_validate_jar() {
  local artifact_id=$1
  local artifact_jar=$M2_DAGGER_REPO/$1/LOCAL-SNAPSHOT/$1-LOCAL-SNAPSHOT.$2
  local java_language_level=$3

  # Validate the java language level of the classes in the jar.
  python $(dirname $0)/validate-jar-language-level.py \
      $artifact_jar $java_language_level

  # Validate the package prefixes of the files in the jar.
  if [[ $artifact_id == "dagger-gwt" ]]; then
     python $(dirname $0)/validate-jar-entry-prefixes.py \
        $artifact_jar "dagger/,META-INF/,javax/inject/,jakarta/inject/"
  elif [[ $artifact_id == "hilt-android" ]]; then
     python $(dirname $0)/validate-jar-entry-prefixes.py \
        $artifact_jar "dagger/,META-INF/,hilt_aggregated_deps/"
  else
     python $(dirname $0)/validate-jar-entry-prefixes.py \
        $artifact_jar "dagger/,META-INF/"
  fi
}

# Dagger API artifacts
_validate_jar "dagger-gwt" "jar" $JDK8
_validate_jar "dagger" "jar" $JDK8
_validate_jar "dagger-android" "aar" $JDK8
_validate_jar "dagger-android-legacy" "aar" $JDK8
_validate_jar "dagger-android-support" "aar" $JDK8
_validate_jar "dagger-android-support-legacy" "aar" $JDK8
_validate_jar "dagger-producers" "jar" $JDK8
_validate_jar "dagger-grpc-server" "jar" $JDK8
_validate_jar "dagger-grpc-server-annotations" "jar" $JDK8
_validate_jar "dagger-lint" "jar" $JDK8
_validate_jar "dagger-lint-aar" "aar" $JDK8

# Hilt API artifacts
# TODO(bcorso): reenable hilt-android-gradle-plugin validation.
# _validate_jar "hilt-android-gradle-plugin" "jar" $JDK8
_validate_jar "hilt-core" "jar" $JDK8
_validate_jar "hilt-android" "aar" $JDK8
_validate_jar "hilt-android-testing" "aar" $JDK8

# Processor artifacts
_validate_jar "dagger-spi" "jar" $JDK8
_validate_jar "dagger-compiler" "jar" $JDK8
_validate_jar "dagger-android-processor" "jar" $JDK8
_validate_jar "dagger-grpc-server-processor" "jar" $JDK8
_validate_jar "hilt-compiler" "jar" $JDK8
_validate_jar "hilt-android-compiler" "jar" $JDK8