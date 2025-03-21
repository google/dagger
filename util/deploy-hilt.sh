#!/bin/bash

set -eu

readonly MVN_GOAL="$1"
readonly VERSION_NAME="$2"
shift 2
readonly EXTRA_MAVEN_ARGS=("$@")

function shaded_rule() {
  echo "$1,dagger.spi.internal.shaded.$1"
}
readonly _SHADED_RULES="\
$(shaded_rule com.google.auto.common);\
$(shaded_rule androidx.room);\
$(shaded_rule kotlin.metadata);\
$(shaded_rule com.squareup.kotlinpoet.javapoet)"

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
  hilt-android/artifact.aar \
  hilt-android/pom.xml \
  hilt-android/artifact-src.jar \
  hilt-android/artifact-javadoc.jar \
  ""

_deploy \
  "" \
  hilt-android-testing/artifact.aar \
  hilt-android-testing/pom.xml \
  hilt-android-testing/artifact-src.jar \
  hilt-android-testing/artifact-javadoc.jar \
  ""

_deploy \
  "$_SHADED_RULES" \
  hilt-compiler/artifact.jar \
  hilt-compiler/pom.xml \
  hilt-compiler/artifact-src.jar \
  hilt-compiler/artifact-javadoc.jar \
  ""

_deploy \
  "$_SHADED_RULES" \
  hilt-compiler/legacy-artifact.jar \
  hilt-compiler/legacy-pom.xml \
  hilt-compiler/legacy-artifact-src.jar \
  hilt-compiler/legacy-artifact-javadoc.jar \
  ""

_deploy \
  "" \
  hilt-core/artifact.jar \
  hilt-core/pom.xml \
  hilt-core/artifact-src.jar \
  hilt-core/artifact-javadoc.jar \
  ""
