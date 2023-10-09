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
#   A library based on Hilt that provides standard components and automated injection for Android.
load("//:build_defs.bzl", "POM_VERSION")
load("//tools:bazel_compat.bzl", "compat_kt_android_library")
load("//tools:maven.bzl", "gen_maven_artifact")

package(default_visibility = ["//:src"])

android_library(
    name = "android_entry_point",
    srcs = [
        "AndroidEntryPoint.java",
        "WithFragmentBindings.java",
    ],
    exported_plugins = [
        "//java/dagger/hilt/android/processor/internal/androidentrypoint:plugin",
    ],
    exports = [
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android/components",
        "//java/dagger/hilt/android/flags:fragment_get_context_fix",
        "//java/dagger/hilt/android/internal",
        "//java/dagger/hilt/android/internal/builders",
        "//java/dagger/hilt/android/internal/managers",
        "//java/dagger/hilt/android/internal/managers:component_supplier",
        "//java/dagger/hilt/android/internal/modules",
        "//java/dagger/hilt/android/lifecycle:hilt_view_model",
        "//java/dagger/hilt/android/lifecycle:hilt_view_model_extensions",
        "//java/dagger/hilt/codegen:originating_element",
        "//java/dagger/hilt/internal:component_entry_point",
        "//java/dagger/hilt/internal:component_manager",
        "//java/dagger/hilt/internal:generated_entry_point",
        "//java/dagger/hilt/internal:preconditions",
        "@maven//:androidx_activity_activity",
        "@maven//:androidx_annotation_annotation",
        "@maven//:androidx_fragment_fragment",
        "@maven//:androidx_lifecycle_lifecycle_common",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
    ],
    deps = [
        ":package_info",
        "//java/dagger/hilt:generates_root_input",
    ],
)

android_library(
    name = "hilt_android_app",
    srcs = ["HiltAndroidApp.java"],
    exported_plugins = [
        "//java/dagger/hilt/android/processor/internal/androidentrypoint:plugin",
        "//java/dagger/hilt/android/processor/internal/viewmodel:validation_plugin",
        "//java/dagger/hilt/processor/internal/root:component_tree_deps_plugin",
        "//java/dagger/hilt/processor/internal/root:root_plugin",
    ],
    exports = [
        ":activity_retained_lifecycle",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android/components",
        "//java/dagger/hilt/android/internal/builders",
        "//java/dagger/hilt/android/internal/legacy:aggregated_element_proxy",
        "//java/dagger/hilt/android/internal/managers",
        "//java/dagger/hilt/android/internal/managers:component_supplier",
        "//java/dagger/hilt/android/internal/modules",
        "//java/dagger/hilt/android/scopes",
        "//java/dagger/hilt/codegen:originating_element",
        "//java/dagger/hilt/internal:component_manager",
        "//java/dagger/hilt/internal:generated_component",
        "//java/dagger/hilt/internal:generated_entry_point",
        "//java/dagger/hilt/internal/aggregatedroot",
        "//java/dagger/hilt/internal/componenttreedeps",
        "//java/dagger/hilt/internal/processedrootsentinel",
        "//java/dagger/hilt/migration:disable_install_in_check",
        "@maven//:androidx_activity_activity",
        "@maven//:androidx_annotation_annotation",
        "@maven//:androidx_fragment_fragment",
        "@maven//:androidx_lifecycle_lifecycle_common",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
    ],
    deps = [
        ":package_info",
        "//java/dagger/hilt:generates_root_input",
    ],
)

android_library(
    name = "activity_retained_lifecycle",
    srcs = [
        "ActivityRetainedLifecycle.java",
    ],
    deps = [
        ":package_info",
        "//java/dagger/hilt/android/lifecycle:retained_lifecycle",
    ],
)

android_library(
    name = "view_model_lifecycle",
    srcs = [
        "ViewModelLifecycle.java",
    ],
    deps = [
        ":package_info",
        "//java/dagger/hilt/android/lifecycle:retained_lifecycle",
    ],
)

android_library(
    name = "early_entry_point",
    srcs = [
        "EarlyEntryPoint.java",
        "EarlyEntryPoints.java",
    ],
    exported_plugins = [
        "//java/dagger/hilt/processor/internal/aggregateddeps:plugin",
        "//java/dagger/hilt/processor/internal/earlyentrypoint:processor",
    ],
    proguard_specs = ["proguard-rules.pro"],
    exports = [
        "//java/dagger/hilt/android/internal/earlyentrypoint",
    ],
    deps = [
        ":package_info",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt/android/internal",
        "//java/dagger/hilt/internal:component_manager",
        "//java/dagger/hilt/internal:preconditions",
        "//java/dagger/hilt/internal:test_singleton_component_manager",
        "//third_party/java/jsr305_annotations",
    ],
)

java_library(
    name = "package_info",
    srcs = ["package-info.java"],
    deps = [
        "//third_party/java/jsr305_annotations",
    ],
)

