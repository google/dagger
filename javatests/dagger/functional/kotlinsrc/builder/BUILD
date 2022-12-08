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
#   Functional tests for Dagger builders

load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX")
load("//:test_defs.bzl", "GenKtTests")

package(default_visibility = ["//:src"])

GenKtTests(
    name = "BuilderBindsInstanceParameterTest",
    srcs = ["BuilderBindsInstanceParameterTest.kt"],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    test_only_deps = [
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava:testlib",
        "//third_party/java/truth",
        "//third_party/java/junit",
        "//:dagger_with_compiler",
    ],
    deps = [],
)

GenKtTests(
    name = "BuilderTest",
    srcs = ["BuilderTest.kt"],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    test_only_deps = [
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava:testlib",
        "//third_party/java/truth",
        "//third_party/java/junit",
        "//:dagger_with_compiler",
    ],
    deps = [],
)

GenKtTests(
    name = "PrivateConstructorsTest",
    srcs = ["PrivateConstructorsTest.kt"],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    test_only_deps = [
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava:testlib",
        "//third_party/java/truth",
        "//third_party/java/junit",
        "//:dagger_with_compiler",
    ],
    deps = [],
)

GenKtTests(
    name = "BuildMethodCovariantReturnTest",
    srcs = ["BuildMethodCovariantReturnTest.kt"],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    test_only_deps = [
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava:testlib",
        "//third_party/java/truth",
        "//third_party/java/junit",
        "//:dagger_with_compiler",
    ],
    deps = [],
)

GenKtTests(
    name = "BuildMethodCovariantReturnInheritedTest",
    srcs = ["BuildMethodCovariantReturnInheritedTest.kt"],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    test_only_deps = [
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava:testlib",
        "//third_party/java/truth",
        "//third_party/java/junit",
        "//:dagger_with_compiler",
    ],
    deps = [],
)
