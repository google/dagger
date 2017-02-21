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
  //java/dagger/android/support:support

deploy_library() {
  if [[ $1 == "--shaded" ]]; then
    library=$2
    library_output=bazel-genfiles/$library
    srcjar=$3
    shift 3
  else
    library=$1
    library_output=bazel-bin/$library
    srcjar="${library%.jar}-src.jar"
    shift 1
  fi
  javadoc=$1
  pomfile=$2
  bazel build $library $srcjar $javadoc
  mvn gpg:sign-and-deploy-file \
    -Dfile=$library_output \
    -DrepositoryId=$REPOSITORY_ID \
    -Durl=$REPOSITORY_URL \
    -Djavadoc=bazel-genfiles/$javadoc \
    -DpomFile=$pomfile \
    -Dsources=bazel-bin/$srcjar \
    $EXTRA_MAVEN_ARGS
}

deploy_library \
  core/src/main/java/dagger/libcore.jar \
  core/src/main/java/dagger/core-javadoc.jar \
  dagger.pom.xml

deploy_library \
  --shaded shaded_compiler.jar compiler/libcompiler-src.jar \
  compiler/compiler-javadoc.jar \
  dagger-compiler.pom.xml

deploy_library \
  producers/libproducers.jar \
  producers/producers-javadoc.jar \
  dagger-producers.pom.xml

deploy_library \
  java/dagger/android/libandroid.jar \
  java/dagger/android/android-javadoc.jar \
  dagger-android.pom.xml

deploy_library \
  java/dagger/android/support/libsupport.jar \
  java/dagger/android/support/support-javadoc.jar \
  dagger-android-support.pom.xml
