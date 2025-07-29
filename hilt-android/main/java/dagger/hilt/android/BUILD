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

load("@rules_android//rules:rules.bzl", "android_library")
load("@rules_java//java:defs.bzl", "java_library")

# Description:
#   A library based on Hilt that provides standard components and automated injection for Android.
load("//tools:bazel_compat.bzl", "compat_kt_android_library")

package(default_visibility = ["//:src"])

android_library(
    name = "android_entry_point",
    srcs = [
        "AndroidEntryPoint.java",
        "WithFragmentBindings.java",
    ],
    exported_plugins = [
        "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/androidentrypoint:plugin",
    ],
    exports = [
        "//hilt-android/main/java/dagger/hilt/android/components",
        "//hilt-android/main/java/dagger/hilt/android/flags:fragment_get_context_fix",
        "//hilt-android/main/java/dagger/hilt/android/internal",
        "//hilt-android/main/java/dagger/hilt/android/internal/builders",
        "//hilt-android/main/java/dagger/hilt/android/internal/managers",
        "//hilt-android/main/java/dagger/hilt/android/internal/managers:component_supplier",
        "//hilt-android/main/java/dagger/hilt/android/internal/modules",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:hilt_view_model",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:hilt_view_model_extensions",
        "//hilt-android/main/java/dagger/hilt/internal:component_entry_point",
        "//hilt-android/main/java/dagger/hilt/internal:generated_entry_point",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//hilt-core/main/java/dagger/hilt/codegen:originating_element",
        "//hilt-core/main/java/dagger/hilt/internal:component_manager",
        "//hilt-core/main/java/dagger/hilt/internal:preconditions",
        "//third_party/java/androidx/annotation",
        "//third_party/java/androidx/fragment",
    ],
    deps = [
        ":package_info",
        "//hilt-core/main/java/dagger/hilt:generates_root_input",
    ],
)

android_library(
    name = "hilt_android_app",
    srcs = ["HiltAndroidApp.java"],
    exported_plugins = [
        "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/androidentrypoint:plugin",
        "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/viewmodel:validation_plugin",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/root:component_tree_deps_processor",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/root:root_plugin",
    ],
    exports = [
        "//hilt-android/main/java/dagger/hilt/android:activity_retained_lifecycle",
        "//hilt-android/main/java/dagger/hilt/android/components",
        "//hilt-android/main/java/dagger/hilt/android/internal/builders",
        "//hilt-android/main/java/dagger/hilt/android/internal/legacy:aggregated_element_proxy",
        "//hilt-android/main/java/dagger/hilt/android/internal/managers",
        "//hilt-android/main/java/dagger/hilt/android/internal/managers:component_supplier",
        "//hilt-android/main/java/dagger/hilt/android/internal/modules",
        "//hilt-android/main/java/dagger/hilt/android/scopes",
        "//hilt-android/main/java/dagger/hilt/internal:generated_entry_point",
        "//hilt-android/main/java/dagger/hilt/internal/aggregatedroot",
        "//hilt-android/main/java/dagger/hilt/internal/componenttreedeps",
        "//hilt-android/main/java/dagger/hilt/internal/processedrootsentinel",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//hilt-core/main/java/dagger/hilt/codegen:originating_element",
        "//hilt-core/main/java/dagger/hilt/internal:component_manager",
        "//hilt-core/main/java/dagger/hilt/internal:generated_component",
        "//hilt-core/main/java/dagger/hilt/migration:disable_install_in_check",
        "//third_party/java/androidx/annotation",
        "//third_party/java/androidx/fragment",
        "//third_party/java/dagger",
    ],
    deps = [
        ":package_info",
        "//hilt-core/main/java/dagger/hilt:generates_root_input",
    ],
)

android_library(
    name = "activity_retained_lifecycle",
    srcs = [
        "ActivityRetainedLifecycle.java",
    ],
    deps = [
        ":package_info",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:retained_lifecycle",
    ],
)

android_library(
    name = "view_model_lifecycle",
    srcs = [
        "ViewModelLifecycle.java",
    ],
    deps = [
        ":package_info",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:retained_lifecycle",
    ],
)

android_library(
    name = "early_entry_point",
    srcs = [
        "EarlyEntryPoint.java",
        "EarlyEntryPoints.java",
    ],
    exported_plugins = [
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/aggregateddeps:plugin",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/earlyentrypoint:processor",
    ],
    proguard_specs = ["proguard-rules.pro"],
    exports = [
        "//hilt-android/main/java/dagger/hilt/android/internal/earlyentrypoint",
    ],
    deps = [
        ":package_info",
        "//hilt-android/main/java/dagger/hilt/android/internal",
        "//hilt-android/main/java/dagger/hilt/internal:test_singleton_component_manager",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//hilt-core/main/java/dagger/hilt/internal:component_manager",
        "//hilt-core/main/java/dagger/hilt/internal:preconditions",
        "//third_party/java/dagger",
        "//third_party/java/jsr305_annotations",
    ],
)

android_library(
    name = "unstable_api",
    srcs = ["UnstableApi.java"],
    deps = [
        "//third_party/java/androidx/annotation/experimental",
    ],
)

java_library(
    name = "package_info",
    srcs = ["package-info.java"],
    deps = [
        "//third_party/java/jsr305_annotations",
    ],
)

compat_kt_android_library(
    name = "entry_point_accessors",
    srcs = ["EntryPointAccessors.kt"],
    deps = [
        "//hilt-android/main/java/dagger/hilt/android/internal",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//third_party/java/androidx/fragment",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
