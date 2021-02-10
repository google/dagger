#!/bin/bash

set -eux

function yml-dagger-version {
  local YML_CONFIG=$1
  local DAGGER_VERSION_REGEX="(?<=daggerVersion:\s\")[^\"]*"

  grep -oP $DAGGER_VERSION_REGEX $YML_CONFIG
}

function yml-hilt-version {
  local YML_CONFIG=$1
  local HILT_VERSION_REGEX="(?<=hiltVersion:\s\")[^\"]*"

  grep -oP $HILT_VERSION_REGEX $YML_CONFIG
}

echo "Verifying Dagger version YML config matches latest:"

readonly YML_CONFIG=$1
readonly CWD=$(dirname $0)

readonly LATEST_DAGGER_VERSION=$($CWD/latest-dagger-version.sh)
readonly LATEST_HILT_VERSION="$LATEST_DAGGER_VERSION-alpha"

readonly YML_DAGGER_VERSION=$(yml-dagger-version $YML_CONFIG)
readonly YML_HILT_VERSION=$(yml-hilt-version $YML_CONFIG)

echo $LATEST_DAGGER_VERSION
echo $LATEST_HILT_VERSION
echo $YML_DAGGER_VERSION
echo $YML_HILT_VERSION

if [ $LATEST_DAGGER_VERSION != $YML_DAGGER_VERSION ]; then
  echo "The Dagger yml version does not match the latest Dagger version:"
  echo "    Latest Dagger version: $LATEST_DAGGER_VERSION"
  echo "       YML Dagger version: $YML_DAGGER_VERSION"
  exit 1
fi

if [ $LATEST_HILT_VERSION != $YML_HILT_VERSION ]; then
  echo "The Hilt yml version does not match the latest Hilt version:"
  echo "    Latest Hilt version: $LATEST_HILT_VERSION"
  echo "       YML Hilt version: $YML_HILT_VERSION"
  exit 1
fi

echo "Done! Dagger and Hilt versions up to date."
