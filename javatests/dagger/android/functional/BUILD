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
#   Functional test code for Dagger-Android

package(default_visibility = ["//:src"])

load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
)
load("//:test_defs.bzl", "GenRobolectricTests")

# Sample code to be tested in functional tests.
android_library(
    name = "code_under_test",
    srcs = glob(
        ["*.java"],
        exclude = ["*Test.java"],
    ),
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    deps = [
        # NOTE: This should not depend on Guava or jsr305 to ensure that Dagger can be
        # used without a Guava and jsr305 deps.
        "//:dagger_with_compiler",
        "//:android",
    ],
)

GenRobolectricTests(
    name = "android_functional_tests",
    srcs = glob(["*Test.java"]),
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    deps = [
        ":code_under_test",
        "//third_party:guava",
        "//third_party:guava_testlib",
        "//third_party:junit",
        "//third_party:truth",
    ],
)
