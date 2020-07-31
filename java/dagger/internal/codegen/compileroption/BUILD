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

java_library(
    name = "compileroption",
    srcs = glob(["*.java"]),
    plugins = ["//java/dagger/internal/codegen/bootstrap"],
    tags = ["maven:merged"],
    deps = [
        "//java/dagger:core",
        "//java/dagger/internal/codegen/extension",
        "//java/dagger/internal/codegen/langmodel",
        "//java/dagger/internal/guava:base",
        "//java/dagger/internal/guava:collect",
        "//java/dagger/producers",
        "@google_bazel_common//third_party/java/auto:common",
        "@google_bazel_common//third_party/java/google_java_format",
        "@google_bazel_common//third_party/java/jsr330_inject",
    ],
)
