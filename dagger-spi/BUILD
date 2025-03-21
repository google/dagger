# Copyright (C) 2018 The Dagger Authors.
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
#   The Service Provider Interface for Dagger's binding graph model

load("@rules_java//java:defs.bzl", "java_library")
load("//:build_defs.bzl", "POM_VERSION")
load("//tools/maven:maven.bzl", "gen_maven_artifact")

package(default_visibility = ["//:src"])

package_group(
    name = "internal",
    packages = [
        "//dagger-spi/...",
    ],
)

ARTIFACT_COORDINATES = "com.google.dagger:dagger-spi:" + POM_VERSION

ARTIFACT_LIBS = [
    "//dagger-spi/main/java/dagger/internal/codegen/extension",
    "//dagger-spi/main/java/dagger/model",
    "//dagger-spi/main/java/dagger/spi",
    "//dagger-spi/main/java/dagger/spi/model",
]

filegroup(
    name = "srcs",
    srcs = [
        "//dagger-spi/main/java/dagger/model:srcs",
        "//dagger-spi/main/java/dagger/spi:srcs",
        "//dagger-spi/main/java/dagger/spi/model:srcs",
    ],
)

# Other artifacts should only depend on this target to ensure that their POM file includes the
# dagger-spi dependency which is found via the maven_coordinates tag.
java_library(
    name = "dagger-spi",
    tags = ["maven_coordinates=" + ARTIFACT_COORDINATES],
    exports = ARTIFACT_LIBS,
)

gen_maven_artifact(
    name = "artifact",
    artifact_coordinates = ARTIFACT_COORDINATES,
    artifact_name = "Dagger SPI",
    artifact_target = ":dagger-spi",
    artifact_target_libs = ARTIFACT_LIBS,
    artifact_target_maven_deps = [
        "com.google.code.findbugs:jsr305",
        "com.google.dagger:dagger",
        "com.google.devtools.ksp:symbol-processing-api",
        "com.google.guava:failureaccess",
        "com.google.guava:guava",
        "com.squareup:javapoet",
        "javax.inject:javax.inject",
    ],
    javadoc_root_packages = [
        "dagger.model",
        "dagger.spi",
    ],
    javadoc_srcs = [":srcs"],
    # The shaded deps are added using jarjar, but they won't be shaded until later
    # due to: https://github.com/google/dagger/issues/2765. For the shaded rules see
    # util/deploy-dagger.sh
    shaded_deps = [
        "//third_party/java/auto:common",
        "//third_party/kotlin/kotlin_metadata_jvm",
        "//third_party/kotlin/kotlinpoet/javapoet",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-jar",
    ],
)
