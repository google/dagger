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
#   Functional tests for Dagger that depend on Guava

load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX")
load("//:test_defs.bzl", "GenJavaTests")

package(default_visibility = ["//:src"])

java_library(
    name = "optional_binding_components",
    srcs = ["OptionalBindingComponents.java"],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    deps = [
        "//:dagger_with_compiler",
        "//third_party/java/auto:value",
        "//third_party/java/jsr305_annotations",
    ],
)

# TODO(b/203233586): Replace with GenJavaTest
GenJavaTests(
    name = "OptionalBindingComponentsEmptyTest",
    srcs = ["OptionalBindingComponentsEmptyTest.java"],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    require_jdk7_syntax = False,
    test_only_deps = [
        ":optional_binding_components",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
    deps = [
        "//:dagger_with_compiler",
        "//third_party/java/auto:value",
        "//third_party/java/jsr305_annotations",
    ],
)

# TODO(b/203233586): Replace with GenJavaTest
GenJavaTests(
    name = "OptionalBindingComponentsPresentTest",
    srcs = ["OptionalBindingComponentsPresentTest.java"],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    require_jdk7_syntax = False,
    test_only_deps = [
        ":optional_binding_components",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
    deps = [
        "//:dagger_with_compiler",
        "//third_party/java/auto:value",
        "//third_party/java/jsr305_annotations",
    ],
)
