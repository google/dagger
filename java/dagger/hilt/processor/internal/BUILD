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
#   Internal code for implementing Hilt processors.

package(default_visibility = ["//:src"])

java_library(
    name = "base_processor",
    srcs = [
        "BaseProcessor.java",
        "ProcessorErrorHandler.java",
    ],
    deps = [
        ":compiler_options",
        ":processor_errors",
        ":processors",
        "//third_party/java/auto:common",
        "//third_party/java/auto:value",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/javapoet",
    ],
)

java_library(
    name = "processor_errors",
    srcs = [
        "BadInputException.java",
        "ErrorTypeException.java",
        "ProcessorErrors.java",
    ],
    deps = [
        "//third_party/java/auto:common",
        "//third_party/java/error_prone:annotations",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/jsr305_annotations",
    ],
)

java_library(
    name = "processors",
    srcs = [
        "AnnotationValues.java",
        "Processors.java",
    ],
    deps = [
        ":classnames",
        ":processor_errors",
        "//java/dagger/hilt/processor/internal/kotlin",
        "//java/dagger/internal/codegen/extension",
        "//third_party/java/auto:common",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/javapoet",
        "@maven//:org_jetbrains_kotlin_kotlin_stdlib",
        "@maven//:org_jetbrains_kotlinx_kotlinx_metadata_jvm",
    ],
)

java_library(
    name = "classnames",
    srcs = [
        "ClassNames.java",
    ],
    deps = [
        "//third_party/java/javapoet",
    ],
)

java_library(
    name = "element_descriptors",
    srcs = [
        "ElementDescriptors.java",
    ],
    deps = [
        "//third_party/java/auto:common",
    ],
)

java_library(
    name = "component_names",
    srcs = [
        "ComponentNames.java",
    ],
    deps = [
        ":processors",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/javapoet",
    ],
)

java_library(
    name = "aggregated_elements",
    srcs = [
        "AggregatedElements.java",
    ],
    deps = [
        ":classnames",
        ":processor_errors",
        ":processors",
        "//java/dagger/internal/codegen/extension",
        "//third_party/java/auto:common",
        "//third_party/java/guava/collect",
        "//third_party/java/javapoet",
    ],
)

java_library(
    name = "component_descriptor",
    srcs = ["ComponentDescriptor.java"],
    deps = [
        "//third_party/java/auto:value",
        "//third_party/java/guava/collect",
        "//third_party/java/javapoet",
    ],
)

java_library(
    name = "components",
    srcs = [
        "Components.java",
    ],
    deps = [
        ":classnames",
        ":component_descriptor",
        ":processor_errors",
        ":processors",
        "//java/dagger/hilt/processor/internal/definecomponent:define_components",
        "//java/dagger/hilt/processor/internal/kotlin",
        "//java/dagger/internal/codegen/extension",
        "//third_party/java/auto:common",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/javapoet",
    ],
)

java_library(
    name = "compiler_options",
    srcs = ["HiltCompilerOptions.java"],
    deps = [
        ":processor_errors",
        "//java/dagger/hilt/processor/internal/optionvalues",
        "//java/dagger/internal/codegen/extension",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/javapoet",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
