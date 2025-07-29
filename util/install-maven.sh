#!/bin/bash

set -eux

function install-maven-version {
  local version=$1

  if [[ ! "$version" =~ ^3\. ]]; then
    echo 'Version must begin with "3."'
    exit 2
  fi

  local current_version=$(mvn --version | grep 'Apache Maven [0-9.]*' | cut -d' ' -f3)
  if [[ "$current_version" == "$version" ]]; then
    echo "Maven version $version is already installed."
    exit 0
  fi

  pushd "$(mktemp -d)"
  # Download the maven version. This call sometimes fails, so we allow a few retries.
  curl https://archive.apache.org/dist/maven/maven-3/${version}/binaries/apache-maven-${version}-bin.tar.gz \
    --output apache-maven-${version}-bin.tar.gz \
    --retry 5 \
    --retry-delay 1 \
    --retry-max-time 40

  # Unzip the contents to the /usr/share/ directory
  sudo tar xvf apache-maven-${version}-bin.tar.gz -C /usr/share/
  popd

  # Replace old symlink with new one
  sudo unlink /usr/bin/mvn
  sudo ln -s /usr/share/apache-maven-${version}/bin/mvn /usr/bin/mvn
}

if [ $# -lt 1 ]; then
  echo "usage $0 <version>"
  exit 1;
fi

install-maven-version $1


