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

load("//java/dagger/testing/compile:macros.bzl", "compiler_test", "kt_compiler_test")

package(default_visibility = ["//:src"])

compiler_test(
    name = "ActivityGeneratorTest",
    srcs = ["ActivityGeneratorTest.java"],
    compiler_deps = [
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//third_party/java/android/android_sdk_linux:android_runtime",
    ],
    resources = glob([
        "goldens/ActivityGeneratorTest_*",
    ]),
    deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//dagger-testing/main/java/dagger/testing/golden",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing/compile",
        "//third_party/java/junit",
    ],
)

compiler_test(
    name = "AndroidEntryPointProcessorTest",
    srcs = ["AndroidEntryPointProcessorTest.java"],
    compiler_deps = [
        "//hilt-android/main/java/dagger/hilt/android:hilt_android_app",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//third_party/java/android/android_sdk_linux:android_runtime",
    ],
    deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing/compile",
        "//third_party/java/guava/collect",
        "//third_party/java/junit",
    ],
)

kt_compiler_test(
    name = "KotlinAndroidEntryPointProcessorTest",
    srcs = ["KotlinAndroidEntryPointProcessorTest.java"],
    compiler_deps = [
        "//hilt-android/main/java/dagger/hilt/android:hilt_android_app",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//third_party/java/android/android_sdk_linux:android_runtime",
    ],
    deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing/compile",
        "//third_party/java/guava/collect",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)
