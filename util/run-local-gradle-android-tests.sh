#!/bin/bash

set -ex

readonly AGP_VERSION_INPUT=$1
readonly GRADLE_PROJECT=$2

echo "Running gradle tests for $GRADLE_PROJECT with AGP $AGP_VERSION_INPUT"
# Enable config cache if AGP is 4.2.0 or greater.
# Note that this is a lexicographical comparison.
if [[ "$AGP_VERSION_INPUT" > "4.1.0" ]]
then
  CONFIG_CACHE_ARG="--configuration-cache"
else
  CONFIG_CACHE_ARG=""
fi
AGP_VERSION=$AGP_VERSION_INPUT ./$GRADLE_PROJECT/gradlew -p $GRADLE_PROJECT buildDebug --no-daemon --stacktrace $CONFIG_CACHE_ARG
AGP_VERSION=$AGP_VERSION_INPUT ./$GRADLE_PROJECT/gradlew -p $GRADLE_PROJECT testDebug  --continue --no-daemon --stacktrace $CONFIG_CACHE_ARG
