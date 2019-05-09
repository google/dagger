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
#   Tests for the Dagger compiler/codegen

package(default_visibility = ["//:src"])

load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX")
load("//:test_defs.bzl", "GenJavaTests")

GenJavaTests(
    name = "compiler_tests",
    srcs = glob(["*.java"]),
    functional = False,
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    deps = [
        "//java/dagger:core",
        "//java/dagger/internal/codegen:base",
        "//java/dagger/internal/codegen:binding",
        "//java/dagger/internal/codegen:binding_graph_validation",
        "//java/dagger/internal/codegen:processor",
        "//java/dagger/internal/codegen:validation",
        "//java/dagger/internal/codegen:writing",
        "//java/dagger/internal/codegen/javapoet",
        "//java/dagger/internal/codegen/langmodel",
        "//java/dagger/internal/codegen/serialization",
        "//java/dagger/model",
        "//java/dagger/model/testing",
        "//java/dagger/producers",
        "//java/dagger/spi",
        "@com_google_auto_value_auto_value//jar",  # For AutoAnnotationProcessor
        "@google_bazel_common//third_party/java/auto:common",
        "@google_bazel_common//third_party/java/auto:value",
        "@google_bazel_common//third_party/java/compile_testing",
        "@google_bazel_common//third_party/java/guava",
        "@google_bazel_common//third_party/java/javapoet",
        "@google_bazel_common//third_party/java/jsr250_annotations",
        "@google_bazel_common//third_party/java/jsr330_inject",
        "@google_bazel_common//third_party/java/junit",
        "@google_bazel_common//third_party/java/mockito",
        "@google_bazel_common//third_party/java/truth",
        "@google_bazel_common//third_party/java/truth:truth8",
    ],
)
