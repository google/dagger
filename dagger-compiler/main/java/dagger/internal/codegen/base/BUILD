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
#   Sources related to compiler options.

load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//dagger-compiler:internal"])

SHARED_SOURCES = [
    "ClearableCache.java",
]

java_library(
    name = "base",
    srcs = glob(
        ["*.java"],
        exclude = SHARED_SOURCES,
    ),
    plugins = ["//dagger-compiler/main/java/dagger/internal/codegen/bootstrap"],
    tags = ["maven:merged"],
    exports = [":shared"],
    deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen/compileroption",
        "//dagger-compiler/main/java/dagger/internal/codegen/model",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xpoet",
        "//dagger-runtime/main/java/dagger:core",
        "//dagger-spi",
        "//third_party/java/auto:value",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/graph",
        "//third_party/java/javapoet",
        "//third_party/java/jsr330_inject:jsr330_inject_without_dagger",
    ],
)

# TODO(bcorso): Remove this target but first remove spi and producers from :base
java_library(
    name = "shared",
    srcs = SHARED_SOURCES,
    tags = ["maven:merged"],
)
