#!/bin/bash
# Copyright (C) 2019 The Dagger Authors.
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

fail() {
  local message="${1:-failed}"
  echo "${message} at $(caller)" >&2
  return 1
}

. "tools/simple_jar.sh"

[[ "$(find_dirname some/package some/package/a/b/c some/package/d/e/f)" \
    == "some/package" ]] \
  || fail "no prefix"

[[ "$(find_dirname some/package prefix/some/package/a prefix/some/package/b)" \
    == "prefix/some/package" ]] \
  || fail "with prefix"

! find_dirname some/package some/package/a other/package/b >/dev/null \
  || fail "expected failure if one file is in the wrong package"

! find_dirname some/package other/package/a other/package/b >/dev/null \
  || fail "expected failure if all files are in the wrong package"

! find_dirname some/package some/package/a prefix/some/package/b >/dev/null \
  || fail "expected failure if files have different prefixes"

! find_dirname some/package >/dev/null \
  || fail "expected failure with no sources"

true
