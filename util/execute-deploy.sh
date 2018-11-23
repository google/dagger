#!/bin/bash

set -eu

readonly MVN_GOAL="$1"
readonly VERSION_NAME="$2"
shift 2
readonly EXTRA_MAVEN_ARGS=("$@")

bazel_output_file() {
  local library=$1
  local output_file=bazel-bin/$library
  if [[ ! -e $output_file ]]; then
     output_file=bazel-genfiles/$library
  fi
  if [[ ! -e $output_file ]]; then
    echo "Could not find bazel output file for $library"
    exit 1
  fi
  echo -n $output_file
}

deploy_library() {
  local library=$1
  local srcjar=$2
  local javadoc=$3
  local pomfile=$4
  bazel build --define=pom_version="$VERSION_NAME" \
    $library $srcjar $javadoc $pomfile

  mvn $MVN_GOAL \
    -Dfile=$(bazel_output_file $library) \
    -Djavadoc=$(bazel_output_file $javadoc) \
    -DpomFile=$(bazel_output_file $pomfile) \
    -Dsources=$(bazel_output_file $srcjar) \
    "${EXTRA_MAVEN_ARGS[@]:+${EXTRA_MAVEN_ARGS[@]}}"
}

deploy_library \
  java/dagger/libcore.jar \
  java/dagger/libcore-src.jar \
  java/dagger/core-javadoc.jar \
  java/dagger/pom.xml

deploy_library \
  gwt/libgwt.jar \
  gwt/libgwt.jar \
  gwt/libgwt.jar \
  gwt/pom.xml

deploy_library \
  shaded_compiler.jar \
  shaded_compiler_src.jar \
  java/dagger/internal/codegen/codegen-javadoc.jar \
  java/dagger/internal/codegen/pom.xml

deploy_library \
  java/dagger/producers/libproducers.jar \
  java/dagger/producers/libproducers-src.jar \
  java/dagger/producers/producers-javadoc.jar \
  java/dagger/producers/pom.xml

deploy_library \
  shaded_spi.jar \
  shaded_spi_src.jar \
  spi-javadoc.jar \
  java/dagger/spi/pom.xml

deploy_library \
  java/dagger/android/android.aar \
  java/dagger/android/libandroid-src.jar \
  java/dagger/android/android-javadoc.jar \
  java/dagger/android/pom.xml

# b/37741866 and https://github.com/google/dagger/issues/715
deploy_library \
  java/dagger/android/libandroid.jar \
  java/dagger/android/libandroid-src.jar \
  java/dagger/android/android-javadoc.jar \
  java/dagger/android/jarimpl-pom.xml

deploy_library \
  java/dagger/android/support/support.aar \
  java/dagger/android/support/libsupport-src.jar \
  java/dagger/android/support/support-javadoc.jar \
  java/dagger/android/support/pom.xml

deploy_library \
  shaded_android_processor.jar \
  java/dagger/android/processor/libprocessor-src.jar \
  java/dagger/android/processor/processor-javadoc.jar \
  java/dagger/android/processor/pom.xml

deploy_library \
  java/dagger/grpc/server/libserver.jar \
  java/dagger/grpc/server/libserver-src.jar \
  java/dagger/grpc/server/javadoc.jar \
  java/dagger/grpc/server/server-pom.xml

deploy_library \
  java/dagger/grpc/server/libannotations.jar \
  java/dagger/grpc/server/libannotations-src.jar \
  java/dagger/grpc/server/javadoc.jar \
  java/dagger/grpc/server/annotations-pom.xml

deploy_library \
  shaded_grpc_server_processor.jar \
  java/dagger/grpc/server/processor/libprocessor-src.jar \
  java/dagger/grpc/server/processor/javadoc.jar \
  java/dagger/grpc/server/processor/pom.xml
