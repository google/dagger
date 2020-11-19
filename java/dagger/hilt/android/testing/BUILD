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

load("//:build_defs.bzl", "POM_VERSION_ALPHA")
load("//tools:maven.bzl", "gen_maven_artifact")

package(default_visibility = ["//:src"])

android_library(
    name = "custom_test_application",
    testonly = 1,
    srcs = ["CustomTestApplication.java"],
    exported_plugins = [
        "//java/dagger/hilt/android/processor/internal/customtestapplication:processor",
    ],
    exports = [
        "//java/dagger/hilt/android/internal/testing:test_application_component_manager",
        "//java/dagger/hilt/android/internal/testing:test_application_component_manager_holder",
        "//java/dagger/hilt/internal:component_manager",
    ],
    deps = [
        ":package_info",
        "//java/dagger/hilt:generates_root_input",
    ],
)

android_library(
    name = "hilt_android_test",
    testonly = 1,
    srcs = ["HiltAndroidTest.java"],
    exported_plugins = [
        "//java/dagger/hilt/processor/internal/root:plugin",
        "//java/dagger/hilt/android/processor/internal/androidentrypoint:plugin",
    ],
    exports = [
        ":hilt_android_rule",
        ":hilt_test_application",
        ":on_component_ready_runner",
        ":package_info",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android/components",
        "//java/dagger/hilt/android/internal/builders",
        "//java/dagger/hilt/android/internal/managers",
        "//java/dagger/hilt/android/internal/modules",
        "//java/dagger/hilt/android/internal/testing:test_application_component_manager",
        "//java/dagger/hilt/android/internal/testing:test_application_component_manager_holder",
        "//java/dagger/hilt/android/internal/testing:test_injector",
        "//java/dagger/hilt/android/scopes",
        "//java/dagger/hilt/internal:component_entry_point",
        "//java/dagger/hilt/internal:component_manager",
        "//java/dagger/hilt/internal:generated_entry_point",
        "//java/dagger/hilt/internal:preconditions",
        "//java/dagger/hilt/migration:disable_install_in_check",
        "@maven//:androidx_annotation_annotation",
        "@maven//:androidx_multidex_multidex",
        "@maven//:androidx_test_core",
    ],
    deps = [
        "//java/dagger/hilt:generates_root_input",
    ],
)

android_library(
    name = "hilt_android_rule",
    testonly = 1,
    srcs = ["HiltAndroidRule.java"],
    deps = [
        ":package_info",
        "//java/dagger/hilt/android/internal/testing:mark_that_rules_ran_rule",
        "//java/dagger/hilt/internal:preconditions",
        "@maven//:junit_junit",
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
        "//java/dagger/hilt/android/internal/testing:test_application_component_manager",
        "//java/dagger/hilt/android/internal/testing:test_application_component_manager_holder",
        "//java/dagger/hilt/internal:component_manager",
        "@maven//:androidx_multidex_multidex",
    ],
)

android_library(
    name = "on_component_ready_runner",
    testonly = 1,
    srcs = ["OnComponentReadyRunner.java"],
    deps = [
        ":package_info",
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
    deps = [
        ":package_info",
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
    deps = [
        ":package_info",
        "//java/dagger/hilt:generates_root_input",
    ],
)

java_library(
    name = "package_info",
    srcs = ["package-info.java"],
    deps = [
        "@google_bazel_common//third_party/java/jsr305_annotations",
    ],
)

android_library(
    name = "artifact-lib",
    testonly = 1,
    tags = ["maven_coordinates=com.google.dagger:hilt-android-testing:" + POM_VERSION_ALPHA],
    exports = [
        ":bind_value",
        ":custom_test_application",
        ":hilt_android_test",
        ":package_info",
        ":uninstall_modules",
        "//java/dagger/hilt/android:artifact-lib",
    ],
)

gen_maven_artifact(
    name = "artifact",
    testonly = 1,
    artifact_coordinates = "com.google.dagger:hilt-android-testing:" + POM_VERSION_ALPHA,
    artifact_name = "Hilt Android Testing",
    artifact_target = ":artifact-lib",
    artifact_target_libs = [
        "//java/dagger/hilt/android/internal/testing:mark_that_rules_ran_rule",
        "//java/dagger/hilt/android/internal/testing:test_application_component_manager",
        "//java/dagger/hilt/android/internal/testing:test_application_component_manager_holder",
        "//java/dagger/hilt/android/internal/testing:test_component_data",
        "//java/dagger/hilt/android/internal/testing:test_injector",
        "//java/dagger/hilt/android/testing:bind_value",
        "//java/dagger/hilt/android/testing:custom_test_application",
        "//java/dagger/hilt/android/testing:hilt_android_rule",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "//java/dagger/hilt/android/testing:hilt_test_application",
        "//java/dagger/hilt/android/testing:on_component_ready_runner",
        "//java/dagger/hilt/android/testing:package_info",
        "//java/dagger/hilt/android/testing:uninstall_modules",
    ],
    artifact_target_maven_deps = [
        "androidx.activity:activity",
        "androidx.annotation:annotation",
        "androidx.fragment:fragment",
        "androidx.lifecycle:lifecycle-viewmodel",
        "androidx.lifecycle:lifecycle-viewmodel-savedstate",
        "androidx.savedstate:savedstate",
        "androidx.multidex:multidex",
        "androidx.test:core",
        "com.google.code.findbugs:jsr305",
        "com.google.dagger:dagger",
        "com.google.dagger:hilt-android",
        "javax.inject:javax.inject",
        "junit:junit",
    ],
    artifact_target_maven_deps_banned = [
        "com.google.guava:guava",
        "javax.annotation:jsr250-api",
    ],
    javadoc_android_api_level = 29,
    javadoc_exclude_packages = [
        "dagger.hilt.internal",
        "dagger.hilt.android.internal",
    ],
    javadoc_root_packages = [
        "dagger.hilt.android.testing",
    ],
    javadoc_srcs = [
        "//java/dagger/hilt:hilt_android_testing_filegroup",
    ],
    manifest = "AndroidManifest.xml",
    packaging = "aar",
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
