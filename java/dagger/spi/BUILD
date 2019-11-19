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

package(default_visibility = ["//:src"])

load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
)
load("//tools:maven.bzl", "POM_VERSION", "gen_maven_artifact")

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
        "//java/dagger/internal/guava:annotations",
        "//java/dagger/internal/guava:base",
        "//java/dagger/internal/guava:collect",
        "//java/dagger/model",
        "@google_bazel_common//third_party/java/auto:value",
        "@google_bazel_common//third_party/java/error_prone:annotations",
        "@google_bazel_common//third_party/java/jsr330_inject",
    ],
)

gen_maven_artifact(
    name = "artifact",
    artifact_id = "dagger-spi",
    artifact_name = "Dagger SPI",
    artifact_target = ":spi",
    javadoc_root_packages = [
        "dagger.model",
        "dagger.spi",
    ],
    javadoc_srcs = [
        ":spi-srcs",
        "//java/dagger/model:model-srcs",
    ],
    shaded_deps = ["@com_google_auto_auto_common//jar"],
    shaded_rules = ["rule com.google.auto.common.** dagger.shaded.auto.common.@1"],
    deps = [
        "//java/dagger/internal/codegen/extension",
        "//java/dagger/model",
    ],
)
