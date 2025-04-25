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

load("@rules_android//rules:rules.bzl", "android_library")
load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "JAVA_RELEASE_MIN",
    "POM_VERSION",
)

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
    javacopts = JAVA_RELEASE_MIN + DOCLINT_HTML_AND_SYNTAX,
    tags = ["maven_coordinates=com.google.dagger:dagger-android:" + POM_VERSION],
    exports = [
        "//dagger-lint:lint-android-artifact-lib",
    ],
    deps = [
        "//:dagger_with_compiler",
        "//dagger-android-proguard-processor:proguard",
        "//third_party/java/auto:value",
        "//third_party/java/error_prone:annotations",
        "@maven//:androidx_annotation_annotation",
        "@maven//:androidx_annotation_annotation_jvm",
    ],
)
