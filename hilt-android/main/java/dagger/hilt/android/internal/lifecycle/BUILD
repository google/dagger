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
#   Hilt lifecycle ViewModel hooks.

load("@rules_android//rules:rules.bzl", "android_library")

package(default_visibility = ["//:src"])

android_library(
    name = "lifecycle",
    srcs = glob(["*.java"]),
    deps = [
        "//hilt-android/main/java/dagger/hilt/android:activity_retained_lifecycle",
        "//hilt-android/main/java/dagger/hilt/android:view_model_lifecycle",
        "//hilt-android/main/java/dagger/hilt/android/components",
        "//hilt-android/main/java/dagger/hilt/android/internal",
        "//hilt-android/main/java/dagger/hilt/android/internal/builders",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:retained_lifecycle",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//hilt-core/main/java/dagger/hilt/internal:preconditions",
        "//third_party/java/androidx/annotation",
        "//third_party/java/androidx/fragment",
        "//third_party/java/androidx/lifecycle/viewmodel",
        "//third_party/java/androidx/lifecycle/viewmodel_savedstate",
        "//third_party/java/dagger",
        "//third_party/kotlin/kotlin:kotlin_stdlib",
        "@maven//:androidx_savedstate_savedstate",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
