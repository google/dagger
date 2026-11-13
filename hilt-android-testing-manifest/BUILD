# Copyright (C) 2026 The Dagger Authors.
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
#   Manifest artifact for Hilt Android tests.

load("@rules_android//rules:rules.bzl", "android_library")
load("//:build_defs.bzl", "POM_VERSION")
load("//tools/maven:maven.bzl", "gen_maven_artifact")

package(default_visibility = ["//:src"])

filegroup(
    name = "javadoc-srcs",
    srcs = [
        "//hilt-android-testing-manifest/main/java/dagger/hilt/android/testing:srcs_filegroup",
    ],
)

android_library(
    name = "artifact-lib",
    testonly = 1,
    tags = ["maven_coordinates=com.google.dagger:hilt-android-testing-manifest:" + POM_VERSION],
    exports = [
        "//hilt-android-testing-manifest/main/java/dagger/hilt/android/testing:hilt_component_activity",
    ],
)

gen_maven_artifact(
    name = "artifact",
    testonly = 1,
    artifact_coordinates = "com.google.dagger:hilt-android-testing-manifest:" + POM_VERSION,
    artifact_name = "Hilt Android Testing Manifest",
    artifact_target = ":artifact-lib",
    artifact_target_libs = [
        "//hilt-android-testing-manifest/main/java/dagger/hilt/android/testing:hilt_component_activity",
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
        "com.google.dagger:dagger",
        "com.google.dagger:hilt-android",
        "com.google.dagger:hilt-core",
        "jakarta.inject:jakarta.inject-api",
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
        "dagger.hilt.android.testing",
    ],
    javadoc_srcs = [
        ":javadoc-srcs",
    ],
    manifest = "main/AndroidManifest.xml",
    packaging = "aar",
)
