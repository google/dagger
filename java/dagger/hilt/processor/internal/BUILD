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

load("@io_bazel_rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")
load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//:src"])

java_library(
    name = "base_processor",
    srcs = [
        "BaseProcessingStep.java",
        "JavacBaseProcessingStepProcessor.java",
        "KspBaseProcessingStepProcessor.java",
        "ProcessorErrorHandler.java",
    ],
    deps = [
        ":compiler_options",
        ":hilt_processing_env_configs",
        ":processor_errors",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-spi",
        "//third_party/java/auto:value",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/javapoet",
        "@maven//:com_google_devtools_ksp_symbol_processing_api",
    ],
)

java_library(
    name = "hilt_processing_env_configs",
    srcs = ["HiltProcessingEnvConfigs.java"],
    deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
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
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//third_party/java/error_prone:annotations",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/jsr305_annotations",
    ],
)

java_library(
    name = "processors",
    srcs = [
        "Processors.java",
    ],
    deps = [
        ":classnames",
        ":processor_errors",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-spi",
        "//java/dagger/hilt/processor/internal/kotlin",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/javapoet",
        "@maven//:com_google_devtools_ksp_symbol_processing_api",
        "@maven//:org_jetbrains_kotlin_kotlin_stdlib",
    ],
)

java_library(
    name = "method_signature",
    srcs = [
        "MethodSignature.java",
    ],
    deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-spi",
        "//third_party/java/auto:value",
        "//third_party/java/guava/collect",
        "//third_party/java/javapoet",
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
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-spi",
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
        ":processor_errors",
        ":processors",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-spi",
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
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-spi",
        "//java/dagger/hilt/processor/internal/optionvalues",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/javapoet",
    ],
)

kt_jvm_library(
    name = "dagger_models",
    srcs = ["DaggerModels.kt"],
    deps = [
        ":processors",
        "//dagger-spi",
        "//third_party/java/auto:common",
        "//third_party/java/javapoet",
        "@maven//:com_google_devtools_ksp_symbol_processing_api",
    ],
)

# See: https://github.com/bazelbuild/rules_kotlin/issues/324
alias(
    name = "libdagger_models-src.jar",
    actual = ":dagger_models-sources.jar",
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
