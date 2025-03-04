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
#   Internal Proguard Processor

load("@rules_java//java:defs.bzl", "java_library", "java_plugin")
load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX", "DOCLINT_REFERENCES")

package(default_visibility = ["//:src"])

java_library(
    name = "proguard-processor",
    srcs = [
        "KspProguardProcessor.java",
        "ProguardProcessingStep.java",
        "ProguardProcessor.java",
    ],
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    deps = [
        "//dagger-android-processor:base_processing_step",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//third_party/java/auto:service",
        "//third_party/java/guava/collect",
        "//third_party/java/javapoet",
        "@maven//:com_google_devtools_ksp_symbol_processing_api",
    ],
)

java_plugin(
    name = "plugin",
    generates_api = 1,
    processor_class = "dagger.android.internal.proguard.ProguardProcessor",
    deps = [":proguard-processor"],
)
