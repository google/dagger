# Copyright (C) 2019 The Dagger Authors.
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
#   A processor that aggregates metadata about Hilt @InstallIn annotations

load("@rules_java//java:defs.bzl", "java_library")
load("//tools:processor_compat.bzl", "compat_processor_plugin")

package(default_visibility = ["//:src"])

compat_processor_plugin(
    name = "plugin",
    generates_api = 1,
    javac_processor_name = "AggregatedDepsProcessor",
    processor_lib_dep = ":processor_lib",
)

java_library(
    name = "processor_lib",
    srcs = [
        "AggregatedDepsGenerator.java",
        "AggregatedDepsProcessingStep.java",
        "AggregatedDepsProcessor.java",
        "KspAggregatedDepsProcessor.java",
        "PkgPrivateEntryPointGenerator.java",
        "PkgPrivateModuleGenerator.java",
    ],
    deps = [
        ":pkg_private_metadata",
        "//dagger-compiler:xprocessing",
        "//dagger-spi",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:base_processor",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:classnames",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:compiler_options",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:components",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:processor_errors",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:processors",
        "//third_party/java/auto:service",
        "//third_party/java/guava/collect",
        "//third_party/java/incap",
        "//third_party/java/javapoet",
        "//third_party/kotlin/kotlin_symbol_processing:symbol-processing-api",
    ],
)

java_library(
    name = "pkg_private_metadata",
    srcs = ["PkgPrivateMetadata.java"],
    deps = [
        "//dagger-compiler:xprocessing",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:classnames",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:processors",
        "//third_party/java/auto:value",
        "//third_party/java/javapoet",
        "//third_party/kotlin/kotlin_symbol_processing:symbol-processing-api",
    ],
)

java_library(
    name = "component_dependencies",
    srcs = [
        "AggregatedDepsMetadata.java",
        "ComponentDependencies.java",
    ],
    deps = [
        ":pkg_private_metadata",
        "//dagger-compiler:xprocessing",
        "//dagger-spi",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:aggregated_elements",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:classnames",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:component_descriptor",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:processors",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/earlyentrypoint:aggregated_early_entry_point_metadata",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/root/ir",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/uninstallmodules:aggregated_uninstall_modules_metadata",
        "//third_party/java/auto:value",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/javapoet",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
