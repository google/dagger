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
#   A JSR-330 compliant dependency injection system for android and java

package(default_visibility = ["//:src"])

load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")
load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX", "DOCLINT_REFERENCES")
load("//tools:maven.bzl", "POM_VERSION", "pom_file")

EXPERIMENTAL_VISUALIZER_SRCS = ["BindingNetworkVisualizer.java"]

JAVAC_PLUGIN_MODULE_SRCS = ["JavacPluginModule.java"]

KYTHE_SRCS = ["DaggerKythePlugin.java"]

STATISTICS_COLLECTOR_SRCS = ["BindingGraphStatisticsCollector.java"]

CODEGEN_SRCS = glob(
    ["*.java"],
    exclude = EXPERIMENTAL_VISUALIZER_SRCS + KYTHE_SRCS + STATISTICS_COLLECTOR_SRCS +
              JAVAC_PLUGIN_MODULE_SRCS,
)

CODEGEN_PLUGINS = [
    "//java/dagger/internal/codegen/bootstrap",
]

CODEGEN_SHARED_DEPS = [
    "@google_bazel_common//third_party/java/auto:service",
    "@google_bazel_common//third_party/java/auto:value",
    "@google_bazel_common//third_party/java/auto:common",
    "@google_bazel_common//third_party/java/checker_framework_annotations",
    "@google_bazel_common//third_party/java/error_prone:annotations",
    "@google_bazel_common//third_party/java/google_java_format",
    "@google_bazel_common//third_party/java/javapoet",
    "@bazel_tools//tools/jdk:langtools-neverlink",
    "@google_bazel_common//third_party/java/jsr250_annotations",
    "@google_bazel_common//third_party/java/jsr330_inject",
    "//java/dagger:core",
    "//java/dagger/producers",
    "//java/dagger/model",
    "//java/dagger/spi",
    "//java/dagger/model:internal-proxies",
]

CODEGEN_DEPS = CODEGEN_SHARED_DEPS + [
    ":package_info",
    "@google_bazel_common//third_party/java/guava",
]

java_library(
    name = "package_info",
    srcs = ["package-info.java"],
    tags = ["maven:merged"],
    deps = [
        "@google_bazel_common//third_party/java/error_prone:annotations",
    ],
)

# The processor's "main", if you will
java_library(
    name = "processor",
    srcs = [
        "ComponentCreatorImplementationFactory.java",
        "ComponentGenerator.java",
        "ComponentHjarProcessingStep.java",
        "ComponentImplementationBuilder.java",
        "ComponentImplementationFactory.java",
        "ComponentProcessingStep.java",
        "ComponentProcessor.java",
        "CurrentImplementationSubcomponent.java",
        "InjectBindingRegistryImpl.java",
        "InjectBindingRegistryModule.java",
        "InjectProcessingStep.java",
        "MapKeyProcessingStep.java",
        "ModuleProcessingStep.java",
        "ProcessingEnvironmentModule.java",
        "ProcessingRoundCacheModule.java",
        "SourceFileGeneratorsModule.java",
        "SpiModule.java",
        "SystemComponentsModule.java",
        "TopLevelImplementationComponent.java",
    ],
    plugins = CODEGEN_PLUGINS,
    tags = ["maven_coordinates=com.google.dagger:dagger-compiler:" + POM_VERSION],
    deps = CODEGEN_DEPS + [
        "//java/dagger/internal/codegen/base",
        "//java/dagger/internal/codegen/binding",
        "//java/dagger/internal/codegen/bindinggraphvalidation",
        "//java/dagger/internal/codegen/compileroption",
        "//java/dagger/internal/codegen/extension",
        "//java/dagger/internal/codegen/javapoet",
        "//java/dagger/internal/codegen/langmodel",
        "//java/dagger/internal/codegen/statistics",
        "//java/dagger/internal/codegen/validation",
        "//java/dagger/internal/codegen/writing",
        "@google_bazel_common//third_party/java/incap",
    ],
)

