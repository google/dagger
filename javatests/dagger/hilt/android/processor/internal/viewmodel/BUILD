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
#   Tests for internal code for implementing Hilt processors.

load("@io_bazel_rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")
load("//java/dagger/testing/compile:macros.bzl", "kt_compiler_test")

package(default_visibility = ["//:src"])

kt_compiler_test(
    name = "ViewModelProcessorTest",
    srcs = ["ViewModelProcessorTest.kt"],
    compiler_deps = [
        "//hilt-android/main/java/dagger/hilt/android:hilt_android_app",
        "//third_party/java/android/android_sdk_linux:android_runtime",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:hilt_view_model",
        "//third_party/java/jakarta_inject",
    ],
    deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing/compile",
        "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/viewmodel:processor_lib",
        "//third_party/java/guava/collect",
        "//third_party/java/junit",
    ],
)

kt_compiler_test(
    name = "ViewModelValidationPluginTest",
    srcs = [
        "ViewModelValidationPluginTest.kt",
    ],
    compiler_deps = [
        "//third_party/java/android/android_sdk_linux:android_runtime",
        "//third_party/java/androidx/lifecycle/viewmodel",
        "//third_party/java/androidx/lifecycle/viewmodel_savedstate",
        "//third_party/java/compile_testing",
        "//third_party/java/truth",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:hilt_view_model",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:hilt_android_app",
    ],
    resources = glob(["goldens/*"]),
    deps = [
        ":test_utils",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//dagger-testing/main/java/dagger/testing/golden",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing/compile",
        "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/viewmodel:processor_lib",
        "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/viewmodel:validation_plugin_lib",
        "//third_party/java/compile_testing",
        "//third_party/java/dagger:compiler",
        "//third_party/java/guava/collect",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

kt_compiler_test(
    name = "ViewModelValidationPluginWithAssistedInjectTest",
    srcs = [
        "ViewModelValidationPluginWithAssistedInjectTest.kt",
    ],
    compiler_deps = [
        "//third_party/java/android/android_sdk_linux:android_runtime",
        "//third_party/java/androidx/lifecycle/viewmodel",
        "//third_party/java/androidx/lifecycle/viewmodel_savedstate",
        "//third_party/java/compile_testing",
        "//third_party/java/truth",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:hilt_view_model",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:hilt_android_app",
    ],
    resources = glob(["goldens/*"]),
    deps = [
        ":test_utils",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//dagger-testing/main/java/dagger/testing/golden",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing/compile",
        "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/viewmodel:processor_lib",
        "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/viewmodel:validation_plugin_lib",
        "//third_party/java/compile_testing",
        "//third_party/java/dagger:compiler",
        "//third_party/java/guava/collect",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

kt_jvm_library(
    name = "test_utils",
    srcs = [
        "TestUtils.kt",
    ],
    deps = [
        "//hilt-android-testing/main/java/dagger/hilt/android/testing/compile",
    ],
)
