#!/bin/bash

set -eu

readonly MVN_GOAL="$1"
readonly VERSION_NAME="$2"
shift 2
readonly EXTRA_MAVEN_ARGS=("$@")
readonly _SHADED_RULES="com.google.auto.common,dagger.spi.internal.shaded.auto.common;androidx.room.compiler,dagger.spi.internal.shaded.androidx.room.compiler;kotlin.metadata,dagger.spi.internal.shaded.kotlin.metadata;androidx.room,dagger.spi.internal.shaded.androidx.room"

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
  dagger-runtime/artifact.jar \
  dagger-runtime/pom.xml \
  dagger-runtime/artifact-src.jar \
  dagger-runtime/artifact-javadoc.jar \
  "dagger"

_deploy \
  "" \
  gwt/libgwt.jar \
  gwt/pom.xml \
  gwt/libgwt.jar \
  gwt/libgwt.jar \
  ""

_deploy \
  "$_SHADED_RULES" \
  dagger-compiler/artifact.jar \
  dagger-compiler/pom.xml \
  dagger-compiler/artifact-src.jar \
  dagger-compiler/artifact-javadoc.jar \
  ""

_deploy \
  "" \
  dagger-producers/artifact.jar \
  dagger-producers/pom.xml \
  dagger-producers/artifact-src.jar \
  dagger-producers/artifact-javadoc.jar \
  ""

_deploy \
  "$_SHADED_RULES" \
  dagger-spi/artifact.jar \
  dagger-spi/pom.xml \
  dagger-spi/artifact-src.jar \
  dagger-spi/artifact-javadoc.jar \
  ""

_deploy \
  "" \
  dagger-android/artifact.aar \
  dagger-android/pom.xml \
  dagger-android/artifact-src.jar \
  dagger-android/artifact-javadoc.jar \
  ""

_deploy \
  "" \
  dagger-android/android-legacy.aar \
  dagger-android/legacy-pom.xml \
  "" \
  "" \
  ""

_deploy \
  "" \
  dagger-android-support/artifact.aar \
  dagger-android-support/pom.xml \
  dagger-android-support/artifact-src.jar \
  dagger-android-support/artifact-javadoc.jar \
  ""

_deploy \
  "" \
  dagger-android-support/support-legacy.aar \
  dagger-android-support/legacy-pom.xml \
  "" \
  "" \
  ""

_deploy \
  "$_SHADED_RULES" \
  dagger-android-processor/artifact.jar \
  dagger-android-processor/pom.xml \
  dagger-android-processor/artifact-src.jar \
  dagger-android-processor/artifact-javadoc.jar \
  ""

_deploy \
  "" \
  java/dagger/grpc/server/libserver.jar \
  java/dagger/grpc/server/server-pom.xml \
  java/dagger/grpc/server/libserver-src.jar \
  java/dagger/grpc/server/javadoc.jar \
  ""

_deploy \
  "" \
  java/dagger/grpc/server/libannotations.jar \
  java/dagger/grpc/server/annotations-pom.xml \
  java/dagger/grpc/server/libannotations-src.jar \
  java/dagger/grpc/server/javadoc.jar \
  ""

_deploy \
  "$_SHADED_RULES" \
  dagger-grpc-server-processor/artifact.jar \
  dagger-grpc-server-processor/pom.xml \
  dagger-grpc-server-processor/artifact-src.jar \
  dagger-grpc-server-processor/artifact-javadoc.jar \
  ""

_deploy \
  "" \
  dagger-lint/lint-artifact.jar \
  dagger-lint/lint-pom.xml \
  dagger-lint/lint-artifact-src.jar \
  dagger-lint/lint-artifact-javadoc.jar \
  ""

_deploy \
  "" \
  dagger-lint/lint-android-artifact.aar \
  dagger-lint/lint-android-pom.xml \
  "" \
  "" \
  ""
