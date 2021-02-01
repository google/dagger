# Copyright (C) 2021 The Dagger Authors.
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
#   Tests for internal code for implementing Hilt processors.

load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kt_jvm_library")
load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX", "SOURCE_7_TARGET_7")
load("//:test_defs.bzl", "GenJavaTests")

package(default_visibility = ["//:src"])

kt_jvm_library(
    name = "KotlinAssistedInjectionClasses",
    srcs = ["KotlinAssistedInjectionClasses.kt"],
    deps = [
        "//:dagger_with_compiler",
    ],
)

GenJavaTests(
    name = "kotlin",
    srcs = glob(
        ["*.java"],
        exclude = ["*.kt"],
    ),
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    lib_javacopts = SOURCE_7_TARGET_7,
    test_only_deps = [
        "//:dagger_with_compiler",
        "@google_bazel_common//third_party/java/junit",
        "@google_bazel_common//third_party/java/truth",
    ],
    # NOTE: This should not depend on Guava or jsr305 to ensure that Dagger can be
    # used without Guava and jsr305 deps.
    deps = [
        ":KotlinAssistedInjectionClasses",
    ],
)
