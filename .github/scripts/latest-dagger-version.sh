#!/bin/bash

set -eux

function github-rest-api {
  local GITHUB_REST_API=$1

  local GITHUB_API_HEADER_ACCEPT="Accept: application/vnd.github.v3+json"

  if [ -z "$GH_TOKEN" ]; then
    curl -s $GITHUB_REST_API -H $GITHUB_API_HEADER_ACCEPT
  else
    curl -s $GITHUB_REST_API \
      -H $GITHUB_API_HEADER_ACCEPT \
      -H "authorization: Bearer $GH_TOKEN"
  fi
}

function github-latest-release-tag {
  local REPO_NAME=$1

  # Grab the last two latest releases:
  # (We skip the latest release if we haven't set release notes yet).
  local RELEASE_API="https://api.github.com/repos/$REPO_NAME/releases?per_page=2"

  # This gets the latest release info (as json) from github.
  local RELEASE_JSON=$(github-rest-api $RELEASE_API)

  # This pulls out the "body" from the json (i.e. the release notes)
  local RELEASE_NOTES=$(echo $RELEASE_JSON | jq '.[0].body')

  if [ "$RELEASE_NOTES" ]
  then
    # Return the latest release tag
    echo $RELEASE_JSON | jq '.[0].tag_name'
  else
    # If there are no release notes in the latest release then we use the
    # 2nd most latest version since we don't want to update the version until
    # the release notes are set.
    echo "Ignoring the latest release since the release notes have not been set."
    echo "Using the previous release's version as latest."

    # Return the 2nd most recent release tag
    echo $RELEASE_JSON | jq '.[1].tag_name'
  fi
}

function dagger-latest-release {
  # Get the latest Dagger release tag, e.g. "dagger-2.31.2" or "dagger-2.32"
  local DAGGER_RELEASE_TAG=$(github-latest-release-tag "google/dagger")

  # Converts the "tag_name" to a version, e.g. "dagger-2.32" => "2.32"
  echo $DAGGER_RELEASE_TAG | grep -oP "(?<=dagger-)\d+\.\d+(\.\d+)?"
}

dagger-latest-release
