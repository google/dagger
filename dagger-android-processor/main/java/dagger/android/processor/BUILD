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

load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
    "POM_VERSION",
)
load("//tools:processor_compat.bzl", "compat_processor_plugin")

package(default_visibility = [
    "//dagger-android-processor:internal",
])

filegroup(
    name = "srcs",
    srcs = glob(["*.java"]),
)

java_library(
    name = "base_processing_step",
    srcs = ["BaseProcessingStep.java"],
    deps = [
        "//dagger-compiler:xprocessing",
        "//dagger-spi",
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
        "//dagger-compiler:xprocessing",
        "//dagger-runtime/main/java/dagger:core",
        "//dagger-spi",
        "//third_party/java/auto:service",
        "//third_party/java/auto:value",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/incap",
        "//third_party/java/javapoet",
        "//third_party/kotlin/kotlin_symbol_processing:symbol-processing-api",
    ],
)

compat_processor_plugin(
    name = "plugin",
    generates_api = 1,
    javac_processor_name = "AndroidProcessor",
    processor_lib_dep = ":processor",
)
