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
#  TCK tests for Dagger

package(default_visibility = ["//:src"])

load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
    "PRE_JAVA_8_INFERENCE_OPTS",
)

# Sample code to be tested in functional tests.
java_library(
    name = "code_under_test",
    testonly = 1,
    srcs = glob(["src/main/java/**/*.java"]),
    javacopts = PRE_JAVA_8_INFERENCE_OPTS + DOCLINT_HTML_AND_SYNTAX + [
        "-Adagger.privateMemberValidation=warning",
        "-Adagger.staticMemberValidation=warning",
        "-Adagger.ignorePrivateAndStaticInjectionForComponent=enabled",
    ],
    deps = [
        "//:dagger_with_compiler",
        "//third_party:jsr330_inject",
        "//third_party:jsr330_inject_tck",
        "//third_party:junit",
    ],
)

load("//:test_defs.bzl", "GenJavaTests")

GenJavaTests(
    name = "tck_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    javacopts = PRE_JAVA_8_INFERENCE_OPTS + DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    deps = [
        ":code_under_test",
        "//:dagger_with_compiler",
        "//third_party:jsr330_inject",
        "//third_party:jsr330_inject_tck",
        "//third_party:junit",
    ],
)

test_suite(name = "AllTests")
