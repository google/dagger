# Copyright (C) 2020 The Dagger Authors.
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
#   Tests for dagger.internal.codegen.bindinggraphvalidation

load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX")
load("//:test_defs.bzl", "GenJavaTests")

package(default_visibility = ["//:src"])

GenJavaTests(
    name = "bindinggraphvalidation_tests",
    srcs = glob(["*.java"]),
    functional = False,
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    shard_count = 3,
    deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen/bindinggraphvalidation",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//java/dagger/testing/compile",
        "//javatests/dagger/internal/codegen:compilers",
        "//third_party/java/auto:common",
        "//third_party/java/compile_testing",
        "//third_party/java/guava/collect",
        "//third_party/java/javapoet",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)
