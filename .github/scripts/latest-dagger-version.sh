#!/bin/bash

set -eux

function github-get {
  local GITHUB_REST_API=$1

  local GITHUB_API_HEADER_ACCEPT="Accept: application/vnd.github.v3+json"

  curl -s $GITHUB_REST_API -H "Accept: application/vnd.github.v3+json"
}

function github-dagger-latest {
  local DAGGER_LATEST_API="https://api.github.com/repos/google/dagger/releases/latest"

  # This gets the Dagger latest release info (as json) from github.
  local DAGGER_LATEST_JSON=$(github-get $DAGGER_LATEST_API)

  # This pulls out the "tag_version" from the json, e.g. "dagger-2.31.2"
  local DAGGER_TAG=$(echo $DAGGER_LATEST_JSON | jq '.tag_name')

  # Then it converts the dagger tag to a version, e.g. "2.31.2"
  local DAGGER_VERSION=$(echo $DAGGER_TAG | grep -oP "(?<=dagger-)\d+\.\d+(\.\d+)?")

  echo $DAGGER_VERSION
}

github-dagger-latest

