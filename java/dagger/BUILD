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

load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "SOURCE_7_TARGET_7",
)
load("//tools:maven.bzl", "POM_VERSION", "pom_file")

java_library(
    name = "core",
    srcs = glob(["**/*.java"]),
    javacopts = SOURCE_7_TARGET_7 + DOCLINT_HTML_AND_SYNTAX,
    tags = ["maven_coordinates=com.google.dagger:dagger:" + POM_VERSION],
    exports = ["@google_bazel_common//third_party/java/jsr330_inject"],
    deps = [
        "@google_bazel_common//third_party/java/jsr330_inject",
    ],
)

pom_file(
    name = "pom",
    artifact_id = "dagger",
    artifact_name = "Dagger",
    targets = [":core"],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(["**/*"]),
)

load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")

javadoc_library(
    name = "core-javadoc",
    srcs = [":javadoc-srcs"],
    exclude_packages = ["dagger.internal"],
    root_packages = ["dagger"],
    deps = ["@google_bazel_common//third_party/java/jsr330_inject"],
)
