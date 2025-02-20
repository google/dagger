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
  "com.google.auto.common,dagger.spi.internal.shaded.auto.common;androidx.room.compiler,dagger.spi.internal.shaded.androidx.room.compiler;kotlin.metadata,dagger.spi.internal.shaded.kotlin.metadata;androidx.room,dagger.spi.internal.shaded.androidx.room" \
  java/dagger/internal/codegen/artifact.jar \
  java/dagger/internal/codegen/pom.xml \
  java/dagger/internal/codegen/artifact-src.jar \
  java/dagger/internal/codegen/artifact-javadoc.jar \
  ""

_deploy \
  "" \
  java/dagger/producers/artifact.jar \
  java/dagger/producers/pom.xml \
  java/dagger/producers/artifact-src.jar \
  java/dagger/producers/artifact-javadoc.jar \
  ""

_deploy \
  "com.google.auto.common,dagger.spi.internal.shaded.auto.common;androidx.room.compiler,dagger.spi.internal.shaded.androidx.room.compiler;kotlin.metadata,dagger.spi.internal.shaded.kotlin.metadata;androidx.room,dagger.spi.internal.shaded.androidx.room" \
  java/dagger/spi/artifact.jar \
  java/dagger/spi/pom.xml \
  java/dagger/spi/artifact-src.jar \
  java/dagger/spi/artifact-javadoc.jar \
  ""

_deploy \
  "" \
  java/dagger/android/artifact.aar \
  java/dagger/android/pom.xml \
  java/dagger/android/artifact-src.jar \
  java/dagger/android/artifact-javadoc.jar \
  ""

_deploy \
  "" \
  java/dagger/android/android-legacy.aar \
  java/dagger/android/legacy-pom.xml \
  "" \
  "" \
  ""

_deploy \
  "" \
  java/dagger/android/support/artifact.aar \
  java/dagger/android/support/pom.xml \
  java/dagger/android/support/artifact-src.jar \
  java/dagger/android/support/artifact-javadoc.jar \
  ""

_deploy \
  "" \
  java/dagger/android/support/support-legacy.aar \
  java/dagger/android/support/legacy-pom.xml \
  "" \
  "" \
  ""

_deploy \
  "com.google.auto.common,dagger.spi.internal.shaded.auto.common;androidx.room.compiler,dagger.spi.internal.shaded.androidx.room.compiler;kotlin.metadata,dagger.spi.internal.shaded.kotlin.metadata;androidx.room,dagger.spi.internal.shaded.androidx.room" \
  java/dagger/android/processor/artifact.jar \
  java/dagger/android/processor/pom.xml \
  java/dagger/android/processor/artifact-src.jar \
  java/dagger/android/processor/artifact-javadoc.jar \
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
  "" \
  shaded_grpc_server_processor.jar \
  java/dagger/grpc/server/processor/pom.xml \
  java/dagger/grpc/server/processor/libprocessor-src.jar \
  java/dagger/grpc/server/processor/javadoc.jar \
  ""

_deploy \
  "" \
  java/dagger/lint/lint-artifact.jar \
  java/dagger/lint/lint-pom.xml \
  java/dagger/lint/lint-artifact-src.jar \
  java/dagger/lint/lint-artifact-javadoc.jar \
  ""

_deploy \
  "" \
  java/dagger/lint/lint-android-artifact.aar \
  java/dagger/lint/lint-android-pom.xml \
  "" \
  "" \
  ""
