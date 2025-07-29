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

load("@rules_java//java:defs.bzl", "java_library")
load("//:build_defs.bzl", "POM_VERSION")
load("//tools/maven:maven.bzl", "gen_maven_artifact")

package(default_visibility = ["//:src"])

package_group(
    name = "internal",
    packages = [
        "//dagger-compiler/...",
        "//java/dagger/query/...",
        "//java/dagger/testing/...",
        "//javatests/...",
    ],
)

ARTIFACT_COORDINATES = "com.google.dagger:dagger-compiler:" + POM_VERSION

gen_maven_artifact(
    name = "artifact",
    artifact_coordinates = ARTIFACT_COORDINATES,
    artifact_name = "Dagger Compiler",
    artifact_target = "//dagger-compiler/main/java/dagger/internal/codegen:processor",
    artifact_target_libs = [
        "//dagger-compiler/main/java/dagger/internal/codegen:package_info",
        "//dagger-compiler/main/java/dagger/internal/codegen/base",
        "//dagger-compiler/main/java/dagger/internal/codegen/base:shared",
        "//dagger-compiler/main/java/dagger/internal/codegen/binding",
        "//dagger-compiler/main/java/dagger/internal/codegen/bindinggraphvalidation",
        "//dagger-compiler/main/java/dagger/internal/codegen/compileroption",
        "//dagger-compiler/main/java/dagger/internal/codegen/componentgenerator",
        "//dagger-compiler/main/java/dagger/internal/codegen/kotlin",
        "//dagger-compiler/main/java/dagger/internal/codegen/model",
        "//dagger-compiler/main/java/dagger/internal/codegen/processingstep",
        "//dagger-compiler/main/java/dagger/internal/codegen/validation",
        "//dagger-compiler/main/java/dagger/internal/codegen/writing",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xpoet",
    ],
    artifact_target_maven_deps = [
        "com.google.code.findbugs:jsr305",
        "com.google.dagger:dagger-spi",
        "com.google.dagger:dagger",
        "com.google.devtools.ksp:symbol-processing-api",
        "com.google.googlejavaformat:google-java-format",
        "com.google.guava:failureaccess",
        "com.google.guava:guava",
        "com.squareup:javapoet",
        "com.squareup:kotlinpoet",
        "javax.inject:javax.inject",
        "net.ltgt.gradle.incap:incap",
        "org.checkerframework:checker-compat-qual",
        "org.jetbrains.kotlin:kotlin-metadata-jvm",
        "org.jetbrains.kotlin:kotlin-stdlib",
    ],
    javadoc_root_packages = ["dagger.internal.codegen"],
    # The javadocs should only include ComponentProcessor.java, since that is the only class used
    # externally. Specifically, ComponentProcessor.forTesting() is required for testing SPI plugins.
    javadoc_srcs = [
        "//dagger-compiler/main/java/dagger/internal/codegen:ComponentProcessor.java",
    ],
    # The shaded deps are added using jarjar, but they won't be shaded until later
    # due to: https://github.com/google/dagger/issues/2765. For the shaded rules see
    # util/deploy-dagger.sh
    shaded_deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-jar",
    ],
)

# The targets below expose the some internal libraries to other Dagger artifacts. We add the maven
# coordinates tag to ensure that other artifacts properly depend on this artifact rather than
# bundling these libraries into their own artifacts. We use java_library instead of alias because
# the aliases don't show up in aspects.

java_library(
    name = "binding",
    tags = ["maven_coordinates=" + ARTIFACT_COORDINATES],
    exports = ["//dagger-compiler/main/java/dagger/internal/codegen/binding"],
)

java_library(
    name = "javac",
    tags = ["maven_coordinates=" + ARTIFACT_COORDINATES],
    exports = ["//dagger-compiler/main/java/dagger/internal/codegen/javac"],
)

java_library(
    name = "model",
    tags = ["maven_coordinates=" + ARTIFACT_COORDINATES],
    exports = ["//dagger-compiler/main/java/dagger/internal/codegen/model"],
)

java_library(
    name = "validation",
    tags = ["maven_coordinates=" + ARTIFACT_COORDINATES],
    exports = ["//dagger-compiler/main/java/dagger/internal/codegen/validation"],
)

java_library(
    name = "processor",
    tags = ["maven_coordinates=" + ARTIFACT_COORDINATES],
    exports = ["//dagger-compiler/main/java/dagger/internal/codegen:processor"],
)

java_library(
    name = "xprocessing",
    tags = ["maven_coordinates=" + ARTIFACT_COORDINATES],
    exports = ["//dagger-compiler/main/java/dagger/internal/codegen/xprocessing"],
)

java_library(
    name = "xprocessing-testing",
    testonly = 1,
    tags = ["maven_coordinates=" + ARTIFACT_COORDINATES],
    exports = ["//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing"],
)

alias(
    name = "component-codegen",
    actual = "//dagger-compiler/main/java/dagger/internal/codegen:component-codegen",
    tags = ["maven_coordinates=" + ARTIFACT_COORDINATES],
)
