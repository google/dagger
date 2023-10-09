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
#   Hilt ViewModel integration.

load("//tools:bazel_compat.bzl", "compat_kt_android_library")

package(default_visibility = ["//:src"])

java_library(
    name = "package_info",
    srcs = ["package-info.java"],
    deps = [
        "//third_party/java/jsr305_annotations",
    ],
)

android_library(
    name = "hilt_view_model",
    srcs = ["HiltViewModel.java"],
    exported_plugins = [
        "//java/dagger/hilt/android/processor/internal/viewmodel:processor",
    ],
    proguard_specs = ["proguard-rules.pro"],
    exports = [
        "//:dagger_with_compiler",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android/components",
        "//java/dagger/hilt/android/internal/lifecycle",
        "//java/dagger/hilt/codegen:originating_element",
    ],
    deps = [
        ":package_info",
        "//java/dagger/hilt:generates_root_input",
    ],
)

android_library(
    name = "retained_lifecycle",
    srcs = ["RetainedLifecycle.java"],
    deps = [
        ":package_info",
        "@maven//:androidx_annotation_annotation",
    ],
)

compat_kt_android_library(
    name = "hilt_view_model_extensions",
    srcs = ["HiltViewModelExtensions.kt"],
    deps = [
        ":package_info",
        "//java/dagger/hilt/android/internal/lifecycle",
        "@maven//:androidx_annotation_annotation",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
