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

load("//java/dagger/testing/compile:macros.bzl", "compiler_test")

package(default_visibility = ["//:src"])

compiler_test(
    name = "GeneratesRootInputProcessorTest",
    srcs = ["GeneratesRootInputProcessorTest.java"],
    compiler_deps = [
        "//hilt-core/main/java/dagger/hilt:generates_root_input",
        "//third_party/java/android/android_sdk_linux:android_runtime",
        "//third_party/java/androidx/annotation",
    ],
    deps = [
        "//dagger-compiler:xprocessing",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//dagger-spi",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing/compile",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:base_processor",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/generatesrootinput:generates_root_inputs",
        "//hilt-core/main/java/dagger/hilt:generates_root_input",
        "//third_party/java/javapoet",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["**/*"]),
)
