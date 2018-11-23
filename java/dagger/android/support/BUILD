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

package(default_visibility = ["//:src"])

load("//:build_defs.bzl", "SOURCE_7_TARGET_7")
load("//tools:maven.bzl", "pom_file", "POM_VERSION")

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
        ":manual-maven-deps",
        "//:dagger_with_compiler",
        "//java/dagger/android",
        "@google_bazel_common//third_party/java/error_prone:annotations",
    ],
)

# Our pom.xml generator does not have a way to add manual maven deps. This target exports the
# targets that don't have the necessary maven_coordinates tags.
android_library(
    name = "manual-maven-deps",
    tags = [
        "maven_coordinates=com.android.support:appcompat-v7:25.0.0",
        "maven_coordinates=com.android.support:support-annotations:25.0.0",
        "maven_coordinates=com.android.support:support-fragment:25.0.0",
    ],
    visibility = ["//visibility:private"],
    exports = [
        "@androidsdk//com.android.support:appcompat-v7-25.0.0",
        "@androidsdk//com.android.support:support-annotations-25.0.0",
        "@androidsdk//com.android.support:support-fragment-25.0.0",
    ],
)

pom_file(
    name = "pom",
    artifact_id = "dagger-android-support",
    artifact_name = "Dagger Android Support",
    packaging = "aar",
    targets = [":support"],
)

load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")

javadoc_library(
    name = "support-javadoc",
    srcs = [":support-srcs"],
    android_api_level = 26,
    root_packages = ["dagger.android.support"],
    deps = [":support"],
)
