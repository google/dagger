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
        "@androidsdk//:platforms/android-34/android.jar",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:hilt_view_model",
    ],
    deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//java/dagger/hilt/android/processor/internal/viewmodel:processor_lib",
        "//java/dagger/hilt/android/testing/compile",
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
        "@androidsdk//:platforms/android-34/android.jar",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
        "//third_party/java/compile_testing",
        "//third_party/java/truth",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:hilt_view_model",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:hilt_android_app",
    ],
    resources = glob(["goldens/*"]),
    deps = [
        ":test_utils",
        "//:compiler_internals",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//java/dagger/hilt/android/processor/internal/viewmodel:processor_lib",
        "//java/dagger/hilt/android/processor/internal/viewmodel:validation_plugin_lib",
        "//java/dagger/hilt/android/testing/compile",
        "//java/dagger/testing/golden",
        "//third_party/java/compile_testing",
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
        "@androidsdk//:platforms/android-34/android.jar",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
        "//third_party/java/compile_testing",
        "//third_party/java/truth",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:hilt_view_model",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:hilt_android_app",
    ],
    resources = glob(["goldens/*"]),
    deps = [
        ":test_utils",
        "//:compiler_internals",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//java/dagger/hilt/android/processor/internal/viewmodel:processor_lib",
        "//java/dagger/hilt/android/processor/internal/viewmodel:validation_plugin_lib",
        "//java/dagger/hilt/android/testing/compile",
        "//java/dagger/testing/golden",
        "//third_party/java/compile_testing",
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
        "//java/dagger/hilt/android/testing/compile",
    ],
)
