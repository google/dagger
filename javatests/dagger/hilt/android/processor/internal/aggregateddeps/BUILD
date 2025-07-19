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

load("@rules_java//java:defs.bzl", "java_library")
load("//java/dagger/testing/compile:macros.bzl", "compiler_test")

compiler_test(
    name = "TestInstallInTest",
    srcs = ["TestInstallInTest.java"],
    compiler_deps = [
        ":InstallInModule",
        "//third_party/java/dagger",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//hilt-core/main/java/dagger/hilt/components",
        "//hilt-android/main/java/dagger/hilt/android/internal/modules",
        "//hilt-android-testing/main/java/dagger/hilt/testing:test_install_in",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//third_party/java/android/android_sdk_linux:android_runtime",
    ],
    deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing/compile",
        "//third_party/java/compile_testing",
        "//third_party/java/junit",
    ],
)

compiler_test(
    name = "EarlyEntryPointProcessorTest",
    srcs = ["EarlyEntryPointProcessorTest.java"],
    compiler_deps = [
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//hilt-android/main/java/dagger/hilt/android:early_entry_point",
        "//hilt-android/main/java/dagger/hilt/android/components",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//third_party/java/android/android_sdk_linux:android_runtime",
    ],
    deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing/compile",
        "//third_party/java/junit",
    ],
)

java_library(
    name = "InstallInModule",
    srcs = ["InstallInModule.java"],
    deps = [
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//hilt-core/main/java/dagger/hilt/components",
        "//third_party/java/dagger",
    ],
)
