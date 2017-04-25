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

# Description:
#   Functional tests for Dagger

package(default_visibility = ["//:src"])

load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "SOURCE_7_TARGET_7",
)
load("//:test_defs.bzl", "GenJavaTests")

GenJavaTests(
    name = "functional_tests",
    srcs = glob(
        ["**/*.java"],
    ),
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    lib_javacopts = SOURCE_7_TARGET_7,
    # NOTE: This should not depend on Guava or jsr305 to ensure that Dagger can be
    # used without Guava and jsr305 deps.
    test_only_deps = [
        "//third_party:guava_testlib",
        "//third_party:guava",
        "//third_party:truth",
        "//third_party:junit",
    ],
    deps = [
        "//:dagger_with_compiler",
        "//third_party:auto_factory",
        "//third_party:auto_value",
        "//third_party:jsr330_inject",
    ],
)
