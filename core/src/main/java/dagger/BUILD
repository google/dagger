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
    "PRE_JAVA_8_INFERENCE_OPTS",
    "SOURCE_7_TARGET_7",
)

java_library(
    name = "core",
    srcs = glob(["**/*.java"]),
    javacopts = PRE_JAVA_8_INFERENCE_OPTS + SOURCE_7_TARGET_7 + DOCLINT_HTML_AND_SYNTAX,
    exports = ["//third_party:jsr330_inject"],
    deps = ["//third_party:jsr330_inject"],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(["**/*"]),
)

load("//tools:javadoc.bzl", "javadoc_library")

javadoc_library(
    name = "core-javadoc",
    srcs = [":javadoc-srcs"],
    exclude_packages = ["dagger.internal"],
    root_packages = ["dagger"],
    deps = ["//third_party:jsr330_inject"],
)
