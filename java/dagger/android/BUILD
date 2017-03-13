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
    "PRE_JAVA_8_INFERENCE_OPTS",
)

filegroup(
    name = "android-srcs",
    srcs = glob(["*.java"]),
)

android_library(
    name = "android",
    srcs = [":android-srcs"],
    javacopts = PRE_JAVA_8_INFERENCE_OPTS + SOURCE_7_TARGET_7 + DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    manifest = "AndroidManifest.xml",
    deps = [
        "//:dagger_with_compiler",
        "//third_party:auto_value",
        "//third_party:error_prone_annotations",
        "//third_party:jsr305_annotations",
        "@androidsdk//com.android.support:support-annotations-25.0.0",
    ],
)

load("//tools:javadoc.bzl", "javadoc_library")

javadoc_library(
    name = "android-javadoc",
    srcs = [":android-srcs"],
    android_api_level = 25,
    root_packages = ["dagger.android"],
    deps = [":android"],
)
