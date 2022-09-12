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

load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kt_jvm_library")
load("//java/dagger/testing/compile:macros.bzl", "kt_compiler_test")

package(default_visibility = ["//:src"])

java_test(
    name = "ViewModelProcessorTest",
    runtime_deps = [
        ":ViewModelProcessorTestLib",
        "//java/dagger/hilt/android/lifecycle:hilt_view_model",
        "//third_party/java/compile_testing",
        "//third_party/java/truth",
        "@androidsdk//:platforms/android-32/android.jar",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
    ],
)

kt_jvm_library(
    name = "ViewModelProcessorTestLib",
    srcs = [
        "ViewModelProcessorTest.kt",
    ],
    deps = [
        ":test_utils",
        "//java/dagger/hilt/android/processor/internal/viewmodel:processor_lib",
        "//third_party/java/compile_testing",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

java_test(
    name = "ViewModelGeneratorTest",
    runtime_deps = [
        ":ViewModelGeneratorTestLib",
        "//java/dagger/hilt/android/lifecycle:hilt_view_model",
        "//third_party/java/compile_testing",
        "//third_party/java/truth",
        "@androidsdk//:platforms/android-32/android.jar",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
    ],
)

kt_jvm_library(
    name = "ViewModelGeneratorTestLib",
    srcs = [
        "ViewModelGeneratorTest.kt",
    ],
    deps = [
        ":test_utils",
        "//java/dagger/hilt/android/processor/internal/viewmodel:processor_lib",
        "//third_party/java/compile_testing",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

kt_compiler_test(
    name = "ViewModelValidationPluginTest",
    srcs = [
        "ViewModelValidationPluginTest.kt",
    ],
    compiler_deps = [
        "@androidsdk//:platforms/android-32/android.jar",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
        "//third_party/java/compile_testing",
        "//third_party/java/truth",
        "//java/dagger/hilt/android/lifecycle:hilt_view_model",
        "//java/dagger/hilt/android:android_entry_point",
        "//java/dagger/hilt/android:hilt_android_app",
    ],
    deps = [
        ":test_utils",
        "//:compiler_internals",
        "//java/dagger/hilt/android/processor/internal/viewmodel:processor_lib",
        "//java/dagger/hilt/android/processor/internal/viewmodel:validation_plugin_lib",
        "//java/dagger/hilt/android/testing/compile",
        "//third_party/java/compile_testing",
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
        "//third_party/java/compile_testing",
    ],
)
