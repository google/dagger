# Copyright (C) 2022 The Dagger Authors.
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
#   Functional tests for Dagger basic usages

load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
)
load("//:test_defs.bzl", "GenJavaTests")

package(default_visibility = ["//:src"])

GenJavaTests(
    name = "basic",
    srcs = glob(
        ["*.java"],
    ),
    gen_library_deps = [
        "//javatests/dagger/functional/basic/subpackage",
    ],
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    deps = [
        "//:dagger_with_compiler",
        "//third_party/java/auto:factory",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/util/concurrent",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)
