#!/bin/bash

set -eu

bash $(dirname $0)/deploy-dagger.sh "$@"

bash $(dirname $0)/deploy-hilt.sh "$@"

bash $(dirname $0)/deploy-hilt-gradle-plugin.sh "$@"