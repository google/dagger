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
#   Annotation processor for Hilt.

load("@rules_java//java:defs.bzl", "java_library", "java_plugin")
load("//tools:processor_compat.bzl", "compat_processor_plugin")

package(default_visibility = ["//:src"])

compat_processor_plugin(
    name = "component_tree_deps_processor",
    generates_api = 1,
    javac_processor_name = "ComponentTreeDepsProcessor",
    processor_lib_dep = ":component_tree_deps_processor_lib",
)

java_library(
    name = "component_tree_deps_processor_lib",
    srcs = [
        "ComponentGenerator.java",
        "ComponentTreeDepsProcessingStep.java",
        "ComponentTreeDepsProcessor.java",
        "EarlySingletonComponentCreatorGenerator.java",
        "KspComponentTreeDepsProcessor.java",
        "RootFileFormatter.java",
        "RootGenerator.java",
        "TestComponentDataGenerator.java",
    ],
    deps = [
        ":root_metadata",
        "//dagger-compiler:xprocessing",
        "//dagger-spi",
        "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/androidentrypoint:android_generators",
        "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/androidentrypoint:metadata",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:base_processor",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:classnames",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:compiler_options",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:component_descriptor",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:component_names",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:processor_errors",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:processors",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/aggregateddeps:component_dependencies",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/aliasof:alias_ofs",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/definecomponent:define_components",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/earlyentrypoint:aggregated_early_entry_point_metadata",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/uninstallmodules:aggregated_uninstall_modules_metadata",
        "//third_party/java/auto:service",
        "//third_party/java/error_prone:annotations",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/graph",
        "//third_party/java/incap",
        "//third_party/java/javapoet",
        "//third_party/kotlin/kotlin_symbol_processing:symbol-processing-api",
    ],
)

java_plugin(
    name = "root_plugin",
    generates_api = 1,
    processor_class = "dagger.hilt.processor.internal.root.RootProcessor",
    deps = [
        ":root_processor_lib",
    ],
)

java_library(
    name = "root_processor_lib",
    srcs = [
        "AggregatedRootGenerator.java",
        "ComponentTreeDepsGenerator.java",
        "KspRootProcessor.java",
        "ProcessedRootSentinelGenerator.java",
        "RootProcessingStep.java",
        "RootProcessor.java",
        "TestInjectorGenerator.java",
    ],
    deps = [
        ":root_metadata",
        ":root_type",
        "//dagger-compiler:xprocessing",
        "//dagger-spi",
        "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/androidentrypoint:android_generators",
        "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/androidentrypoint:metadata",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:aggregated_elements",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:base_processor",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:classnames",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:compiler_options",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:processor_errors",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:processors",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/aggregateddeps:component_dependencies",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/aliasof:alias_ofs",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/definecomponent:define_components",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/earlyentrypoint:aggregated_early_entry_point_metadata",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/generatesrootinput:generates_root_inputs",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/root/ir",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/uninstallmodules:aggregated_uninstall_modules_metadata",
        "//third_party/java/auto:service",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/incap",
        "//third_party/java/javapoet",
        "//third_party/kotlin/kotlin_symbol_processing:symbol-processing-api",
    ],
)

java_library(
    name = "root_metadata",
    srcs = [
        "AggregatedRootMetadata.java",
        "ComponentTree.java",
        "ComponentTreeDepsMetadata.java",
        "ProcessedRootSentinelMetadata.java",
        "Root.java",
        "RootMetadata.java",
        "TestRootMetadata.java",
    ],
    deps = [
        ":root_type",
        "//dagger-compiler:xprocessing",
        "//dagger-spi",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:aggregated_elements",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:classnames",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:component_descriptor",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:processor_errors",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:processors",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/aggregateddeps:component_dependencies",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/aliasof:alias_ofs",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/kotlin",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/root/ir",
        "//third_party/java/auto:common",
        "//third_party/java/auto:value",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/graph",
        "//third_party/java/javapoet",
    ],
)

java_library(
    name = "root_type",
    srcs = ["RootType.java"],
    deps = [
        "//dagger-compiler:xprocessing",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:classnames",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:processors",
        "//third_party/java/javapoet",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
