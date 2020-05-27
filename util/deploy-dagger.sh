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
  java/dagger/libcore.jar \
  java/dagger/pom.xml \
  java/dagger/libcore-src.jar \
  java/dagger/core-javadoc.jar

_deploy \
  gwt/libgwt.jar \
  gwt/pom.xml \
  gwt/libgwt.jar \
  gwt/libgwt.jar

_deploy \
  java/dagger/internal/codegen/artifact.jar \
  java/dagger/internal/codegen/pom.xml \
  java/dagger/internal/codegen/artifact-src.jar \
  java/dagger/internal/codegen/artifact-javadoc.jar

_deploy \
  java/dagger/producers/artifact.jar \
  java/dagger/producers/pom.xml \
  java/dagger/producers/artifact-src.jar \
  java/dagger/producers/artifact-javadoc.jar

_deploy \
  java/dagger/spi/artifact.jar \
  java/dagger/spi/pom.xml \
  java/dagger/spi/artifact-src.jar \
  java/dagger/spi/artifact-javadoc.jar

_deploy \
  java/dagger/android/android.aar \
  java/dagger/android/pom.xml \
  java/dagger/android/libandroid-src.jar \
  java/dagger/android/android-javadoc.jar

_deploy \
  java/dagger/android/android-legacy.aar \
  java/dagger/android/legacy-pom.xml \
  "" \
  ""

# b/37741866 and https://github.com/google/dagger/issues/715
_deploy \
  java/dagger/android/libandroid.jar \
  java/dagger/android/jarimpl-pom.xml \
  java/dagger/android/libandroid-src.jar \
  java/dagger/android/android-javadoc.jar

_deploy \
  java/dagger/android/support/support.aar \
  java/dagger/android/support/pom.xml \
  java/dagger/android/support/libsupport-src.jar \
  java/dagger/android/support/support-javadoc.jar

_deploy \
  java/dagger/android/support/support-legacy.aar \
  java/dagger/android/support/legacy-pom.xml \
  "" \
  ""

_deploy \
  shaded_android_processor.jar \
  java/dagger/android/processor/pom.xml \
  java/dagger/android/processor/libprocessor-src.jar \
  java/dagger/android/processor/processor-javadoc.jar

_deploy \
  java/dagger/grpc/server/libserver.jar \
  java/dagger/grpc/server/server-pom.xml \
  java/dagger/grpc/server/libserver-src.jar \
  java/dagger/grpc/server/javadoc.jar

_deploy \
  java/dagger/grpc/server/libannotations.jar \
  java/dagger/grpc/server/annotations-pom.xml \
  java/dagger/grpc/server/libannotations-src.jar \
  java/dagger/grpc/server/javadoc.jar

_deploy \
  shaded_grpc_server_processor.jar \
  java/dagger/grpc/server/processor/pom.xml \
  java/dagger/grpc/server/processor/libprocessor-src.jar \
  java/dagger/grpc/server/processor/javadoc.jar

_deploy \
  java/dagger/lint/lint-artifact.jar \
  java/dagger/lint/lint-pom.xml \
  java/dagger/lint/lint-artifact-src.jar \
  java/dagger/lint/lint-artifact-javadoc.jar

_deploy \
  java/dagger/lint/lint-android-artifact.aar \
  java/dagger/lint/lint-android-pom.xml \
  "" \
  ""
