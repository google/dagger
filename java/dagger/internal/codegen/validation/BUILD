# Copyright (C) 2019 The Dagger Authors.
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
#   Code related to validating the user-written Dagger code

load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//:src"])

java_library(
    name = "validation",
    srcs = glob(["*.java"]),
    plugins = ["//java/dagger/internal/codegen/bootstrap"],
    tags = ["maven:merged"],
    deps = [
        "//java/dagger:core",
        "//java/dagger/internal/codegen/base",
        "//java/dagger/internal/codegen/binding",
        "//java/dagger/internal/codegen/compileroption",
        "//java/dagger/internal/codegen/extension",
        "//java/dagger/internal/codegen/javapoet",
        "//java/dagger/internal/codegen/kotlin",
        "//java/dagger/internal/codegen/langmodel",
        "//java/dagger/internal/codegen/xprocessing",
        "//java/dagger/internal/guava:base",
        "//java/dagger/internal/guava:cache",
        "//java/dagger/internal/guava:collect",
        "//java/dagger/internal/guava:concurrent",
        "//java/dagger/internal/guava:graph",
        "//java/dagger/producers",
        "//java/dagger/spi",
        "@google_bazel_common//third_party/java/auto:common",
        "@google_bazel_common//third_party/java/auto:value",
        "@google_bazel_common//third_party/java/checker_framework_annotations",
        "@google_bazel_common//third_party/java/error_prone:annotations",
        "@google_bazel_common//third_party/java/javapoet",
        "@google_bazel_common//third_party/java/jsr330_inject",
    ],
)
