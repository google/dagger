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

load("@rules_java//java:defs.bzl", "java_library", "java_plugin")
load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
    "POM_VERSION",
)
load("//tools/maven:maven.bzl", "gen_maven_artifact")

package(default_visibility = ["//:src"])

filegroup(
    name = "srcs",
    srcs = glob(["*.java"]),
)

java_library(
    name = "base_processing_step",
    srcs = ["BaseProcessingStep.java"],
    deps = [
        "//java/dagger/internal/codegen/extension",
        "//java/dagger/internal/codegen/xprocessing",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/javapoet",
    ],
)

java_library(
    name = "processor",
    srcs = glob(
        ["*.java"],
        exclude = ["BaseProcessingStep.java"],
    ),
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    tags = ["maven_coordinates=com.google.dagger:dagger-android-processor:" + POM_VERSION],
    deps = [
        ":base_processing_step",
        "//java/dagger:core",
        "//java/dagger/internal/codegen/xprocessing",
        "//java/dagger/spi",
        "//third_party/java/auto:service",
        "//third_party/java/auto:value",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/incap",
        "//third_party/java/javapoet",
        "@maven//:com_google_devtools_ksp_symbol_processing_api",
    ],
)

gen_maven_artifact(
    name = "artifact",
    artifact_coordinates = "com.google.dagger:dagger-android-processor:" + POM_VERSION,
    artifact_name = "Dagger Android Processor",
    artifact_target = ":processor",
    artifact_target_libs = [
        "//java/dagger/internal/codegen/xprocessing",
        "//java/dagger/android/processor:base_processing_step",
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
    javadoc_srcs = [":srcs"],
)

java_plugin(
    name = "plugin",
    generates_api = 1,
    processor_class = "dagger.android.processor.AndroidProcessor",
    deps = [":processor"],
)
