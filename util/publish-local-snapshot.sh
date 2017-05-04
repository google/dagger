#!/bin/bash

set -eu

echo -e "Publishing maven snapshot locally...\n"

bash $(dirname $0)/execute-deploy.sh \
  "install:install-file" \
  "LOCAL-SNAPSHOT"

echo -e "Published local snapshot"
