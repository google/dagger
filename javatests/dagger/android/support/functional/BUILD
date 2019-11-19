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
#   Functional test code for Dagger-Android

package(default_visibility = ["//:src"])

android_library(
    name = "functional",
    srcs = glob(
        ["*.java"],
        exclude = ["*Test.java"],
    ),
    exports_manifest = 1,
    manifest = "AndroidManifest.xml",
    resource_files = glob(["res/**"]),
    deps = [
        "@maven//:com_android_support_support_fragment",
        "@maven//:com_android_support_appcompat_v7",
        "@maven//:com_android_support_support_annotations",
        "//java/dagger/internal/guava:collect",
        "//:dagger_with_compiler",
        "//:android",
        "//:android-support",
        # TODO(ronshapiro): figure out why strict deps is failing without this
        "@google_bazel_common//third_party/java/jsr250_annotations",
    ],
)

load("//:test_defs.bzl", "GenRobolectricTests")

GenRobolectricTests(
    name = "functional_tests",
    srcs = glob(["*Test.java"]),
    deps = [
        ":functional",
        "//:android",
        "//:android-support",
        "//:dagger_with_compiler",
        "@google_bazel_common//third_party/java/junit",
        "@google_bazel_common//third_party/java/robolectric",
        "@google_bazel_common//third_party/java/truth",
        "@maven//:com_android_support_support_fragment",
    ],
)
