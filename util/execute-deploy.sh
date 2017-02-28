#!/bin/sh

VERSION_NAME=$1
REPOSITORY_ID=$2
REPOSITORY_URL=$3
EXTRA_MAVEN_ARGS=$4

python $(dirname $0)/maven/generate_poms.py $VERSION_NAME \
  //core/src/main/java/dagger:core \
  //compiler:compiler \
  //producers:producers \
  //java/dagger/android:android \
  //java/dagger/android/support:support \
  //java/dagger/android/processor:processor

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
  mvn gpg:sign-and-deploy-file \
    -Dfile=$(library_output_file $library) \
    -DrepositoryId=$REPOSITORY_ID \
    -Durl=$REPOSITORY_URL \
    -Djavadoc=bazel-genfiles/$javadoc \
    -DpomFile=$pomfile \
    -Dsources=bazel-bin/$srcjar \
    $EXTRA_MAVEN_ARGS
}

deploy_library \
  core/src/main/java/dagger/libcore.jar \
  core/src/main/java/dagger/libcore-src.jar \
  core/src/main/java/dagger/core-javadoc.jar \
  dagger.pom.xml

deploy_library \
  shaded_compiler.jar \
  compiler/libcompiler-src.jar \
  compiler/compiler-javadoc.jar \
  dagger-compiler.pom.xml

deploy_library \
  producers/libproducers.jar \
  producers/libproducers-src.jar \
  producers/producers-javadoc.jar \
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
  java/dagger/android/processor/libprocessor.jar \
  java/dagger/android/processor/libprocessor-src.jar \
  java/dagger/android/processor/processor-javadoc.jar \
  dagger-android-processor.pom.xml
