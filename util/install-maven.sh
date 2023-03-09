#!/bin/bash

set -eux

function install-maven-version {
  local VERSION=$1

  if [[ ! "$VERSION" =~ ^3\. ]]; then
    echo 'Version must begin with "3."'
    exit 2
  fi

  pushd "$(mktemp -d)"
  # Download the maven version
  curl https://archive.apache.org/dist/maven/maven-3/${VERSION}/binaries/apache-maven-${VERSION}-bin.tar.gz --output apache-maven-${VERSION}-bin.tar.gz

  # Unzip the contents to the /usr/share/ directory
  sudo tar xvf apache-maven-${VERSION}-bin.tar.gz -C /usr/share/
  popd

  # Replace old symlink with new one
  sudo unlink /usr/bin/mvn
  sudo ln -s /usr/share/apache-maven-${VERSION}/bin/mvn /usr/bin/mvn
}

if [ $# -lt 1 ]; then
  echo "usage $0 <version>"
  exit 1;
fi

install-maven-version $1


