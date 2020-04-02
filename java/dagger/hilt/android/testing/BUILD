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
#   Testing libraries for Hilt Android.

load("//tools:maven.bzl", "POM_VERSION", "gen_maven_artifact")

package(default_visibility = ["//:src"])

android_library(
    name = "android_robolectric_entry_point",
    testonly = 1,
    srcs = ["AndroidRobolectricEntryPoint.java"],
    exports = [
        ":common_test_entry_point_exports",
        ":robolectric_test_rule",
    ],
    deps = [
        "//java/dagger/hilt:generates_root_input",
    ],
)

android_library(
    name = "android_emulator_entry_point",
    testonly = 1,
    srcs = ["AndroidEmulatorEntryPoint.java"],
    exports = [
        ":common_test_entry_point_exports",
        ":emulator_test_rule",
    ],
    deps = [
        "//java/dagger/hilt:generates_root_input",
    ],
)

android_library(
    name = "common_test_entry_point_exports",
    testonly = 1,
    exported_plugins = [
        "//java/dagger/hilt/android/processor/internal/testroot:test_root_processor",
        "//java/dagger/hilt/processor/internal/root:plugin",
        "//java/dagger/hilt/android/processor/internal/testing:internal_test_root_processor",
        "//java/dagger/hilt/android/processor/internal/androidentrypoint:plugin",
    ],
    exports = [
        ":on_component_ready_runner",
        "//java/dagger/hilt:generate_components",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android/components",
        "//java/dagger/hilt/android/internal/builders",
        "//java/dagger/hilt/android/internal/managers",
        "//java/dagger/hilt/android/internal/managers:component_supplier",
        "//java/dagger/hilt/android/internal/modules",
        "//java/dagger/hilt/android/internal/testing:test_application_component_manager",
        "//java/dagger/hilt/android/internal/testing:test_application_component_manager_holder",
        "//java/dagger/hilt/android/internal/testing:test_injector",
        "//java/dagger/hilt/android/scopes",
        "//java/dagger/hilt/internal:component_entry_point",
        "//java/dagger/hilt/internal:component_manager",
        "//java/dagger/hilt/internal:generated_entry_point",
        "//java/dagger/internal/guava:base-android",
        "@maven//:androidx_annotation_annotation",
        "@maven//:androidx_multidex_multidex",
        "@maven//:androidx_test_core",
    ],
)

android_library(
    name = "robolectric_test_rule",
    testonly = 1,
    srcs = ["HiltRobolectricTestRule.java"],
    deps = [
        ":test_rules_common",
        "@google_bazel_common//third_party/java/junit",
        "@maven//:androidx_test_core",
    ],
)

android_library(
    name = "emulator_test_rule",
    testonly = 1,
    srcs = ["HiltEmulatorTestRule.java"],
    deps = [
        ":test_rules_common",
        "//java/dagger/internal/guava:base-android",
        "@google_bazel_common//third_party/java/junit",
        "@maven//:androidx_test_monitor",
    ],
)

android_library(
    name = "test_rules_common",
    testonly = 1,
    srcs = ["MarkThatRulesRanRule.java"],
    deps = [
        "//java/dagger/hilt/android/internal/testing:test_application_component_manager",
        "//java/dagger/hilt/android/internal/testing:test_application_component_manager_holder",
        "//java/dagger/internal/guava:base-android",
        "@google_bazel_common//third_party/java/junit",
    ],
)

android_library(
    name = "on_component_ready_runner",
    testonly = 1,
    srcs = ["OnComponentReadyRunner.java"],
    deps = [
        "//:dagger_with_compiler",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt/internal:component_manager",
        "//java/dagger/internal/guava:base-android",
        "@google_bazel_common//third_party/java/auto:value",
    ],
)

android_library(
    name = "ignore_modules",
    testonly = 1,
    srcs = ["IgnoreModules.java"],
    exported_plugins = [
        "//java/dagger/hilt/android/processor/internal/ignoremodules:processor",
    ],
)

android_library(
    name = "artifact-lib",
    testonly = 1,
    tags = ["maven_coordinates=com.google.dagger:hilt-android-testing:" + POM_VERSION],
    visibility = ["//visibility:private"],
    exports = [
        ":android_robolectric_entry_point",
        ":ignore_modules",
        "//java/dagger/hilt/android:artifact-lib",
    ],
)

gen_maven_artifact(
    name = "artifact",
    testonly = 1,
    artifact_id = "hilt-android-testing",
    artifact_name = "Hilt Android Testing",
    artifact_target = ":artifact-lib",
    javadoc_android_api_level = 29,
    javadoc_root_packages = [
        "dagger.hilt.android.testing",
    ],
    javadoc_srcs = glob(["*.java"]),
    shaded_deps = ["@com_google_auto_auto_common//jar"],
    shaded_rules = ["rule com.google.auto.common.** dagger.shaded.auto.common.@1"],
    deps = [
        "//java/dagger/hilt/android/internal/testing:test_application_component_manager",
        "//java/dagger/hilt/android/internal/testing:test_application_component_manager_holder",
        "//java/dagger/hilt/android/internal/testing:test_injector",
        "//java/dagger/hilt/android/testing:android_robolectric_entry_point",
        "//java/dagger/hilt/android/testing:ignore_modules",
        "//java/dagger/hilt/android/testing:on_component_ready_runner",
        "//java/dagger/hilt/android/testing:robolectric_test_rule",
        "//java/dagger/hilt/android/testing:test_rules_common",
    ],
)
