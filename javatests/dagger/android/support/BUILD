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
#
# Description:
#   Tests for Dagger's Android and Support library integrations

load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX")
load("//:test_defs.bzl", "GenRobolectricTests")

package(default_visibility = ["//:src"])

GenRobolectricTests(
    name = "android-support-tests",
    srcs = glob(["*.java"]),
    functional = False,
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        "//:dagger_with_compiler",
        "//java/dagger/android",
        "//java/dagger/android/support",
        "//java/dagger/internal/guava:base",
        "//java/dagger/internal/guava:collect",
        "//java/dagger/internal/guava:concurrent",
        "@google_bazel_common//third_party/java/junit",
        "@google_bazel_common//third_party/java/truth",
        "@maven//:androidx_activity_activity",
        "@maven//:androidx_appcompat_appcompat",
        "@maven//:androidx_fragment_fragment",
        "@maven//:androidx_lifecycle_lifecycle_common",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
    ],
)
