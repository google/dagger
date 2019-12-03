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
#
# Description:
#   Functional test code for Dagger-Android

load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kt_jvm_library")
load("//:test_defs.bzl", "GenJavaTests")

package(default_visibility = ["//:src"])

kt_jvm_library(
    name = "kotlin",
    srcs = glob(
        [
            "*.java",
            "*.kt",
        ],
        exclude = [
            "*Test.java",
            "JavaTestQualifier.java",
            "FooWithInjectedQualifier.kt",
        ],
    ),
    deps = [
        ":foo_with_injected_qualifier",
        ":java_qualifier",
        "//:dagger_with_compiler",
    ],
)

kt_jvm_library(
    name = "foo_with_injected_qualifier",
    srcs = ["FooWithInjectedQualifier.kt"],
    deps = [
        ":java_qualifier",
        "//:dagger_with_compiler",
    ],
)

java_library(
    name = "java_qualifier",
    srcs = ["JavaTestQualifier.java"],
    deps = [
        "//:dagger_with_compiler",
    ],
)

GenJavaTests(
    name = "kotlin_tests",
    srcs = glob(["*Test.java"]),
    functional = True,
    test_only_deps = [
        "//java/dagger/internal/guava:annotations",
        "//java/dagger/internal/guava:base",
        "//java/dagger/internal/guava:collect",
        "@google_bazel_common//third_party/java/junit",
        "@google_bazel_common//third_party/java/truth",
    ],
    deps = [
        ":foo_with_injected_qualifier",
        ":kotlin",
        "//:dagger_with_compiler",
    ],
)
