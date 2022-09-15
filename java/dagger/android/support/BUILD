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

load(
    "//:build_defs.bzl",
    "JAVA_RELEASE_MIN",
    "POM_VERSION",
)
load("//tools:dejetify.bzl", "dejetified_library")
load("//tools:maven.bzl", "pom_file")
load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")

package(default_visibility = ["//:src"])

filegroup(
    name = "support-srcs",
    srcs = glob(["*.java"]),
)

android_library(
    name = "support",
    srcs = glob(["*.java"]),
    javacopts = JAVA_RELEASE_MIN,
    manifest = "AndroidManifest.xml",
    tags = ["maven_coordinates=com.google.dagger:dagger-android-support:" + POM_VERSION],
    deps = [
        "//:dagger_with_compiler",
        "//java/dagger/android",
        "//third_party/java/error_prone:annotations",
        "@maven//:androidx_activity_activity",
        "@maven//:androidx_annotation_annotation",
        "@maven//:androidx_appcompat_appcompat",
        "@maven//:androidx_fragment_fragment",
        "@maven//:androidx_lifecycle_lifecycle_common",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
    ],
)

pom_file(
    name = "pom",
    artifact_id = "dagger-android-support",
    artifact_name = "Dagger Android Support",
    packaging = "aar",
    targets = [":support"],
)

dejetified_library(
    name = "dejetified-support",
    input = ":support.aar",
    output = "support-legacy.aar",
)

android_library(
    name = "legacy-deps",
    tags = ["maven_coordinates=com.google.dagger:dagger-android-support-legacy:" + POM_VERSION],
    exports = [
        "//:dagger_with_compiler",
        "//java/dagger/android:legacy-deps",
        "//third_party/java/error_prone:annotations",
        "@maven//:com_android_support_appcompat_v7",
        "@maven//:com_android_support_support_annotations",
        "@maven//:com_android_support_support_fragment",
    ],
)

pom_file(
    name = "legacy-pom",
    artifact_id = "dagger-android-support-legacy",
    artifact_name = "Dagger Android Legacy Support",
    packaging = "aar",
    targets = [":legacy-deps"],
)

javadoc_library(
    name = "support-javadoc",
    srcs = [":support-srcs"],
    android_api_level = 32,
    root_packages = ["dagger.android.support"],
    deps = [":support"],
)
