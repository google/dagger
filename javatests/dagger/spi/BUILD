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
#   Tests for the Dagger SPI

load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
)
load("//:test_defs.bzl", "GenCompilerTests")

java_library(
    name = "failing_plugin",
    srcs = ["FailingPlugin.java"],
    deps = [
        "//dagger-spi",
        "//third_party/java/auto:service",
        "//third_party/java/guava/collect",
    ],
)

GenCompilerTests(
    name = "spi_tests",
    srcs = glob(
        ["*.java"],
        exclude = ["FailingPlugin.java"],
    ),
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    deps = [
        ":failing_plugin",
        "//dagger-compiler/main/java/dagger/internal/codegen:processor",
        "//dagger-runtime/main/java/dagger:core",
        "//dagger-spi",
        "//third_party/java/compile_testing",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)
