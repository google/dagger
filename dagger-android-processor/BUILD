# Copyright (C) 2017 The Dagger Authors.
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
#   Public Dagger API for Android

load("//:build_defs.bzl", "POM_VERSION")
load("//tools/maven:maven.bzl", "gen_maven_artifact")

package(default_visibility = ["//:src"])

package_group(
    name = "internal",
    packages = [
        "//dagger-android-processor/...",
        "//javatests/dagger/android/processor/...",
    ],
)

gen_maven_artifact(
    name = "artifact",
    artifact_coordinates = "com.google.dagger:dagger-android-processor:" + POM_VERSION,
    artifact_name = "Dagger Android Processor",
    artifact_target = "//dagger-android-processor/main/java/dagger/android/processor",
    artifact_target_libs = [
        "//dagger-android-processor/main/java/dagger/android/processor:base_processing_step",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
    ],
    artifact_target_maven_deps = [
        "com.google.dagger:dagger",
        "com.google.devtools.ksp:symbol-processing-api",
        "com.google.guava:guava",
        "com.squareup:javapoet",
        "com.google.code.findbugs:jsr305",
        "com.google.dagger:dagger-spi",
        "com.google.guava:failureaccess",
        "com.squareup:kotlinpoet",
        "net.ltgt.gradle.incap:incap",
        "org.jetbrains.kotlin:kotlin-stdlib",
    ],
    javadoc_root_packages = [
        "dagger.android.processor",
    ],
    javadoc_srcs = [
        "//dagger-android-processor/main/java/dagger/android/processor:srcs",
    ],
)

# Note: We're exposing this to the proguard processor, which is not actually used by any artifact.
alias(
    name = "base_processing_step",
    actual = "//dagger-android-processor/main/java/dagger/android/processor:base_processing_step",
    visibility = [
        "//dagger-android-proguard-processor/main/java/dagger/android/internal/proguard:__pkg__",
    ],
)

alias(
    name = "plugin",
    actual = "//dagger-android-processor/main/java/dagger/android/processor:plugin",
    visibility = [
        "//:__pkg__",
    ],
)
