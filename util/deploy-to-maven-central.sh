#!/bin/bash

set -eu

if [ $# -lt 2 ]; then
  echo "usage $0 <ssl-key> <version-name> [<param> ...]"
  exit 1;
fi
key=$1
version_name=$2
shift 2

if [[ ! $version_name =~ ^2\. ]]; then
  echo 'Version name must begin with "2."'
  exit 2
fi

#validate key
keystatus=$(gpg --list-keys | grep ${key} | awk '{print $1}')
if [ "${keystatus}" != "pub" ]; then
  echo "Could not find public key with label ${key}"
  echo -n "Available keys from: "
  gpg --list-keys | grep --invert-match '^sub'

  exit 64
fi

sh $(dirname $0)/execute-deploy.sh \
  "$version_name" \
  "sonatype-nexus-staging" \
  "https://oss.sonatype.org/service/local/staging/deploy/maven2/" \
  "-Dgpg.keyname=${key}"

# Publish javadocs to gh-pages
bazel build //:user-docs.jar
git clone --quiet --branch gh-pages \
    https://github.com/google/dagger gh-pages > /dev/null
cd gh-pages
unzip ../bazel-genfiles/user-docs.jar -d api/$version_name
git add api/$version_name
git commit -m "$version_name docs"
git push origin gh-pages
cd ..
rm -rf gh-pages
