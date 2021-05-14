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

load("//:test_defs.bzl", "GenRobolectricTests")

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
        "@maven//:androidx_activity_activity",
        "@maven//:androidx_fragment_fragment",
        "@maven//:androidx_lifecycle_lifecycle_common",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
        "@maven//:androidx_appcompat_appcompat",
        "@maven//:androidx_annotation_annotation",
        "//:dagger_with_compiler",
        "//:android",
        "//:android-support",
        # TODO(ronshapiro): figure out why strict deps is failing without this
        "@google_bazel_common//third_party/java/jsr250_annotations",
    ],
)

GenRobolectricTests(
    name = "functional_tests",
    srcs = glob(["*Test.java"]),
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        ":functional",
        "//:android",
        "//:android-support",
        "//:dagger_with_compiler",
        "@google_bazel_common//third_party/java/truth",
        "@maven//:androidx_activity_activity",
        "@maven//:androidx_fragment_fragment",
        "@maven//:androidx_lifecycle_lifecycle_common",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
        "@maven//:androidx_test_core",
        "@maven//:androidx_test_ext_junit",
        "@maven//:junit_junit",
        "@maven//:org_robolectric_robolectric",
    ],
)
