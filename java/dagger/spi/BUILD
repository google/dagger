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
load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
    "POM_VERSION",
)
load("//tools/maven:maven.bzl", "gen_maven_artifact")

package(default_visibility = ["//:src"])

filegroup(
    name = "spi-srcs",
    srcs = glob(["*.java"]) + [
        "//java/dagger/model:model-srcs",
        "//java/dagger/spi/model:model-srcs",
    ],
)

java_library(
    name = "spi",
    srcs = glob(["*.java"]),
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    tags = ["maven_coordinates=com.google.dagger:dagger-spi:" + POM_VERSION],
    exports = [
        "//java/dagger/model",
        "//java/dagger/spi/model",
    ],
    deps = [
        "//java/dagger:core",
        "//java/dagger/model",
        "//third_party/java/auto:value",
        "//third_party/java/error_prone:annotations",
    ],
)

gen_maven_artifact(
    name = "artifact",
    artifact_coordinates = "com.google.dagger:dagger-spi:" + POM_VERSION,
    artifact_name = "Dagger SPI",
    artifact_target = ":spi",
    artifact_target_libs = [
        "//java/dagger/internal/codegen/extension",
        "//java/dagger/model",
        "//java/dagger/spi/model",
    ],
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
    javadoc_srcs = [":spi-srcs"],
    # The shaded deps are added using jarjar, but they won't be shaded until later
    # due to: https://github.com/google/dagger/issues/2765. For the shaded rules see
    # util/deploy-dagger.sh
    shaded_deps = [
        "//third_party/java/auto:common",
        "@maven//:org_jetbrains_kotlinx_kotlinx_metadata_jvm",
        "//java/dagger/internal/codegen/xprocessing:xprocessing-jar",
    ],
)
