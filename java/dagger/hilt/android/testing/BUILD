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
    name = "custom_base_test_application",
    testonly = 1,
    srcs = ["CustomBaseTestApplication.java"],
    exported_plugins = [
        "//java/dagger/hilt/android/processor/internal/custombasetestapplication:plugin",
    ],
    exports = [
        "//java/dagger/hilt/android/internal/custombasetestapplication:data",
    ],
    deps = [
        "//java/dagger/hilt:generates_root_input",
    ],
)

android_library(
    name = "android_robolectric_entry_point",
    testonly = 1,
    srcs = ["AndroidRobolectricEntryPoint.java"],
    exports = [
        ":common_test_entry_point_exports",
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
        ":hilt_test_rule",
        ":on_component_ready_runner",
        "//java/dagger/hilt:generate_components",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android/components",
        "//java/dagger/hilt/android/internal/builders",
        "//java/dagger/hilt/android/internal/managers",
        "//java/dagger/hilt/android/internal/modules",
        "//java/dagger/hilt/android/internal/testing:test_application_component_manager",
        "//java/dagger/hilt/android/internal/testing:test_application_component_manager_holder",
        "//java/dagger/hilt/android/internal/testing:test_injector",
        "//java/dagger/hilt/android/internal/testing:test_instance_holder",
        "//java/dagger/hilt/android/scopes",
        "//java/dagger/hilt/internal:component_entry_point",
        "//java/dagger/hilt/internal:component_manager",
        "//java/dagger/hilt/internal:generated_entry_point",
        "//java/dagger/hilt/internal:preconditions",
        "@maven//:androidx_annotation_annotation",
        "@maven//:androidx_multidex_multidex",
        "@maven//:androidx_test_core",
    ],
)

android_library(
    name = "hilt_test_rule",
    testonly = 1,
    srcs = ["HiltTestRule.java"],
    deps = [
        "//java/dagger/hilt/android/internal/testing:mark_that_rules_ran_rule",
        "//java/dagger/hilt/internal:preconditions",
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
        "//java/dagger/hilt/android/internal/testing:test_application_component_manager_holder",
        "//java/dagger/hilt/internal:component_manager",
        "//java/dagger/hilt/internal:preconditions",
        "@google_bazel_common//third_party/java/auto:value",
    ],
)

android_library(
    name = "uninstall_modules",
    testonly = 1,
    srcs = ["UninstallModules.java"],
    exported_plugins = [
        "//java/dagger/hilt/android/processor/internal/uninstallmodules:processor",
    ],
)

java_library(
    name = "bind_value",
    testonly = 1,
    srcs = [
        "BindElementsIntoSet.java",
        "BindValue.java",
        "BindValueIntoMap.java",
        "BindValueIntoSet.java",
    ],
    exported_plugins = [
        "//java/dagger/hilt/android/processor/internal/bindvalue:bind_value_processor",
    ],
    exports = [
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android/qualifiers",
    ],
    deps = ["//java/dagger/hilt:generates_root_input"],
)

android_library(
    name = "artifact-lib",
    testonly = 1,
    tags = ["maven_coordinates=com.google.dagger:hilt-android-testing:" + POM_VERSION],
    visibility = ["//visibility:private"],
    exports = [
        ":android_emulator_entry_point",
        ":android_robolectric_entry_point",
        ":bind_value",
        ":custom_base_test_application",
        ":uninstall_modules",
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
        "//java/dagger/hilt/android/internal/custombasetestapplication:data",
        "//java/dagger/hilt/android/internal/testing:mark_that_rules_ran_rule",
        "//java/dagger/hilt/android/internal/testing:test_application_component_manager",
        "//java/dagger/hilt/android/internal/testing:test_application_component_manager_holder",
        "//java/dagger/hilt/android/internal/testing:test_component_supplier",
        "//java/dagger/hilt/android/internal/testing:test_injector",
        "//java/dagger/hilt/android/internal/testing:test_instance_holder",
        "//java/dagger/hilt/android/testing:android_emulator_entry_point",
        "//java/dagger/hilt/android/testing:android_robolectric_entry_point",
        "//java/dagger/hilt/android/testing:bind_value",
        "//java/dagger/hilt/android/testing:custom_base_test_application",
        "//java/dagger/hilt/android/testing:hilt_test_rule",
        "//java/dagger/hilt/android/testing:on_component_ready_runner",
        "//java/dagger/hilt/android/testing:uninstall_modules",
    ],
)
