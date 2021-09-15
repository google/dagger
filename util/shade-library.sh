#!/bin/bash

set -eux

readonly INPUT_JAR=$1
readonly SHADE_RULES=$2

_shade_libary() {
  local shader=$(dirname $0)/../tools/shader
  local output="${INPUT_JAR%.*}-shaded.${INPUT_JAR##*.}"

  ./$shader/gradlew -p $shader shadowJar \
      -PinputJar="../../$INPUT_JAR" \
      -PshadedRules=$SHADE_RULES

  # Copy the shaded jar to the specified output
  cp $shader/build/libs/shader.jar $output
}

_shade_libary
