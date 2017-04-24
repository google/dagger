#!/bin/bash

set -eu

echo -e "Publishing maven snapshot locally...\n"

bash $(dirname $0)/execute-deploy.sh \
  "deploy:deploy-file" \
  "LOCAL-SNAPSHOT" \
  "local-repo" \
  "file://$HOME/.m2/repository"

echo -e "Published local snapshot"
