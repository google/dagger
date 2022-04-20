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

package(default_visibility = ["//:src"])

java_plugin(
    name = "component_tree_deps_plugin",
    generates_api = 1,
    processor_class = "dagger.hilt.processor.internal.root.ComponentTreeDepsProcessor",
    deps = [
        ":component_tree_deps_processor_lib",
    ],
)

java_library(
    name = "component_tree_deps_processor_lib",
    srcs = [
        "ComponentGenerator.java",
        "ComponentTreeDepsProcessor.java",
        "EarlySingletonComponentCreatorGenerator.java",
        "RootFileFormatter.java",
        "RootGenerator.java",
        "TestComponentDataGenerator.java",
    ],
    deps = [
        ":root_metadata",
        "//java/dagger/hilt/android/processor/internal/androidentrypoint:android_generators",
        "//java/dagger/hilt/android/processor/internal/androidentrypoint:metadata",
        "//java/dagger/hilt/processor/internal:base_processor",
        "//java/dagger/hilt/processor/internal:classnames",
        "//java/dagger/hilt/processor/internal:compiler_options",
        "//java/dagger/hilt/processor/internal:component_descriptor",
        "//java/dagger/hilt/processor/internal:component_names",
        "//java/dagger/hilt/processor/internal:processor_errors",
        "//java/dagger/hilt/processor/internal:processors",
        "//java/dagger/hilt/processor/internal/aggregateddeps:component_dependencies",
        "//java/dagger/hilt/processor/internal/aliasof:alias_ofs",
        "//java/dagger/hilt/processor/internal/definecomponent:define_components",
        "//java/dagger/hilt/processor/internal/earlyentrypoint:aggregated_early_entry_point_metadata",
        "//java/dagger/hilt/processor/internal/uninstallmodules:aggregated_uninstall_modules_metadata",
        "//java/dagger/internal/codegen/extension",
        "//third_party/java/auto:common",
        "//third_party/java/auto:service",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/graph",
        "//third_party/java/incap",
        "//third_party/java/javapoet",
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
        "ProcessedRootSentinelGenerator.java",
        "RootProcessor.java",
        "TestInjectorGenerator.java",
    ],
    deps = [
        ":root_metadata",
        ":root_type",
        "//java/dagger/hilt/android/processor/internal/androidentrypoint:android_generators",
        "//java/dagger/hilt/android/processor/internal/androidentrypoint:metadata",
        "//java/dagger/hilt/processor/internal:aggregated_elements",
        "//java/dagger/hilt/processor/internal:base_processor",
        "//java/dagger/hilt/processor/internal:classnames",
        "//java/dagger/hilt/processor/internal:compiler_options",
        "//java/dagger/hilt/processor/internal:processor_errors",
        "//java/dagger/hilt/processor/internal:processors",
        "//java/dagger/hilt/processor/internal/aggregateddeps:component_dependencies",
        "//java/dagger/hilt/processor/internal/aliasof:alias_ofs",
        "//java/dagger/hilt/processor/internal/definecomponent:define_components",
        "//java/dagger/hilt/processor/internal/earlyentrypoint:aggregated_early_entry_point_metadata",
        "//java/dagger/hilt/processor/internal/generatesrootinput:generates_root_inputs",
        "//java/dagger/hilt/processor/internal/root/ir",
        "//java/dagger/hilt/processor/internal/uninstallmodules:aggregated_uninstall_modules_metadata",
        "//java/dagger/internal/codegen/extension",
        "//third_party/java/auto:common",
        "//third_party/java/auto:service",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/incap",
        "//third_party/java/javapoet",
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
        "//java/dagger/hilt/processor/internal:aggregated_elements",
        "//java/dagger/hilt/processor/internal:classnames",
        "//java/dagger/hilt/processor/internal:component_descriptor",
        "//java/dagger/hilt/processor/internal:processor_errors",
        "//java/dagger/hilt/processor/internal:processors",
        "//java/dagger/hilt/processor/internal/aggregateddeps:component_dependencies",
        "//java/dagger/hilt/processor/internal/aliasof:alias_ofs",
        "//java/dagger/hilt/processor/internal/kotlin",
        "//java/dagger/hilt/processor/internal/root/ir",
        "//java/dagger/internal/codegen/extension",
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
        "//java/dagger/hilt/processor/internal:classnames",
        "//java/dagger/hilt/processor/internal:processors",
        "//third_party/java/javapoet",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
