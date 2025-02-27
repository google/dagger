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
#   Hilt android library for binding values in test processors.

load("@rules_java//java:defs.bzl", "java_library", "java_plugin")

package(default_visibility = ["//:src"])

java_plugin(
    name = "bind_value_processor",
    generates_api = 1,
    processor_class = "dagger.hilt.android.processor.internal.bindvalue.BindValueProcessor",
    deps = [
        ":bind_value_processor_lib",
    ],
)

java_library(
    name = "bind_value_processor_lib",
    srcs = [
        "BindValueGenerator.java",
        "BindValueMetadata.java",
        "BindValueProcessingStep.java",
        "BindValueProcessor.java",
        "KspBindValueProcessor.java",
    ],
    deps = [
        "//:dagger_with_compiler",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-spi",
        "//java/dagger/hilt/processor/internal:base_processor",
        "//java/dagger/hilt/processor/internal:classnames",
        "//java/dagger/hilt/processor/internal:components",
        "//java/dagger/hilt/processor/internal:processor_errors",
        "//java/dagger/hilt/processor/internal:processors",
        "//java/dagger/hilt/processor/internal/kotlin",
        "//third_party/java/auto:service",
        "//third_party/java/auto:value",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/incap",
        "//third_party/java/javapoet",
        "@maven//:com_google_devtools_ksp_symbol_processing_api",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
