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
#   Sources related to Kotlin metadata.

load("//tools:bazel_compat.bzl", "compat_kt_jvm_library")

package(default_visibility = ["//:src"])

compat_kt_jvm_library(
    name = "kotlin",
    srcs = glob([
        "*.java",
        "*.kt",
    ]),
    plugins = ["//dagger-compiler/main/java/dagger/internal/codegen/bootstrap"],
    tags = ["maven:merged"],
    deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen/base:shared",
        "//dagger-compiler/main/java/dagger/internal/codegen/javapoet",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-runtime/main/java/dagger:core",
        "//dagger-spi",
        "//third_party/java/auto:value",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/javapoet",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "//third_party/kotlin/kotlin_metadata_jvm",
        "@maven//:org_jetbrains_kotlin_kotlin_stdlib",
    ],
)
