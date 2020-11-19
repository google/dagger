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
load("//:build_defs.bzl", "POM_VERSION_ALPHA")
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
        "//java/dagger/hilt/android/internal/builders",
        "//java/dagger/hilt/android/internal/managers",
        "//java/dagger/hilt/android/internal/managers:component_supplier",
        "//java/dagger/hilt/android/internal/modules",
        "//java/dagger/hilt/android/lifecycle",
        "//java/dagger/hilt/codegen:originating_element",
        "//java/dagger/hilt/internal:component_entry_point",
        "//java/dagger/hilt/internal:component_manager",
        "//java/dagger/hilt/internal:generated_entry_point",
        "//java/dagger/hilt/internal:preconditions",
        "@maven//:androidx_annotation_annotation",
        "@maven//:androidx_fragment_fragment",
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
        "//java/dagger/hilt/processor/internal/root:plugin",
    ],
    exports = [
        "//:dagger_with_compiler",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android/components",
        "//java/dagger/hilt/android/internal/managers",
        "//java/dagger/hilt/android/internal/managers:component_supplier",
        "//java/dagger/hilt/android/internal/modules",
        "//java/dagger/hilt/codegen:originating_element",
        "//java/dagger/hilt/internal:component_manager",
        "//java/dagger/hilt/internal:generated_component",
        "//java/dagger/hilt/internal:generated_entry_point",
        "//java/dagger/hilt/migration:disable_install_in_check",
        "@maven//:androidx_annotation_annotation",
    ],
    deps = [
        ":package_info",
        "//java/dagger/hilt:generates_root_input",
    ],
)

android_library(
    name = "entry_point_accessors",
    srcs = ["EntryPointAccessors.java"],
    deps = [
        ":package_info",
        "//java/dagger/hilt:entry_point",
        "@google_bazel_common//third_party/java/jsr305_annotations",
        "@maven//:androidx_fragment_fragment",
    ],
)

android_library(
    name = "activity_retained_lifecycle",
    srcs = ["ActivityRetainedLifecycle.java"],
    deps = [
        ":package_info",
        "@maven//:androidx_annotation_annotation",
    ],
)

android_library(
    name = "artifact-lib",
    tags = ["maven_coordinates=com.google.dagger:hilt-android:" + POM_VERSION_ALPHA],
    exports = [
        ":android_entry_point",
        ":entry_point_accessors",
        ":hilt_android_app",
        ":package_info",
        "//java/dagger/hilt:artifact-core-lib",
        "//java/dagger/hilt/android/migration:optional_inject",
        "//java/dagger/lint:lint-android-artifact-lib",
    ],
)

java_library(
    name = "package_info",
    srcs = ["package-info.java"],
    deps = [
        "@google_bazel_common//third_party/java/jsr305_annotations",
    ],
)

gen_maven_artifact(
    name = "artifact",
    artifact_coordinates = "com.google.dagger:hilt-android:" + POM_VERSION_ALPHA,
    artifact_name = "Hilt Android",
    artifact_target = ":artifact-lib",
    artifact_target_libs = [
        ":entry_point_accessors",
        "//java/dagger/hilt/android:activity_retained_lifecycle",
        "//java/dagger/hilt/android:android_entry_point",
        "//java/dagger/hilt/android:hilt_android_app",
        "//java/dagger/hilt/android:package_info",
        "//java/dagger/hilt/android/components",
        "//java/dagger/hilt/android/components:view_model_component",
        "//java/dagger/hilt/android/components:package_info",
        "//java/dagger/hilt/android/internal",
        "//java/dagger/hilt/android/internal/builders",
        "//java/dagger/hilt/android/internal/lifecycle",
        "//java/dagger/hilt/android/internal/managers",
        "//java/dagger/hilt/android/internal/managers:component_supplier",
        "//java/dagger/hilt/android/internal/migration:injected_by_hilt",
        "//java/dagger/hilt/android/internal/modules",
        "//java/dagger/hilt/android/lifecycle",
        "//java/dagger/hilt/android/migration:optional_inject",
        "//java/dagger/hilt/android/migration:package_info",
        "//java/dagger/hilt/android/qualifiers",
        "//java/dagger/hilt/android/qualifiers:package_info",
        "//java/dagger/hilt/android/scopes",
        "//java/dagger/hilt/android/scopes:activity_retained_scoped",
        "//java/dagger/hilt/android/scopes:view_model_scoped",
        "//java/dagger/hilt/android/scopes:package_info",
        "//java/dagger/hilt/internal:component_entry_point",
        "//java/dagger/hilt/internal:generated_entry_point",
    ],
    artifact_target_maven_deps = [
        "androidx.activity:activity",
        "androidx.annotation:annotation",
        "androidx.fragment:fragment",
        "androidx.lifecycle:lifecycle-viewmodel",
        "androidx.lifecycle:lifecycle-viewmodel-savedstate",
        "androidx.savedstate:savedstate",
        "com.google.code.findbugs:jsr305",
        "com.google.dagger:dagger-lint-aar",
        "com.google.dagger:dagger",
        "com.google.dagger:hilt-core",
        "javax.inject:javax.inject",
    ],
    artifact_target_maven_deps_banned = [
        "com.google.guava:guava",
        "javax.annotation:jsr250-api",
    ],
    javadoc_android_api_level = 29,
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
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
