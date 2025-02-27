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
#
# Description:
#  Builds and run tests related to AggregatedDepsProcessor.

load("//java/dagger/testing/compile:macros.bzl", "compiler_test")

package(default_visibility = ["//:src"])

compiler_test(
    name = "AggregatedDepsProcessorErrorsTest",
    size = "small",
    srcs = ["AggregatedDepsProcessorErrorsTest.java"],
    compiler_deps = [
        "//java/dagger/hilt/internal:component_entry_point",
        "//java/dagger/hilt/internal:generated_entry_point",
        "//:dagger_with_compiler",
        "//third_party/java/jsr250_annotations",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "//java/dagger/hilt/android/components",
        "@androidsdk//:platforms/android-34/android.jar",
    ],
    deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//java/dagger/hilt/android/testing/compile",
        "//javatests/dagger/hilt/processor/internal:generated_import",
        "//third_party/java/guava/collect",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
