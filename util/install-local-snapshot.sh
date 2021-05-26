#!/bin/bash

set -eu

echo -e "Installing maven snapshot locally...\n"

bash $(dirname $0)/deploy-all.sh \
  "install:install-file" \
  "LOCAL-SNAPSHOT"

echo -e "Installed local snapshot"

verify_version_file() {
  local m2_repo=$(mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout)
  local group_path=com/google/dagger
  local artifact_id=$1
  local type=$2
  local version="LOCAL-SNAPSHOT"
  local temp_dir=$(mktemp -d)
  local content
  if [ $type = "jar" ]; then
    unzip $m2_repo/$group_path/$artifact_id/$version/$artifact_id-$version.jar \
      META-INF/com.google.dagger_$artifact_id.version \
      -d $temp_dir
  elif [ $type = "aar" ]; then
    unzip $m2_repo/$group_path/$artifact_id/$version/$artifact_id-$version.aar \
      classes.jar \
      -d $temp_dir
    unzip $temp_dir/classes.jar \
      META-INF/com.google.dagger_$artifact_id.version \
      -d $temp_dir
  fi
  local content=$(cat $temp_dir/META-INF/com.google.dagger_${artifact_id}.version)
  if [[ $content != $version ]]; then
    echo "Version file failed verification for artifact: $artifact_id"
    exit 1
  fi
}

# Verify tracking version file in Dagger and Dagger Android
verify_version_file "dagger" "jar"
verify_version_file "dagger-android" "aar"
