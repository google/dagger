#!/bin/bash

set -eu

echo -e "Installing maven...\n"

readonly VERSION="$1"

curl https://archive.apache.org/dist/maven/maven-3/${VERSION}/binaries/apache-maven-${VERSION}-bin.tar.gz --output apache-maven-${VERSION}-bin.tar.gz
tar xvf apache-maven-${VERSION}-bin.tar.gz -C /opt

echo -e ">>>>> $PATH\m"

export PATH="/opt/apache-maven-${VERSION}/bin:${PATH}"

echo -e ">>>> maven version: $(mvn --version)"