android_library(
    name = "artifact-lib",
    tags = ["maven_coordinates=com.google.dagger:hilt-android:" + POM_VERSION],
    exports = [
        ":android_entry_point",
        ":early_entry_point",
        ":entry_point_accessors",
        ":hilt_android_app",
        ":package_info",
        "//java/dagger/hilt:artifact-core-lib",
        "//java/dagger/hilt/android/migration:custom_inject",
        "//java/dagger/hilt/android/migration:optional_inject",
        "//java/dagger/lint:lint-android-artifact-lib",
    ],
)

gen_maven_artifact(
    name = "artifact",
    artifact_coordinates = "com.google.dagger:hilt-android:" + POM_VERSION,
    artifact_name = "Hilt Android",
    artifact_target = ":artifact-lib",
    artifact_target_libs = [
        ":entry_point_accessors",
        "//java/dagger/hilt/android:activity_retained_lifecycle",
        "//java/dagger/hilt/android:android_entry_point",
        "//java/dagger/hilt/android:hilt_android_app",
        "//java/dagger/hilt/android:early_entry_point",
        "//java/dagger/hilt/android:package_info",
        "//java/dagger/hilt/android:view_model_lifecycle",
        "//java/dagger/hilt/android/components",
        "//java/dagger/hilt/android/components:package_info",
        "//java/dagger/hilt/android/flags:fragment_get_context_fix",
        "//java/dagger/hilt/android/internal",
        "//java/dagger/hilt/android/internal/builders",
        "//java/dagger/hilt/android/internal/earlyentrypoint",
        "//java/dagger/hilt/android/internal/legacy:aggregated_element_proxy",
        "//java/dagger/hilt/android/internal/lifecycle",
        "//java/dagger/hilt/android/internal/managers",
        "//java/dagger/hilt/android/internal/managers:component_supplier",
        "//java/dagger/hilt/android/internal/migration:has_custom_inject",
        "//java/dagger/hilt/android/internal/migration:injected_by_hilt",
        "//java/dagger/hilt/android/internal/modules",
        "//java/dagger/hilt/android/lifecycle:hilt_view_model",
        "//java/dagger/hilt/android/lifecycle:hilt_view_model_extensions",
        "//java/dagger/hilt/android/lifecycle:package_info",
        "//java/dagger/hilt/android/lifecycle:retained_lifecycle",
        "//java/dagger/hilt/android/migration:custom_inject",
        "//java/dagger/hilt/android/migration:optional_inject",
        "//java/dagger/hilt/android/migration:package_info",
        "//java/dagger/hilt/android/qualifiers",
        "//java/dagger/hilt/android/qualifiers:package_info",
        "//java/dagger/hilt/android/scopes",
        "//java/dagger/hilt/android/scopes:package_info",
        "//java/dagger/hilt/internal:component_entry_point",
        "//java/dagger/hilt/internal:generated_entry_point",
        "//java/dagger/hilt/internal:test_singleton_component_manager",
        "//java/dagger/hilt/internal/aggregatedroot:aggregatedroot",
        "//java/dagger/hilt/internal/processedrootsentinel:processedrootsentinel",
        "//java/dagger/hilt/internal/componenttreedeps:componenttreedeps",
    ],
    artifact_target_maven_deps = [
        "androidx.activity:activity",
        "androidx.annotation:annotation",
        "androidx.fragment:fragment",
        "androidx.lifecycle:lifecycle-common",
        "androidx.lifecycle:lifecycle-viewmodel",
        "androidx.lifecycle:lifecycle-viewmodel-savedstate",
        "androidx.savedstate:savedstate",
        "com.google.code.findbugs:jsr305",
        "com.google.dagger:dagger-lint-aar",
        "com.google.dagger:dagger",
        "com.google.dagger:hilt-core",
        "javax.inject:javax.inject",
        "org.jetbrains.kotlin:kotlin-stdlib",
    ],
    artifact_target_maven_deps_banned = [
        "com.google.guava:guava",
        "javax.annotation:javax.annotation-api",
    ],
    javadoc_android_api_level = 32,
    javadoc_exclude_packages = [
        "dagger.hilt.android.internal",
    ],
    javadoc_root_packages = [
        "dagger.hilt.android",
    ],
    javadoc_srcs = [
        "//java/dagger/hilt:hilt_android_filegroup",
    ],
    manifest = "AndroidManifest.xml",
    packaging = "aar",
    proguard_specs = [
        "//java/dagger/hilt:proguard-rules.pro",
        ":proguard-rules.pro",
        "//java/dagger/hilt/android/lifecycle:proguard-rules.pro",
        "//java/dagger/hilt/internal:proguard-rules.pro",
    ],
)

compat_kt_android_library(
    name = "entry_point_accessors",
    srcs = ["EntryPointAccessors.kt"],
    deps = [
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt/android/internal",
        "@maven//:androidx_activity_activity",
        "@maven//:androidx_fragment_fragment",
        "@maven//:androidx_lifecycle_lifecycle_common",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
