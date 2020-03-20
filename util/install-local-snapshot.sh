#!/bin/bash

set -eu

echo -e "Installing maven snapshot locally...\n"

bash $(dirname $0)/deploy-dagger.sh \
  "install:install-file" \
  "LOCAL-SNAPSHOT"

bash $(dirname $0)/deploy-hilt.sh \
  "install:install-file" \
  "LOCAL-SNAPSHOT"

readonly _HILT_GRADLE_PLUGIN_DIR=java/dagger/hilt/android/plugin
./$_HILT_GRADLE_PLUGIN_DIR/gradlew -p $_HILT_GRADLE_PLUGIN_DIR publishToMavenLocal

echo -e "Installed local snapshot"
