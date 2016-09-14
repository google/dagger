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

# Rename snapshot version and sanity check that it doesn't exist in any poms
sed -i s/HEAD-SNAPSHOT/$version_name/g `find . -name pom.xml`
if [[ $(git grep HEAD-SNAPSHOT -- '*pom.xml') ]]; then
  echo "Snapshots found in poms!"
  exit 3
fi

#validate key
keystatus=$(gpg --list-keys | grep ${key} | awk '{print $1}')
if [ "${keystatus}" != "pub" ]; then
  echo "Could not find public key with label ${key}"
  echo -n "Available keys from: "
  gpg --list-keys | grep --invert-match '^sub'

  exit 64
fi

mvn "$@" -P '!examples' -P sonatype-oss-release \
    -Dgpg.skip=false -Dgpg.keyname=${key} \
    clean site:jar deploy

# Publish javadocs to gh-pages
mvn javadoc:aggregate -P!examples -DexcludePackageNames=*.internal
git clone --quiet --branch gh-pages \
    https://git@github.com/google/dagger gh-pages > /dev/null
cd gh-pages
cp -r ../target/site/apidocs api/$version_name
git add api/$version_name
git commit -m "$version_name docs"
git push origin gh-pages
cd ..
rm -rf gh-pages
