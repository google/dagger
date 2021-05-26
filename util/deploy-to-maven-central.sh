#!/bin/bash

set -eu

if [ $# -lt 2 ]; then
  echo "usage $0 <ssl-key> <version-name> [<param> ...]"
  exit 1;
fi
readonly KEY=$1
readonly VERSION_NAME=$2
shift 2

if [[ ! "$VERSION_NAME" =~ ^2\. ]]; then
  echo 'Version name must begin with "2."'
  exit 2
fi

if [[ "$VERSION_NAME" =~ " " ]]; then
  echo "Version name must not have any spaces"
  exit 3
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

# Set the version string that is used as a tag in all of our libraries. If
# another repo depends on a versioned tag of Dagger, their java_library.tags
# should match the versioned release.
sed -i s/'${project.version}'/"${VERSION_NAME}"/g build_defs.bzl

# Note: We avoid commiting until after deploying in case deploying fails and
# we need to run the script again.
git commit -m "${VERSION_NAME} release" build_defs.bzl
git tag -a -m "Dagger ${VERSION_NAME}" dagger-"${VERSION_NAME}"
git push origin tag dagger-"${VERSION_NAME}"

# Switch back to the original HEAD
git checkout -

# Publish javadocs to gh-pages
bazel build //:user-docs.jar
git clone --quiet --branch gh-pages \
    https://github.com/google/dagger gh-pages > /dev/null
cd gh-pages
unzip ../bazel-bin/user-docs.jar -d api/$VERSION_NAME
rm -rf api/$VERSION_NAME/META-INF/
git add api/$VERSION_NAME
git commit -m "$VERSION_NAME docs"
git push origin gh-pages
cd ..
rm -rf gh-pages
