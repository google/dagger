#!/bin/sh

MVN_GOAL=$1
VERSION_NAME=$2
REPOSITORY_ID=$3
REPOSITORY_URL=$4
EXTRA_MAVEN_ARGS=$5

python $(dirname $0)/maven/generate_poms.py $VERSION_NAME \
  //java/dagger:core \
  //java/dagger/internal/codegen:codegen \
  //java/dagger/producers:producers \
  //java/dagger/android:android \
  //java/dagger/android/support:support \
  //java/dagger/android/processor:processor \
  //java/dagger/grpc/server:server \
  //java/dagger/grpc/server:annotations \
  //java/dagger/grpc/server/processor:processor

library_output_file() {
  library=$1
  library_output=bazel-bin/$library
  if [[ ! -e $library_output ]]; then
     library_output=bazel-genfiles/$library
  fi
  if [[ ! -e $library_output ]]; then
    echo "Could not find bazel output file for $library"
    exit 1
  fi
  echo -n $library_output
}

deploy_library() {
  library=$1
  srcjar=$2
  javadoc=$3
  pomfile=$4
  bazel build $library $srcjar $javadoc
  mvn $MVN_GOAL \
    -Dfile=$(library_output_file $library) \
    -DrepositoryId=$REPOSITORY_ID \
    -Durl=$REPOSITORY_URL \
    -Djavadoc=bazel-bin/$javadoc \
    -DpomFile=$pomfile \
    -Dsources=bazel-bin/$srcjar \
    $EXTRA_MAVEN_ARGS
}

deploy_library \
  java/dagger/libcore.jar \
  java/dagger/libcore-src.jar \
  java/dagger/core-javadoc.jar \
  dagger.pom.xml

deploy_library \
  shaded_compiler.jar \
  java/dagger/internal/codegen/libcodegen-src.jar \
  java/dagger/internal/codegen/codegen-javadoc.jar \
  dagger-compiler.pom.xml

deploy_library \
  java/dagger/producers/libproducers.jar \
  java/dagger/producers/libproducers-src.jar \
  java/dagger/producers/producers-javadoc.jar \
  dagger-producers.pom.xml

deploy_library \
  java/dagger/android/android.aar \
  java/dagger/android/libandroid-src.jar \
  java/dagger/android/android-javadoc.jar \
  dagger-android.pom.xml

deploy_library \
  java/dagger/android/support/support.aar \
  java/dagger/android/support/libsupport-src.jar \
  java/dagger/android/support/support-javadoc.jar \
  dagger-android-support.pom.xml

deploy_library \
  shaded_android_processor.jar \
  java/dagger/android/processor/libprocessor-src.jar \
  java/dagger/android/processor/processor-javadoc.jar \
  dagger-android-processor.pom.xml

deploy_library \
  java/dagger/grpc/server/libserver.jar \
  java/dagger/grpc/server/libserver-src.jar \
  java/dagger/grpc/server/javadoc.jar \
  dagger-grpc-server.pom.xml

deploy_library \
  java/dagger/grpc/server/libannotations.jar \
  java/dagger/grpc/server/libannotations-src.jar \
  java/dagger/grpc/server/javadoc.jar \
  dagger-grpc-server-annotations.pom.xml

deploy_library \
  shaded_grpc_server_processor.jar \
  java/dagger/grpc/server/processor/libprocessor-src.jar \
  java/dagger/grpc/server/processor/javadoc.jar \
  dagger-grpc-server-processor.pom.xml
