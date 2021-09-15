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
# @param {string} module_name the JPMS module name to include in the jar. This
# is an optional parameter and can only be used with jar files.
_deploy() {
  local shaded_rules=$1
  local library=$2
  local pomfile=$3
  local srcjar=$4
  local javadoc=$5
  local module_name=$6
  bash $(dirname $0)/deploy-library.sh \
      "$shaded_rules" \
      "$library" \
      "$pomfile" \
      "$srcjar" \
      "$javadoc" \
      "$module_name" \
      "$MVN_GOAL" \
      "$VERSION_NAME" \
      "${EXTRA_MAVEN_ARGS[@]:+${EXTRA_MAVEN_ARGS[@]}}"
}

_deploy \
  "" \
  java/dagger/hilt/android/artifact.aar \
  java/dagger/hilt/android/pom.xml \
  java/dagger/hilt/android/artifact-src.jar \
  java/dagger/hilt/android/artifact-javadoc.jar \
  ""

_deploy \
  "" \
  java/dagger/hilt/android/testing/artifact.aar \
  java/dagger/hilt/android/testing/pom.xml \
  java/dagger/hilt/android/testing/artifact-src.jar \
  java/dagger/hilt/android/testing/artifact-javadoc.jar \
  ""

_deploy \
  "com.google.auto.common,dagger.hilt.android.shaded.auto.common" \
  java/dagger/hilt/processor/artifact.jar \
  java/dagger/hilt/processor/pom.xml \
  java/dagger/hilt/processor/artifact-src.jar \
  java/dagger/hilt/processor/artifact-javadoc.jar \
  ""

_deploy \
  "com.google.auto.common,dagger.hilt.android.shaded.auto.common" \
  java/dagger/hilt/android/processor/artifact.jar \
  java/dagger/hilt/android/processor/pom.xml \
  java/dagger/hilt/android/processor/artifact-src.jar \
  java/dagger/hilt/android/processor/artifact-javadoc.jar \
  ""

_deploy \
  "" \
  java/dagger/hilt/artifact-core.jar \
  java/dagger/hilt/pom.xml \
  java/dagger/hilt/artifact-core-src.jar \
  java/dagger/hilt/artifact-core-javadoc.jar \
  ""
