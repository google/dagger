#!/usr/bin/env bash

# Copyright (C) 2017 The Dagger Authors.
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


export JAVA_HOME=$(readlink -f $1)

set -eu

JAR_BINARY="$(readlink external/local_jdk/bin/jar)"
JAVA_BINARY="$(readlink external/local_jdk/bin/java)"

report_bad_output() {
  $JAR_BINARY tf output.jar
  echo $1
  exit 1
}

ROOT=$TEST_SRCDIR/$TEST_WORKSPACE

cd $TEST_TMPDIR

echo "rule foo.** baz.@1" > rules_file

$ROOT/tools/jarjar_library_impl.sh \
  "output.jar" \
  "$ROOT/tools/testdata/libfoo.jar $ROOT/tools/testdata/libbar.jar" \
  "rules_file" \
  "$ROOT/tools/jarjar_deploy.jar" \
  "$JAR_BINARY" \
  "$JAVA_BINARY" \
  "."
if $JAR_BINARY  tf output.jar | grep -F -q foo/Foo.class; then
  report_bad_output "Expected foo/Foo.class to be renamed to baz/Foo.class"
elif ! $JAR_BINARY tf output.jar | grep -F -q baz/Foo.class; then
  report_bad_output "Expected baz/Foo.class to be in the output jar"
fi

if $ROOT/tools/jarjar_library_impl.sh \
  "output.jar" \
  "$ROOT/tools/testdata/libfoo_with_dupe_file.jar $ROOT/tools/testdata/libbar_with_dupe_file.jar" \
  "rules_file" \
  "$ROOT/tools/jarjar_deploy.jar" \
  "$JAR_BINARY" \
  "$JAVA_BINARY" \
  "."; then
  report_bad_output 'Expected duplicate file "dupe"'
fi
