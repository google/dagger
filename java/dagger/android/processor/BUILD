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

load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")
load("@rules_java//java:defs.bzl", "java_library", "java_plugin")
load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
    "POM_VERSION",
)
load("//tools:maven.bzl", "pom_file")

package(default_visibility = ["//:src"])

filegroup(
    name = "srcs",
    srcs = glob(["*.java"]),
)

java_library(
    name = "processor",
    srcs = [":srcs"],
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    tags = ["maven_coordinates=com.google.dagger:dagger-android-processor:" + POM_VERSION],
    deps = [
        "//java/dagger:core",
        "//java/dagger/internal/codegen/extension",
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

pom_file(
    name = "pom",
    artifact_id = "dagger-android-processor",
    artifact_name = "Dagger Android Processor",
    targets = [":processor"],
)

java_plugin(
    name = "plugin",
    generates_api = 1,
    processor_class = "dagger.android.processor.AndroidProcessor",
    deps = [":processor"],
)

javadoc_library(
    name = "processor-javadoc",
    srcs = [":srcs"],
    root_packages = ["dagger.android.processor"],
    deps = [":processor"],
)
