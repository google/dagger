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

load("//tools:maven.bzl", "POM_VERSION", "gen_maven_artifact")

package(default_visibility = ["//:src"])

java_library(
    name = "processor",
    srcs = glob(
        ["*.java"],
        exclude = ["package-info.java"],
    ),
    plugins = [
        "//java/dagger/internal/codegen/bootstrap",
    ],
    tags = ["maven_coordinates=com.google.dagger:dagger-compiler:" + POM_VERSION],
    deps = [
        ":package_info",
        "//java/dagger:core",
        "//java/dagger/internal/codegen/base",
        "//java/dagger/internal/codegen/binding",
        "//java/dagger/internal/codegen/bindinggraphvalidation",
        "//java/dagger/internal/codegen/compileroption",
        "//java/dagger/internal/codegen/extension",
        "//java/dagger/internal/codegen/javapoet",
        "//java/dagger/internal/codegen/kotlin",
        "//java/dagger/internal/codegen/langmodel",
        "//java/dagger/internal/codegen/statistics",
        "//java/dagger/internal/codegen/validation",
        "//java/dagger/internal/codegen/writing",
        "//java/dagger/internal/guava:base",
        "//java/dagger/internal/guava:cache",
        "//java/dagger/internal/guava:collect",
        "//java/dagger/internal/guava:concurrent",
        "//java/dagger/internal/guava:graph",
        "//java/dagger/internal/guava:io",
        "//java/dagger/model:internal-proxies",
        "//java/dagger/producers",
        "//java/dagger/spi",
        "@google_bazel_common//third_party/java/auto:common",
        "@google_bazel_common//third_party/java/auto:service",
        "@google_bazel_common//third_party/java/auto:value",
        "@google_bazel_common//third_party/java/checker_framework_annotations",
        "@google_bazel_common//third_party/java/error_prone:annotations",
        "@google_bazel_common//third_party/java/google_java_format",
        "@google_bazel_common//third_party/java/incap",
        "@google_bazel_common//third_party/java/javapoet",
        "@google_bazel_common//third_party/java/jsr250_annotations",
        "@google_bazel_common//third_party/java/jsr330_inject",
    ],
)

java_library(
    name = "package_info",
    srcs = ["package-info.java"],
    tags = ["maven:merged"],
    deps = ["@google_bazel_common//third_party/java/error_prone:annotations"],
)

gen_maven_artifact(
    name = "artifact",
    artifact_id = "dagger-compiler",
    artifact_name = "Dagger Compiler",
    artifact_target = ":processor",
    javadoc_root_packages = ["dagger.internal.codegen"],
    # The javadocs should only include ComponentProcessor.java, since that is the only class used
    # externally. Specifically, ComponentProcessor.forTesting() is required for testing SPI plugins.
    javadoc_srcs = ["ComponentProcessor.java"],
    shaded_deps = ["@com_google_auto_auto_common//jar"],
    shaded_rules = ["rule com.google.auto.common.** dagger.shaded.auto.common.@1"],
    deps = [
        ":package_info",
        "//java/dagger/internal/codegen/base",
        "//java/dagger/internal/codegen/binding",
        "//java/dagger/internal/codegen/bindinggraphvalidation",
        "//java/dagger/internal/codegen/compileroption",
        "//java/dagger/internal/codegen/javapoet",
        "//java/dagger/internal/codegen/kotlin",
        "//java/dagger/internal/codegen/langmodel",
        "//java/dagger/internal/codegen/statistics",
        "//java/dagger/internal/codegen/validation",
        "//java/dagger/internal/codegen/writing",
        "//java/dagger/model:internal-proxies",
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
