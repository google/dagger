#!/bin/bash
# Copyright (C) 2018 The Dagger Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

readonly PACKAGE_NAME="$1"
readonly OUT="${PWD}/$2"
shift 2

dirname=""
for src in "$@"; do
  src_dirname="$(echo "${src}" | grep -o -P "(.*/)?${PACKAGE_NAME}" | head -n1)"
  if [[ -z "${dirname}" ]]; then
    dirname="${src_dirname}"
  elif [[ "${dirname}" != "${src_dirname}" ]]; then
    echo "Sources must all be in the same directory: $@"
    exit 1
  fi
done

if [[ -z "${dirname}" ]]; then
  echo "No sources provided"
  exit 1
fi

cd "${dirname}"
zip "${OUT}" -r * &> /dev/null
