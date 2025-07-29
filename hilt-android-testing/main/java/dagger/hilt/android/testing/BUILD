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

load("@rules_android//rules:rules.bzl", "android_library")
load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//:src"])

android_library(
    name = "custom_test_application",
    testonly = 1,
    srcs = ["CustomTestApplication.java"],
    exported_plugins = [
        "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/customtestapplication:processor",
    ],
    exports = [
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/testing:test_application_component_manager",
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/testing:test_application_component_manager_holder",
        "//hilt-core/main/java/dagger/hilt/internal:component_manager",
    ],
    deps = [
        ":package_info",
        "//hilt-core/main/java/dagger/hilt:generates_root_input",
    ],
)

android_library(
    name = "hilt_android_test",
    testonly = 1,
    srcs = ["HiltAndroidTest.java"],
    exported_plugins = [
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/root:component_tree_deps_processor",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/root:root_plugin",
        "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/androidentrypoint:plugin",
        "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/viewmodel:validation_plugin",
    ],
    exports = [
        ":hilt_android_rule",
        ":hilt_test_application",
        ":on_component_ready_runner",
        ":package_info",
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/testing:early_test_singleton_component_creator",
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/testing:test_application_component_manager",
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/testing:test_application_component_manager_holder",
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/testing:test_component_data",
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/testing:test_injector",
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/testing/root:default",
        "//hilt-android/main/java/dagger/hilt/android/components",
        "//hilt-android/main/java/dagger/hilt/android/internal",
        "//hilt-android/main/java/dagger/hilt/android/internal/builders",
        "//hilt-android/main/java/dagger/hilt/android/internal/legacy:aggregated_element_proxy",
        "//hilt-android/main/java/dagger/hilt/android/internal/managers",
        "//hilt-android/main/java/dagger/hilt/android/internal/modules",
        "//hilt-android/main/java/dagger/hilt/android/scopes",
        "//hilt-android/main/java/dagger/hilt/internal:component_entry_point",
        "//hilt-android/main/java/dagger/hilt/internal:generated_entry_point",
        "//hilt-android/main/java/dagger/hilt/internal/aggregatedroot",
        "//hilt-android/main/java/dagger/hilt/internal/componenttreedeps",
        "//hilt-android/main/java/dagger/hilt/internal/processedrootsentinel",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//hilt-core/main/java/dagger/hilt/internal:component_manager",
        "//hilt-core/main/java/dagger/hilt/internal:generated_component",
        "//hilt-core/main/java/dagger/hilt/internal:preconditions",
        "//hilt-core/main/java/dagger/hilt/internal:test_singleton_component",
        "//hilt-core/main/java/dagger/hilt/migration:disable_install_in_check",
        "//third_party/android/androidx_test/core",
        "//third_party/java/android/android_sdk_linux/extras/android/compatibility/multidex",
        "//third_party/java/androidx/annotation",
        "//third_party/java/dagger",
    ],
    deps = [
        "//hilt-core/main/java/dagger/hilt:generates_root_input",
    ],
)

android_library(
    name = "hilt_android_rule",
    testonly = 1,
    srcs = ["HiltAndroidRule.java"],
    deps = [
        ":package_info",
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/testing:mark_that_rules_ran_rule",
        "//hilt-core/main/java/dagger/hilt/internal:preconditions",
        "//third_party/java/junit",
    ],
)

android_library(
    name = "hilt_test_application",
    testonly = 1,
    srcs = [
        "HiltTestApplication.java",
    ],
    deps = [
        ":on_component_ready_runner",
        ":package_info",
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/testing:test_application_component_manager",
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/testing:test_application_component_manager_holder",
        "//hilt-core/main/java/dagger/hilt/internal:component_manager",
        "//third_party/java/android/android_sdk_linux/extras/android/compatibility/multidex",
    ],
)

android_library(
    name = "on_component_ready_runner",
    testonly = 1,
    srcs = ["OnComponentReadyRunner.java"],
    deps = [
        ":package_info",
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/testing:test_application_component_manager_holder",
        "//hilt-android/main/java/dagger/hilt/android/internal",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//hilt-core/main/java/dagger/hilt/internal:component_manager",
        "//hilt-core/main/java/dagger/hilt/internal:preconditions",
        "//third_party/java/auto:value",
        "//third_party/java/dagger",
    ],
)

android_library(
    name = "uninstall_modules",
    testonly = 1,
    srcs = ["UninstallModules.java"],
    exported_plugins = [
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/uninstallmodules:processor",
    ],
    exports = [
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/uninstallmodules",
    ],
    deps = [
        ":package_info",
        "//hilt-core/main/java/dagger/hilt:generates_root_input",
    ],
)

android_library(
    name = "bind_value",
    testonly = 1,
    srcs = [
        "BindElementsIntoSet.java",
        "BindValue.java",
        "BindValueIntoMap.java",
        "BindValueIntoSet.java",
    ],
    exported_plugins = [
        "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/bindvalue:bind_value_processor",
    ],
    exports = [
        "//hilt-android/main/java/dagger/hilt/android/qualifiers",
        "//hilt-core/main/java/dagger/hilt:install_in",
    ],
    deps = [
        ":package_info",
        "//hilt-core/main/java/dagger/hilt:generates_root_input",
    ],
)

android_library(
    name = "skip_test_injection",
    testonly = 1,
    srcs = ["SkipTestInjection.java"],
    deps = [
        ":package_info",
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
    srcs = glob(["*"]),
)
