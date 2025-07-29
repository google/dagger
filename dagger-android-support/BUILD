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

load("@rules_android//rules:rules.bzl", "android_library")
load("//:build_defs.bzl", "POM_VERSION")
load("//tools:dejetify.bzl", "dejetified_library")
load(
    "//tools/maven:maven.bzl",
    "dagger_pom_file",
    "gen_maven_artifact",
)

package(default_visibility = ["//:src"])

gen_maven_artifact(
    name = "artifact",
    artifact_coordinates = "com.google.dagger:dagger-android-support:" + POM_VERSION,
    artifact_name = "Dagger Android Support",
    artifact_target = "//dagger-android-support/main/java/dagger/android/support:support",
    artifact_target_maven_deps = [
        "androidx.activity:activity:aar",
        "androidx.annotation:annotation",
        "androidx.annotation:annotation-jvm",
        "androidx.appcompat:appcompat:aar",
        "androidx.fragment:fragment:aar",
        "androidx.lifecycle:lifecycle-common",
        "androidx.lifecycle:lifecycle-viewmodel:aar",
        "androidx.lifecycle:lifecycle-viewmodel-savedstate:aar",
        "com.google.dagger:dagger",
        "com.google.dagger:dagger-android",
    ],
    javadoc_android_api_level = 34,
    javadoc_root_packages = [
        "dagger.android.support",
    ],
    javadoc_srcs = ["//dagger-android-support/main/java/dagger/android/support:support-srcs"],
    manifest = "main/AndroidManifest.xml",
    packaging = "aar",
)

dejetified_library(
    name = "dejetified-support",
    input = ":artifact.aar",
    output = "support-legacy.aar",
)

android_library(
    name = "legacy-deps",
    tags = ["maven_coordinates=com.google.dagger:dagger-android-support-legacy:" + POM_VERSION],
    exports = [
        "//dagger-android:legacy-deps",
        "//third_party/java/dagger",
        "//third_party/java/error_prone:annotations",
        "@maven//:com_android_support_appcompat_v7",
        "@maven//:com_android_support_support_annotations",
        "@maven//:com_android_support_support_fragment",
    ],
)

dagger_pom_file(
    name = "legacy-pom",
    artifact_id = "dagger-android-support-legacy",
    artifact_name = "Dagger Android Legacy Support",
    packaging = "aar",
    targets = [":legacy-deps"],
)
