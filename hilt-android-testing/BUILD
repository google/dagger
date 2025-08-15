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
load("//:build_defs.bzl", "POM_VERSION")
load("//tools/maven:maven.bzl", "gen_maven_artifact")

package(default_visibility = ["//:src"])

filegroup(
    name = "javadoc-srcs",
    srcs = [
        ":hilt_android_testing_filegroup",
        ":hilt_testing_filegroup",
    ],
)

filegroup(
    name = "hilt_testing_filegroup",
    srcs = [
        "//hilt-android-testing/main/java/dagger/hilt/testing:srcs_filegroup",
    ],
)

filegroup(
    name = "hilt_android_testing_filegroup",
    srcs = [
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/testing:srcs_filegroup",
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/uninstallmodules:srcs_filegroup",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:srcs_filegroup",
    ],
)

android_library(
    name = "artifact-lib",
    testonly = 1,
    tags = ["maven_coordinates=com.google.dagger:hilt-android-testing:" + POM_VERSION],
    exports = [
        "//hilt-android:artifact-lib",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:bind_value",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:custom_test_application",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:package_info",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:skip_test_injection",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:uninstall_modules",
        "//hilt-android-testing/main/java/dagger/hilt/testing:test_install_in",
    ],
)

gen_maven_artifact(
    name = "artifact",
    testonly = 1,
    artifact_coordinates = "com.google.dagger:hilt-android-testing:" + POM_VERSION,
    artifact_name = "Hilt Android Testing",
    artifact_target = ":artifact-lib",
    artifact_target_libs = [
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/testing:early_test_singleton_component_creator",
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/testing:mark_that_rules_ran_rule",
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/testing:test_application_component_manager",
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/testing:test_application_component_manager_holder",
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/testing:test_component_data",
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/testing:test_injector",
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/testing/root:default",
        "//hilt-android-testing/main/java/dagger/hilt/android/internal/uninstallmodules:uninstallmodules",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:bind_value",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:custom_test_application",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_rule",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_test_application",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:on_component_ready_runner",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:package_info",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:skip_test_injection",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:uninstall_modules",
        "//hilt-android-testing/main/java/dagger/hilt/testing:test_install_in",
        "//hilt-android-testing/main/java/dagger/hilt/testing:package_info",
    ],
    artifact_target_maven_deps = [
        "androidx.activity:activity:aar",
        "androidx.annotation:annotation",
        "androidx.annotation:annotation-experimental:aar",
        "androidx.fragment:fragment:aar",
        "androidx.lifecycle:lifecycle-common",
        "androidx.lifecycle:lifecycle-viewmodel:aar",
        "androidx.lifecycle:lifecycle-viewmodel-savedstate:aar",
        "androidx.multidex:multidex:aar",
        "androidx.savedstate:savedstate:aar",
        "androidx.test:core:aar",
        "com.google.code.findbugs:jsr305",
        "com.google.dagger:dagger",
        "com.google.dagger:hilt-android",
        "javax.inject:javax.inject",
        "junit:junit",
        "org.jetbrains.kotlin:kotlin-stdlib",
    ],
    artifact_target_maven_deps_banned = [
        "com.google.guava:guava",
        "javax.annotation:javax.annotation-api",
    ],
    javadoc_android_api_level = 34,
    javadoc_exclude_packages = [
        "dagger.hilt.internal",
        "dagger.hilt.android.internal",
    ],
    javadoc_root_packages = [
        "dagger.hilt.android.testing",
    ],
    javadoc_srcs = [
        ":javadoc-srcs",
    ],
    manifest = "AndroidManifest.xml",
    packaging = "aar",
)
