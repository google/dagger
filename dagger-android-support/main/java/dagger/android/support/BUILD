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
load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "JAVA_RELEASE_MIN",
    "POM_VERSION",
)

package(default_visibility = ["//:src"])

filegroup(
    name = "support-srcs",
    srcs = glob(["*.java"]),
)

android_library(
    name = "support",
    srcs = glob(["*.java"]),
    javacopts = JAVA_RELEASE_MIN + DOCLINT_HTML_AND_SYNTAX,
    tags = ["maven_coordinates=com.google.dagger:dagger-android-support:" + POM_VERSION],
    deps = [
        "//dagger-android/main/java/dagger/android",
        "//third_party/java/androidx/annotation",
        "//third_party/java/androidx/appcompat",
        "//third_party/java/androidx/fragment",
        "//third_party/java/dagger",
        "//third_party/java/error_prone:annotations",
    ],
)
