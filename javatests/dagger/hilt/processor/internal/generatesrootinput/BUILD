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
#
# Description:
# Tests the functionality of GeneratesRootInputProcessor.

load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX")
load("//:test_defs.bzl", "GenJavaTests")

package(default_visibility = ["//:src"])

GenJavaTests(
    name = "GeneratesRootInputProcessorTest",
    srcs = glob(["*.java"]),
    functional = False,
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    deps = [
        "//:dagger_with_compiler",
        "//java/dagger/hilt:generates_root_input",
        "//java/dagger/hilt/processor/internal:base_processor",
        "//java/dagger/hilt/processor/internal/generatesrootinput:generates_root_inputs",
        "//java/dagger/hilt/processor/internal/generatesrootinput:processor_lib",
        "@google_bazel_common//third_party/java/auto:common",
        "@google_bazel_common//third_party/java/compile_testing",
        "@google_bazel_common//third_party/java/javapoet",
        "@google_bazel_common//third_party/java/junit",
        "@google_bazel_common//third_party/java/truth",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["**/*"]),
)
