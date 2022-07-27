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

package(default_visibility = ["//:src"])

SHARED_SOURCES = [
    "ClearableCache.java",
]

java_library(
    name = "base",
    srcs = glob(
        ["*.java"],
        exclude = SHARED_SOURCES,
    ),
    plugins = ["//java/dagger/internal/codegen/bootstrap"],
    tags = ["maven:merged"],
    exports = [":shared"],
    deps = [
        "//java/dagger:core",
        "//java/dagger/internal/codegen/compileroption",
        "//java/dagger/internal/codegen/extension",
        "//java/dagger/internal/codegen/javapoet",
        "//java/dagger/internal/codegen/langmodel",
        "//java/dagger/internal/codegen/xprocessing",
        "//java/dagger/spi",
        "//third_party/java/auto:value",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/graph",
        "//third_party/java/javapoet",
        "//third_party/java/jsr330_inject",
        "@maven//:com_google_devtools_ksp_symbol_processing_api",
    ],
)

# TODO(bcorso): Remove this target but first remove spi and producers from :base
java_library(
    name = "shared",
    srcs = SHARED_SOURCES,
    tags = ["maven:merged"],
)
