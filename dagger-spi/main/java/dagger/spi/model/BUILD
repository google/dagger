# Copyright (C) 2021 The Dagger Authors.
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
#   Dagger's core APIs exposed for plugins

load("@io_bazel_rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

package(default_visibility = ["//dagger-spi:internal"])

filegroup(
    name = "srcs",
    srcs = glob([
        "*.java",
        "*.kt",
    ]),
)

kt_jvm_library(
    name = "model",
    srcs = [":srcs"],
    # TODO(bcorso): Add javacopts explicitly once kt_jvm_library supports them.
    deps = [
        "//dagger-runtime/main/java/dagger:core",
        "//dagger-spi/main/java/dagger/internal/codegen/extension",
        "//third_party/java/auto:common",
        "//third_party/java/auto:value",
        "//third_party/java/error_prone:annotations",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/graph",
        "//third_party/java/javapoet",
        "//third_party/java/jsr330_inject:jsr330_inject_without_dagger",
        "//third_party/kotlin/kotlin_symbol_processing:symbol-processing-api",
    ],
)

# See: https://github.com/bazelbuild/rules_kotlin/issues/324
alias(
    name = "libmodel-src.jar",
    actual = ":model-sources.jar",
)
