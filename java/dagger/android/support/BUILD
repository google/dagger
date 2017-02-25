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

load("//:build_defs.bzl", "SOURCE_7_TARGET_7", "PRE_JAVA_8_INFERENCE_OPTS")

filegroup(
    name = "support-srcs",
    srcs = glob(["*.java"]),
)

android_library(
    name = "support",
    srcs = glob(["*.java"]),
    javacopts = PRE_JAVA_8_INFERENCE_OPTS + SOURCE_7_TARGET_7,
    manifest = "AndroidManifest.xml",
    deps = [
        "//:dagger_with_compiler",
        "//java/dagger/android",
        "@androidsdk//com.android.support:appcompat-v7-25.0.0",
        "@androidsdk//com.android.support:support-annotations-25.0.0",
        "@androidsdk//com.android.support:support-v4-25.0.0",
    ],
)

load("//tools:javadoc.bzl", "javadoc_library")

javadoc_library(
    name = "support-javadoc",
    srcs = [":support-srcs"],
    android_api_level = 25,
    root_packages = ["dagger.android.support"],
    deps = [":support"],
)
