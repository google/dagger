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

package(default_visibility = ["//:src"])

# TODO(bcorso): Remove all AggregatedDeps usage from the processor class path.
java_library(
    name = "annotation",
    srcs = ["AggregatedDeps.java"],
    exports = [
        "//java/dagger/hilt/codegen:originating_element",
    ],
)

java_plugin(
    name = "plugin",
    generates_api = 1,
    processor_class = "dagger.hilt.processor.internal.aggregateddeps.AggregatedDepsProcessor",
    deps = [":processor_lib"],
)

java_library(
    name = "processor_lib",
    srcs = [
        "AggregatedDepsGenerator.java",
        "AggregatedDepsProcessor.java",
        "PkgPrivateEntryPointGenerator.java",
        "PkgPrivateModuleGenerator.java",
    ],
    deps = [
        ":pkg_private_metadata",
        "//java/dagger/hilt/processor/internal:base_processor",
        "//java/dagger/hilt/processor/internal:classnames",
        "//java/dagger/hilt/processor/internal:compiler_options",
        "//java/dagger/hilt/processor/internal:components",
        "//java/dagger/hilt/processor/internal:processor_errors",
        "//java/dagger/hilt/processor/internal:processors",
        "//java/dagger/internal/codegen/extension",
        "//third_party/java/auto:common",
        "//third_party/java/auto:service",
        "//third_party/java/guava/collect",
        "//third_party/java/incap",
        "//third_party/java/javapoet",
    ],
)

java_library(
    name = "pkg_private_metadata",
    srcs = ["PkgPrivateMetadata.java"],
    deps = [
        "//java/dagger/hilt/processor/internal:classnames",
        "//java/dagger/hilt/processor/internal:processors",
        "//java/dagger/hilt/processor/internal/kotlin",
        "//third_party/java/auto:common",
        "//third_party/java/auto:value",
        "//third_party/java/javapoet",
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
        "//java/dagger/hilt/processor/internal:aggregated_elements",
        "//java/dagger/hilt/processor/internal:classnames",
        "//java/dagger/hilt/processor/internal:component_descriptor",
        "//java/dagger/hilt/processor/internal:processors",
        "//java/dagger/hilt/processor/internal/earlyentrypoint:aggregated_early_entry_point_metadata",
        "//java/dagger/hilt/processor/internal/root/ir",
        "//java/dagger/hilt/processor/internal/uninstallmodules:aggregated_uninstall_modules_metadata",
        "//java/dagger/internal/codegen/extension",
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
