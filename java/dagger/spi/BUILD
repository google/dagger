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

load("@google_bazel_common//tools/jarjar:jarjar.bzl", "jarjar_library")
load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")

# Description:
#   The Service Provider Interface for Dagger's binding graph model

package(default_visibility = ["//:src"])

load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
)
load("//tools:maven.bzl", "POM_VERSION", "pom_file")

filegroup(
    name = "spi-srcs",
    srcs = glob(["*.java"]),
)

java_library(
    name = "spi",
    srcs = [":spi-srcs"],
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    tags = ["maven_coordinates=com.google.dagger:dagger-spi:" + POM_VERSION],
    exports = [
        "//java/dagger/model",
    ],
    deps = [
        "//java/dagger:core",
        "//java/dagger/model",
        "@google_bazel_common//third_party/java/auto:value",
        "@google_bazel_common//third_party/java/error_prone:annotations",
        "@google_bazel_common//third_party/java/guava",
        "@google_bazel_common//third_party/java/jsr330_inject",
    ],
)

javadoc_library(
    name = "spi-javadoc",
    srcs = [
        ":spi-srcs",
        "//java/dagger/model:model-srcs",
    ],
    root_packages = [
        "dagger.model",
        "dagger.spi",
    ],
    deps = [
        ":spi",
        "//java/dagger/model",
    ],
)

jarjar_library(
    name = "shaded_spi",
    jars = [
        # TODO(user): Look into why this depends on internal/codegen/extension, and how this
        # doesn't break gradle users since maven doesn't know to pull in the codegen dependency.
        "//java/dagger/internal/codegen/extension",
        "//java/dagger/model",
        "//java/dagger/spi",
        "@com_google_auto_auto_common//jar",
    ],
    rules = ["rule com.google.auto.common.** dagger.shaded.auto.common.@1"],
)

jarjar_library(
    name = "shaded_spi_src",
    jars = [
        "//java/dagger/internal/codegen/extension:libextension-src.jar",
        "//java/dagger/model:libmodel-src.jar",
        "//java/dagger/spi:libspi-src.jar",
    ],
)

pom_file(
    name = "pom",
    artifact_id = "dagger-spi",
    artifact_name = "Dagger SPI",
    targets = [
        "//java/dagger/model",
        ":spi",
    ],
)
