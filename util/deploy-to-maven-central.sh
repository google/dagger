#!/bin/bash

set -eu

if [ $# -lt 2 ]; then
  echo "usage $0 <ssl-key> <version-name> [<param> ...]"
  exit 1;
fi
key=$1
version_name=$2
shift 2

if [[ ! "$version_name" =~ ^2\. ]]; then
  echo 'Version name must begin with "2."'
  exit 2
fi

if [[ "$version_name" =~ " " ]]; then
  echo "Version name must not have any spaces"
  exit 3
fi

bazel test //...

bash $(dirname $0)/execute-deploy.sh \
  "gpg:sign-and-deploy-file" \
  "$version_name" \
  "-DrepositoryId=sonatype-nexus-staging" \
  "-Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/" \
  "-Dgpg.keyname=${key}"

# Publish javadocs to gh-pages
bazel build //:user-docs.jar
git clone --quiet --branch gh-pages \
    https://github.com/google/dagger gh-pages > /dev/null
cd gh-pages
unzip ../bazel-bin/user-docs.jar -d api/$version_name
rm -rf api/$version_name/META-INF/
git add api/$version_name
git commit -m "$version_name docs"
git push origin gh-pages
cd ..
rm -rf gh-pages

git checkout --detach
# Set the version string that is used as a tag in all of our libraries. If another repo depends on
# a versioned tag of Dagger, their java_library.tags should match the versioned release.
sed -i s/'${project.version}'/"${version_name}"/g tools/maven.bzl
git commit -m "${version_name} release" tools/maven.bzl

git tag -a -m "Dagger ${version_name}" dagger-"${version_name}"
git push origin tag dagger-"${version_name}"

# Switch back to the original HEAD
git checkout -
