# Copyright (C) 2025 The Dagger Authors.
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

load("@rules_java//java:defs.bzl", "java_library")
load("@rules_android//rules:rules.bzl", "android_library")

# BUILD rules for Dagger

package(default_visibility = ["//:src"])

android_library(
    name = "android",
    exported_plugins = ["//dagger-android-processor:plugin"],
    exports = ["//dagger-android/main/java/dagger/android"],
)

android_library(
    name = "android-support",
    exports = [
        ":android",
        "//dagger-android-support/main/java/dagger/android/support",
    ],
)

java_library(
    name = "compiler",
    exports = [
        "//dagger-compiler:processor",
    ],
)

java_library(
    name = "dagger",
    exported_plugins = ["//dagger-compiler:component-codegen"],
    exports = ["//dagger-runtime/main/java/dagger:core"],
)

java_library(
    name = "producers",
    exports = [
        ":dagger",
        "//dagger-producers/main/java/dagger/producers",
    ],
)
