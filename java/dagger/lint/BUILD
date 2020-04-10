# Copyright (C) 2020 The Dagger Authors.
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
#   Source for Dagger lints

load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kt_jvm_library")
load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX")

package(default_visibility = ["//:src"])

java_library(
    name = "lint_api",
    neverlink = True,
    exports = [
        "@google_bazel_common//third_party/java/auto:service",
        "@maven//:com_android_tools_lint_lint",
        "@maven//:com_android_tools_lint_lint_api",
        "@maven//:com_android_tools_external_org_jetbrains_uast",
        "@maven//:com_android_tools_external_com_intellij_intellij_core",
        "@maven//:com_android_tools_external_com_intellij_kotlin_compiler",
    ]
)

kt_jvm_library(
    name = "dagger_lint",
    srcs = [
        "DaggerKotlinIssuesDetector.kt",
        "DaggerIssueRegistry.kt",
    ],
    plugins = [
        "@google_bazel_common//third_party/java/auto:service",
    ],
    deps = [
        ":lint_api",
        "@maven//:org_jetbrains_kotlin_kotlin_stdlib",
    ]
)
