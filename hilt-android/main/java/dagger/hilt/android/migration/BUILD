# Copyright (C) 2020 The Dagger Authors.
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
#   Helpers for migrating to Hilt.

load("@rules_android//rules:rules.bzl", "android_library")
load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//:src"])

android_library(
    name = "optional_inject",
    srcs = [
        "OptionalInject.java",
        "OptionalInjectCheck.java",
    ],
    exports = [
        "//hilt-android/main/java/dagger/hilt/android/internal/migration:injected_by_hilt",
    ],
    deps = [
        ":package_info",
        "//hilt-android/main/java/dagger/hilt/android/internal/migration:injected_by_hilt",
        "//hilt-core/main/java/dagger/hilt/internal:preconditions",
        "//third_party/java/androidx/annotation",
        "//third_party/java/androidx/fragment",
    ],
)

android_library(
    name = "custom_inject",
    srcs = [
        "CustomInject.java",
        "CustomInjection.java",
    ],
    exports = [
        "//hilt-android/main/java/dagger/hilt/android/internal/migration:has_custom_inject",
    ],
    deps = [
        ":package_info",
        "//hilt-android/main/java/dagger/hilt/android/internal/migration:has_custom_inject",
        "//hilt-core/main/java/dagger/hilt/internal:preconditions",
        "//third_party/java/androidx/annotation",
    ],
)

java_library(
    name = "package_info",
    srcs = ["package-info.java"],
    deps = [
        "//third_party/java/jsr305_annotations",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["**/*"]),
)
