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

load("//tools:maven.bzl", "POM_VERSION", "gen_maven_artifact")

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
        "//java/dagger/hilt/android/internal/lifecycle",
        "//java/dagger/hilt/android/internal/managers",
        "//java/dagger/hilt/android/internal/managers:component_supplier",
        "//java/dagger/hilt/android/internal/modules",
        "//java/dagger/hilt/codegen:originating_element",
        "//java/dagger/hilt/internal:component_entry_point",
        "//java/dagger/hilt/internal:component_manager",
        "//java/dagger/hilt/internal:generated_entry_point",
        "//java/dagger/hilt/internal:preconditions",
        "@maven//:androidx_annotation_annotation",
        "@maven//:androidx_fragment_fragment",
    ],
    deps = [
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
        "@maven//:androidx_annotation_annotation",
    ],
    deps = [
        "//java/dagger/hilt:generates_root_input",
    ],
)

android_library(
    name = "entry_point_accessors",
    srcs = ["EntryPointAccessors.java"],
    deps = [
        "//java/dagger/hilt:entry_point",
        "@maven//:androidx_fragment_fragment",
    ],
)

android_library(
    name = "artifact-lib",
    tags = ["maven_coordinates=com.google.dagger:hilt-android:" + POM_VERSION],
    visibility = [
        "//java/dagger/hilt/android/testing:__pkg__",
    ],
    exports = [
        ":entry_point_accessors",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt:generate_components",
        "//java/dagger/hilt:generates_root_input",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:android_entry_point",
        "//java/dagger/hilt/android/migration:optional_inject",
        "//java/dagger/hilt/migration:alias_of",
        "//java/dagger/lint:lint-android-artifact-lib",
    ],
)

gen_maven_artifact(
    name = "artifact",
    artifact_id = "hilt-android",
    artifact_name = "Hilt Android",
    artifact_target = ":artifact-lib",
    javadoc_android_api_level = 29,
    javadoc_exclude_packages = [
        "dagger.hilt.internal",
        "dagger.hilt.android.internal",
    ],
    javadoc_root_packages = [
        "dagger.hilt",
    ],
    javadoc_srcs = glob(["*.java"]),  # TODO(user): Fix these srcs.
    manifest = "AndroidManifest.xml",
    packaging = "aar",
    shaded_deps = ["@com_google_auto_auto_common//jar"],
    shaded_rules = ["rule com.google.auto.common.** dagger.shaded.auto.common.@1"],
    deps = [
        ":entry_point_accessors",
        "//java/dagger/hilt:define_component",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt:generate_components",
        "//java/dagger/hilt:generates_root_input",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:android_entry_point",
        "//java/dagger/hilt/android/components",
        "//java/dagger/hilt/android/internal/builders",
        "//java/dagger/hilt/android/internal/lifecycle",
        "//java/dagger/hilt/android/internal/managers",
        "//java/dagger/hilt/android/internal/managers:component_supplier",
        "//java/dagger/hilt/android/internal/migration:injected_by_hilt",
        "//java/dagger/hilt/android/internal/modules",
        "//java/dagger/hilt/android/migration:optional_inject",
        "//java/dagger/hilt/android/qualifiers",
        "//java/dagger/hilt/android/scopes",
        "//java/dagger/hilt/android/scopes:activity_retained_scoped",
        "//java/dagger/hilt/codegen:originating_element",
        "//java/dagger/hilt/internal:component_entry_point",
        "//java/dagger/hilt/internal:component_manager",
        "//java/dagger/hilt/internal:generated_component",
        "//java/dagger/hilt/internal:generated_entry_point",
        "//java/dagger/hilt/internal:preconditions",
        "//java/dagger/hilt/internal:unsafe_casts",
        "//java/dagger/hilt/internal/aliasof",
        "//java/dagger/hilt/internal/definecomponent",
        "//java/dagger/hilt/internal/generatesrootinput",
        "//java/dagger/hilt/migration:alias_of",
        "//java/dagger/hilt/processor/internal/aggregateddeps:annotation",
    ],
)
