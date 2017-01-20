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
#   Functional tests for Dagger Producers

package(default_visibility = ["//:src"])

load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
    "JAVA_7_SOURCE_LEVEL",
    "PRE_JAVA_8_INFERENCE_OPTS",
)

# Sample code to be tested in functional tests.
java_library(
    name = "code_under_test",
    srcs = glob(["src/main/java/**/*.java"]),
    javacopts = PRE_JAVA_8_INFERENCE_OPTS + JAVA_7_SOURCE_LEVEL + DOCLINT_HTML_AND_SYNTAX,
    deps = [
        "//:producers_with_compiler",
        "//third_party:auto_value",
        "//third_party:guava",
        "//third_party:jsr330_inject",
    ],
)

load("//:test_defs.bzl", "GenJavaTests")

GenJavaTests(
    name = "producers-functional-tests",
    srcs = glob(["src/test/java/**/*.java"]),
    javacopts = PRE_JAVA_8_INFERENCE_OPTS + JAVA_7_SOURCE_LEVEL + DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    deps = [
        ":code_under_test",
        "//:producers_with_compiler",
        "//third_party:guava",
        "//third_party:jsr330_inject",
        "//third_party:junit",
        "//third_party:mockito",
        "//third_party:truth",
    ],
)

test_suite(name = "AllTests")
