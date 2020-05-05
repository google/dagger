#!/bin/bash

set -eu

echo -e "Installing maven snapshot locally...\n"

bash $(dirname $0)/deploy-dagger.sh \
  "install:install-file" \
  "LOCAL-SNAPSHOT"

bash $(dirname $0)/deploy-hilt.sh \
  "install:install-file" \
  "LOCAL-SNAPSHOT"

echo -e "Installed local snapshot"
