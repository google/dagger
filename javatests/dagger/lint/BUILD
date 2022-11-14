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
#   Tests for the Dagger Lint Rules

load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kt_jvm_test")

package(default_visibility = ["//:src"])

kt_jvm_test(
    name = "DaggerKotlinIssueDetectorTest",
    srcs = ["DaggerKotlinIssueDetectorTest.kt"],
    deps = [
        "//java/dagger/lint:lint-artifact-lib",
        "//third_party/java/junit",
        "@maven//:com_android_tools_lint_lint_checks",
        "@maven//:com_android_tools_lint_lint_tests",
        "@maven//:com_android_tools_testutils",
        "@maven//:org_jetbrains_kotlin_kotlin_stdlib",
    ],
)
