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
#   Public Dagger API for Android that interacts with the Android support libraries

load("//:build_defs.bzl", "SOURCE_7_TARGET_7")
load("//tools:maven.bzl", "POM_VERSION", "pom_file")
load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")

package(default_visibility = ["//:src"])

filegroup(
    name = "support-srcs",
    srcs = glob(["*.java"]),
)

android_library(
    name = "support",
    srcs = glob(["*.java"]),
    javacopts = SOURCE_7_TARGET_7,
    manifest = "AndroidManifest.xml",
    tags = ["maven_coordinates=com.google.dagger:dagger-android-support:" + POM_VERSION],
    deps = [
        "//:dagger_with_compiler",
        "//java/dagger/android",
        "@google_bazel_common//third_party/java/error_prone:annotations",
        "@maven//:com_android_support_appcompat_v7",
        "@maven//:com_android_support_support_annotations",
        "@maven//:com_android_support_support_fragment",
    ],
)

pom_file(
    name = "pom",
    artifact_id = "dagger-android-support",
    artifact_name = "Dagger Android Support",
    packaging = "aar",
    targets = [":support"],
)

javadoc_library(
    name = "support-javadoc",
    srcs = [":support-srcs"],
    android_api_level = 29,
    root_packages = ["dagger.android.support"],
    deps = [":support"],
)
