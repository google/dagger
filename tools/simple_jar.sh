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

set -e

find_dirname() {
  local package_name="$1"
  shift

  local src src_dirname dirname=""
  for src in "$@"; do
    if [[ ! "${src}" =~ ^((.*/)?"${package_name}")/ ]]; then
      echo "Sources must be in ${package_name}: $0" >&2
      return 1
    fi
    src_dirname="${BASH_REMATCH[1]}"
    if [[ "${dirname:=${src_dirname}}" != "${src_dirname}" ]]; then
      echo "Sources must all be in the same directory: $@" >&2
      return 1
    fi
  done

  if [[ -z "${dirname}" ]]; then
    echo "No sources provided" >&2
    return 1
  fi

  echo "${dirname}"
}

main() {
  local package_name="$1"
  local out="${PWD}/$2"
  shift 2

  local dirname
  dirname="$(find_dirname "${package_name}" "$@")"
  cd "${dirname}"
  zip "${out}" -r * &> /dev/null
}

if [[ -z "$TEST_SRCDIR" ]]; then
  main "$@"
fi
