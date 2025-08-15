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

load("@rules_android//rules:rules.bzl", "android_library")
load("//:build_defs.bzl", "POM_VERSION")
load("//tools/maven:maven.bzl", "gen_maven_artifact")

package(default_visibility = ["//:src"])

filegroup(
    name = "javadoc-srcs",
    srcs = [
        "//hilt-android/main/java/dagger/hilt/android:srcs_filegroup",
        "//hilt-android/main/java/dagger/hilt/android/components:srcs_filegroup",
        "//hilt-android/main/java/dagger/hilt/android/flags:srcs_filegroup",
        "//hilt-android/main/java/dagger/hilt/android/internal:srcs_filegroup",
        "//hilt-android/main/java/dagger/hilt/android/internal/builders:srcs_filegroup",
        "//hilt-android/main/java/dagger/hilt/android/internal/lifecycle:srcs_filegroup",
        "//hilt-android/main/java/dagger/hilt/android/internal/managers:srcs_filegroup",
        "//hilt-android/main/java/dagger/hilt/android/internal/migration:srcs_filegroup",
        "//hilt-android/main/java/dagger/hilt/android/internal/modules:srcs_filegroup",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:srcs_filegroup",
        "//hilt-android/main/java/dagger/hilt/android/migration:srcs_filegroup",
        "//hilt-android/main/java/dagger/hilt/android/qualifiers:srcs_filegroup",
        "//hilt-android/main/java/dagger/hilt/android/scopes:srcs_filegroup",
        "//hilt-android/main/java/dagger/hilt/internal:srcs_filegroup",
    ],
)

android_library(
    name = "artifact-lib",
    tags = ["maven_coordinates=com.google.dagger:hilt-android:" + POM_VERSION],
    exports = [
        "//dagger-lint-android:lint-android-artifact-lib",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:early_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:entry_point_accessors",
        "//hilt-android/main/java/dagger/hilt/android:hilt_android_app",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-android/main/java/dagger/hilt/android:unstable_api",
        "//hilt-android/main/java/dagger/hilt/android/migration:custom_inject",
        "//hilt-android/main/java/dagger/hilt/android/migration:optional_inject",
        "//hilt-core:artifact-lib",
    ],
)

gen_maven_artifact(
    name = "artifact",
    artifact_coordinates = "com.google.dagger:hilt-android:" + POM_VERSION,
    artifact_name = "Hilt Android",
    artifact_target = ":artifact-lib",
    artifact_target_libs = [
        "//hilt-android/main/java/dagger/hilt/android:entry_point_accessors",
        "//hilt-android/main/java/dagger/hilt/android:activity_retained_lifecycle",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:hilt_android_app",
        "//hilt-android/main/java/dagger/hilt/android:unstable_api",
        "//hilt-android/main/java/dagger/hilt/android:early_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-android/main/java/dagger/hilt/android:view_model_lifecycle",
        "//hilt-android/main/java/dagger/hilt/android/components",
        "//hilt-android/main/java/dagger/hilt/android/components:package_info",
        "//hilt-android/main/java/dagger/hilt/android/flags:fragment_get_context_fix",
        "//hilt-android/main/java/dagger/hilt/android/internal",
        "//hilt-android/main/java/dagger/hilt/android/internal/builders",
        "//hilt-android/main/java/dagger/hilt/android/internal/earlyentrypoint",
        "//hilt-android/main/java/dagger/hilt/android/internal/legacy:aggregated_element_proxy",
        "//hilt-android/main/java/dagger/hilt/android/internal/lifecycle",
        "//hilt-android/main/java/dagger/hilt/android/internal/managers",
        "//hilt-android/main/java/dagger/hilt/android/internal/managers:component_supplier",
        "//hilt-android/main/java/dagger/hilt/android/internal/managers:saved_state_handle_holder",
        "//hilt-android/main/java/dagger/hilt/android/internal/migration:has_custom_inject",
        "//hilt-android/main/java/dagger/hilt/android/internal/migration:injected_by_hilt",
        "//hilt-android/main/java/dagger/hilt/android/internal/modules",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:activity_retained_saved_state",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:hilt_view_model",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:hilt_view_model_extensions",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:package_info",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:retained_lifecycle",
        "//hilt-android/main/java/dagger/hilt/android/migration:custom_inject",
        "//hilt-android/main/java/dagger/hilt/android/migration:optional_inject",
        "//hilt-android/main/java/dagger/hilt/android/migration:package_info",
        "//hilt-android/main/java/dagger/hilt/android/qualifiers",
        "//hilt-android/main/java/dagger/hilt/android/qualifiers:package_info",
        "//hilt-android/main/java/dagger/hilt/android/scopes",
        "//hilt-android/main/java/dagger/hilt/android/scopes:package_info",
        "//hilt-android/main/java/dagger/hilt/internal:component_entry_point",
        "//hilt-android/main/java/dagger/hilt/internal:generated_entry_point",
        "//hilt-android/main/java/dagger/hilt/internal:test_singleton_component_manager",
        "//hilt-android/main/java/dagger/hilt/internal/aggregatedroot",
        "//hilt-android/main/java/dagger/hilt/internal/processedrootsentinel",
        "//hilt-android/main/java/dagger/hilt/internal/componenttreedeps",
    ],
    artifact_target_maven_deps = [
        "androidx.activity:activity:aar",
        "androidx.annotation:annotation",
        "androidx.annotation:annotation-experimental:aar",
        "androidx.fragment:fragment:aar",
        "androidx.lifecycle:lifecycle-common",
        "androidx.lifecycle:lifecycle-viewmodel:aar",
        "androidx.lifecycle:lifecycle-viewmodel-savedstate:aar",
        "androidx.savedstate:savedstate:aar",
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
    javadoc_android_api_level = 34,
    javadoc_exclude_packages = [
        "dagger.hilt.android.internal",
    ],
    javadoc_root_packages = [
        "dagger.hilt.android",
    ],
    javadoc_srcs = [
        ":javadoc-srcs",
    ],
    manifest = "AndroidManifest.xml",
    packaging = "aar",
    proguard_and_r8_specs = [
        "//hilt-core/main/resources:proguard",
        "//hilt-android/main/resources:proguard",
    ],
)
