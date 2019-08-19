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
load("//tools:maven.bzl", "POM_VERSION", "pom_file")

DAGGER_DEPS = [
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
]

java_library(
    name = "codegen-shared-deps",
    exported_plugins = [
        "//java/dagger/internal/codegen/bootstrap",
    ],
    visibility = ["//visibility:private"],
    exports = DAGGER_DEPS + [
        ":package_info",
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
        "@google_bazel_common//third_party/java/incap",
    ],
)

java_library(
    name = "processor",
    srcs = [":codegen-srcs"],
    tags = ["maven_coordinates=com.google.dagger:dagger-compiler:" + POM_VERSION],
    deps = [
        ":codegen-shared-deps",
        "@google_bazel_common//third_party/java/guava",
    ],
)

filegroup(
    name = "codegen-srcs",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:private"],
)

java_library(
    name = "package_info",
    srcs = ["package-info.java"],
    tags = ["maven:merged"],
    deps = ["@google_bazel_common//third_party/java/error_prone:annotations"],
)

pom_file(
    name = "pom",
    artifact_id = "dagger-compiler",
    artifact_name = "Dagger Compiler",
    targets = DAGGER_DEPS + [":processor"],
)

# The javadocs should only include ComponentProcessor.java, since that is the only class used
# externally. Specifically, ComponentProcessor.forTesting() is required for testing SPI plugins.
javadoc_library(
    name = "codegen-javadoc",
    srcs = ["ComponentProcessor.java"],
    root_packages = ["dagger.internal.codegen"],
    deps = [":processor"],
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
