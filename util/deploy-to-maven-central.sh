#!/bin/bash

set -eu

if [ $# -lt 2 ]; then
  echo "usage $0 <ssl-key> <version-name> [<param> ...]"
  exit 1;
fi
readonly KEY=$1
readonly VERSION_NAME=$2
shift 2

$(dirname $0)/validate-dagger-version.sh "$VERSION_NAME"

BAZEL_VERSION=$(bazel --version)
if [[ $BAZEL_VERSION != *"5.3.2"* ]]; then
  echo "Must use Bazel version 5.3.2"
  exit 4
fi

if [[ -z "${ANDROID_HOME}" ]]; then
  echo "ANDROID_HOME environment variable must be set"
  exit 5
fi

bash $(dirname $0)/run-local-tests.sh

bash $(dirname $0)/deploy-all.sh \
  "gpg:sign-and-deploy-file" \
  "$VERSION_NAME" \
  "-DrepositoryId=sonatype-nexus-staging" \
  "-Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/" \
  "-Dgpg.keyname=${KEY}"

# Note: we detach from head before making any sed changes to avoid commiting
# a particular version to master.
git checkout --detach
bash $(dirname $0)/publish-tagged-release.sh $VERSION_NAME
# Switch back to the original HEAD
git checkout -

bash $(dirname $0)/publish-tagged-docs.sh $VERSION_NAME
