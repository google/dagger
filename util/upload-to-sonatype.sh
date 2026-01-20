#!/bin/bash
# Uploads a staged repository to Sonatype.
#
# Usage (must have 'SONATYPE_TOKEN' variable set in its environment):
#   upload-to-sonatype.sh

set -eu

upload_to_sonatype() {
  local staging_url="https://ossrh-staging-api.central.sonatype.com/manual"
  local header="Authorization: Bearer $SONATYPE_TOKEN"

  # Query the REST API for the existing Dagger repositories.
  local response=$(curl -s --header "$header" -X GET $staging_url/search/repositories?ip=any&profile_id=com.google.dagger)

  # Filter to get the list of open repositories.
  local open_repositories=$(echo $response | jq '.repositories | map(select(.state == "open"))')
  local open_repositories_count=$(echo $open_repositories | jq length)

  # Fail if there is not exactly 1 open repository.
  if [[ $open_repositories_count -eq 0 ]]; then
    echo "No open repositories found."
    exit 1
  elif [[ $open_repositories_count -gt 1 ]]; then
    echo "Expected 1 open repository but found multiple: $(echo $open_repositories | jq)."
    echo "Please drop all unused open repositories and try again."
    exit 1
  fi

  # Now that we know there is exactly one open repository, get its key.
  local repository_key=$(echo $open_repositories | jq -r '.[0].key')

  # Finally, upload the staged repository to the Sonatype Publisher Portal.
  curl --header "$header" -X POST $staging_url/upload/repository/$repository_key
}

upload_to_sonatype