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

package(default_visibility = ["//:src"])

load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
    "SOURCE_7_TARGET_7",
)
load("//tools:maven.bzl", "POM_VERSION", "pom_file")

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
    javacopts = SOURCE_7_TARGET_7 + DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    manifest = "AndroidManifest.xml",
    proguard_specs = ["proguard.cfg"],
    tags = ["maven_coordinates=com.google.dagger:dagger-android:" + POM_VERSION],
    deps = [
        "//:dagger_with_compiler",
        "@google_bazel_common//third_party/java/auto:value",
        "@google_bazel_common//third_party/java/error_prone:annotations",
        "@maven//:com_android_support_support_annotations",
    ],
)

pom_file(
    name = "pom",
    artifact_id = "dagger-android",
    artifact_name = "Dagger Android",
    packaging = "aar",
    targets = [":android"],
)

# b/37741866 and https://github.com/google/dagger/issues/715
pom_file(
    name = "jarimpl-pom",
    artifact_id = "dagger-android-jarimpl",
    artifact_name = "Dagger Android",
    targets = [":android"],
)

load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")

javadoc_library(
    name = "android-javadoc",
    srcs = [":android-srcs"],
    android_api_level = 29,
    exclude_packages = ["dagger.android.internal"],
    root_packages = ["dagger.android"],
    deps = [":android"],
)
