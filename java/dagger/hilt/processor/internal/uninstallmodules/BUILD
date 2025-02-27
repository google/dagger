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
#   A processor for @dagger.hilt.android.testing.UninstallModules.

load("@rules_java//java:defs.bzl", "java_library", "java_plugin")

package(default_visibility = ["//:src"])

java_plugin(
    name = "processor",
    generates_api = 1,
    processor_class = "dagger.hilt.processor.internal.uninstallmodules.UninstallModulesProcessor",
    deps = [":processor_lib"],
)

java_library(
    name = "processor_lib",
    srcs = [
        "AggregatedUninstallModulesGenerator.java",
        "KspUninstallModulesProcessor.java",
        "UninstallModulesProcessingStep.java",
        "UninstallModulesProcessor.java",
    ],
    deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-spi",
        "//java/dagger/hilt/processor/internal:base_processor",
        "//java/dagger/hilt/processor/internal:classnames",
        "//java/dagger/hilt/processor/internal:processor_errors",
        "//java/dagger/hilt/processor/internal:processors",
        "//third_party/java/auto:service",
        "//third_party/java/guava/collect",
        "//third_party/java/incap",
        "//third_party/java/javapoet",
        "@maven//:com_google_devtools_ksp_symbol_processing_api",
    ],
)

java_library(
    name = "aggregated_uninstall_modules_metadata",
    srcs = [
        "AggregatedUninstallModulesMetadata.java",
    ],
    deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-spi",
        "//java/dagger/hilt/processor/internal:aggregated_elements",
        "//java/dagger/hilt/processor/internal:classnames",
        "//java/dagger/hilt/processor/internal:processors",
        "//java/dagger/hilt/processor/internal/root/ir",
        "//third_party/java/auto:value",
        "//third_party/java/guava/collect",
        "//third_party/java/javapoet",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
