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

package(default_visibility = ["//:src"])

java_test(
    name = "ViewModelInjectProcessorTest",
    runtime_deps = [
        ":ViewModelInjectProcessorTestLib",
        "//java/dagger/hilt/android/lifecycle",
        "@androidsdk//:platforms/android-29/android.jar",
        "@google_bazel_common//third_party/java/compile_testing",
        "@google_bazel_common//third_party/java/truth",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
    ],
)

kt_jvm_library(
    name = "ViewModelInjectProcessorTestLib",
    srcs = [
        "TestUtils.kt",
        "ViewModelInjectProcessorTest.kt",
    ],
    deps = [
        "//java/dagger/hilt/android/processor/internal/viewmodelinject:processor_lib",
        "@google_bazel_common//third_party/java/compile_testing",
        "@google_bazel_common//third_party/java/junit",
        "@google_bazel_common//third_party/java/truth",
    ],
)

java_test(
    name = "ViewModelGeneratorTest",
    runtime_deps = [
        ":ViewModelGeneratorTestLib",
        "//java/dagger/hilt/android/lifecycle",
        "@androidsdk//:platforms/android-29/android.jar",
        "@google_bazel_common//third_party/java/compile_testing",
        "@google_bazel_common//third_party/java/truth",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
    ],
)

kt_jvm_library(
    name = "ViewModelGeneratorTestLib",
    srcs = [
        "TestUtils.kt",
        "ViewModelGeneratorTest.kt",
    ],
    deps = [
        "//java/dagger/hilt/android/processor/internal/viewmodelinject:processor_lib",
        "@google_bazel_common//third_party/java/compile_testing",
        "@google_bazel_common//third_party/java/junit",
        "@google_bazel_common//third_party/java/truth",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
