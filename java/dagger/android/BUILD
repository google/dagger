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

# Description:
#   Public Dagger API for Android

load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
    "JAVA_RELEASE_MIN",
    "POM_VERSION",
)
load("//tools:dejetify.bzl", "dejetified_library")
load("//tools:maven.bzl", "pom_file")
load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")

package(default_visibility = ["//:src"])

# Work around b/70476182 which prevents Kythe from connecting :producers to the .java files it
# contains.
SRCS = glob([
    "*.java",
    "internal/*.java",
])

filegroup(
    name = "android-srcs",
    srcs = SRCS,
)

android_library(
    name = "android",
    srcs = SRCS,
    javacopts = JAVA_RELEASE_MIN + DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    manifest = "AndroidManifest.xml",
    plugins = [
        "//java/dagger/android/internal/proguard:plugin",
    ],
    tags = ["maven_coordinates=com.google.dagger:dagger-android:" + POM_VERSION],
    exports = [
        "//java/dagger/lint:lint-android-artifact-lib",
    ],
    deps = [
        "//:dagger_with_compiler",
        "//third_party/java/auto:value",
        "//third_party/java/error_prone:annotations",
        "@maven//:androidx_annotation_annotation",
    ],
)

pom_file(
    name = "pom",
    artifact_id = "dagger-android",
    artifact_name = "Dagger Android",
    packaging = "aar",
    targets = [":android"],
)

dejetified_library(
    name = "dejetified-android",
    input = ":android.aar",
    output = "android-legacy.aar",
)

android_library(
    name = "legacy-deps",
    tags = ["maven_coordinates=com.google.dagger:dagger-android-legacy:" + POM_VERSION],
    exports = [
        "//:dagger_with_compiler",
        "//third_party/java/auto:value",
        "//third_party/java/error_prone:annotations",
        "@maven//:com_android_support_support_annotations",
    ],
)

pom_file(
    name = "legacy-pom",
    artifact_id = "dagger-android-legacy",
    artifact_name = "Dagger Android Legacy",
    packaging = "aar",
    targets = [":legacy-deps"],
)

javadoc_library(
    name = "android-javadoc",
    srcs = [":android-srcs"],
    android_api_level = 30,
    exclude_packages = ["dagger.android.internal"],
    root_packages = ["dagger.android"],
    deps = [":android"],
)