pom_file(
    name = "pom",
    artifact_id = "dagger-compiler",
    artifact_name = "Dagger Compiler",
    targets = [
        ":processor",
        "//java/dagger/internal/codegen/base",
        "//java/dagger/internal/codegen/binding",
        "//java/dagger/internal/codegen/bindinggraphvalidation",
        "//java/dagger/internal/codegen/compileroption",
        "//java/dagger/internal/codegen/extension",
        "//java/dagger/internal/codegen/javapoet",
        "//java/dagger/internal/codegen/langmodel",
        "//java/dagger/internal/codegen/statistics",
        "//java/dagger/internal/codegen/validation",
        "//java/dagger/internal/codegen/writing",
    ],
)

java_library(
    name = "javac-plugin-module",
    srcs = JAVAC_PLUGIN_MODULE_SRCS,
    plugins = [":component-codegen"],
    visibility = ["//visibility:private"],
    deps = [
        ":javac",
        "//java/dagger:core",
        "//java/dagger/internal/codegen/binding",
        "//java/dagger/internal/codegen/compileroption",
        "//java/dagger/internal/codegen/langmodel",
    ],
)

java_library(
    name = "kythe",
    srcs = KYTHE_SRCS,
    plugins = [":component-codegen"],
    deps = [
        ":javac",
        ":javac-plugin-module",
        ":kythe_plugin",
        ":package_info",
        ":processor",
        "//java/dagger:core",
        "//java/dagger/internal/codegen/binding",
        "//java/dagger/internal/codegen/langmodel",
        "//java/dagger/model",
        "//java/dagger/producers",
        "@google_bazel_common//third_party/java/auto:common",
        "@google_bazel_common//third_party/java/auto:service",
        "@google_bazel_common//third_party/java/guava",
    ],
)

# Replacement for @bazel_tools//third_party/java/jdk/langtools:javac, which seems to have gone away?
java_import(
    name = "javac",
    jars = ["@bazel_tools//third_party/java/jdk/langtools:javac_jar"],
)

# A _deploy.jar consisting of the java_librarys in https://github.com/kythe/kythe needed to build a
# Kythe plugin
# TODO(ronshapiro): replace this with a http_archive of the next release in
# https://github.com/kythe/kythe/releases
java_import(
    name = "kythe_plugin",
    jars = ["kythe_plugin_deploy.jar"],
    neverlink = 1,
)

javadoc_library(
    name = "codegen-javadoc",
    srcs = CODEGEN_SRCS,
    root_packages = ["dagger.internal.codegen"],
    deps = [":processor"],
)

java_library(
    name = "check-package-javadoc",
    testonly = 1,
    srcs = CODEGEN_SRCS,
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    plugins = CODEGEN_PLUGINS,
    deps = CODEGEN_DEPS + [
        "//java/dagger/internal/codegen/base",
        "//java/dagger/internal/codegen/binding",
        "//java/dagger/internal/codegen/bindinggraphvalidation",
        "//java/dagger/internal/codegen/compileroption",
        "//java/dagger/internal/codegen/extension",
        "//java/dagger/internal/codegen/langmodel",
        "//java/dagger/internal/codegen/javapoet",
        "//java/dagger/internal/codegen/statistics",
        "//java/dagger/internal/codegen/validation",
        "//java/dagger/internal/codegen/writing",
        "@google_bazel_common//third_party/java/incap",
    ],
)

java_plugin(
    name = "component-codegen",
    generates_api = 1,
    output_licenses = ["unencumbered"],
    processor_class = "dagger.internal.codegen.ComponentProcessor",
    tags = [
        "annotation=dagger.Component;" +
        "genclass=${package}.Dagger${outerclasses}${classname}",
        "annotation=dagger.producers.ProductionComponent;" +
        "genclass=${package}.Dagger${outerclasses}${classname}",
    ],
    deps = [":processor"],
)

java_library(
    name = "statistics",
    srcs = STATISTICS_COLLECTOR_SRCS,
    plugins = [":component-codegen"],
    deps = [
        ":javac",
        ":javac-plugin-module",
        ":package_info",
        ":processor",
        "//java/dagger:core",
        "//java/dagger/internal/codegen/base",
        "//java/dagger/internal/codegen/binding",
        "//java/dagger/model",
        "@google_bazel_common//third_party/java/error_prone:check_api",
    ],
)
