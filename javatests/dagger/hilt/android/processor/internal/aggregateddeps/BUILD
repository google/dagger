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

load("//java/dagger/testing/compile:macros.bzl", "compiler_test")

package(default_visibility = ["//:src"])

compiler_test(
    name = "TestInstallInTest",
    srcs = ["TestInstallInTest.java"],
    compiler_deps = [
        ":InstallInModule",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt/components",
        "//java/dagger/hilt/android/internal/modules",
        "//java/dagger/hilt/testing:test_install_in",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "@androidsdk//:platforms/android-32/android.jar",
    ],
    deps = [
        "//java/dagger/hilt/android/testing/compile",
        "//third_party/java/compile_testing",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

compiler_test(
    name = "EarlyEntryPointProcessorTest",
    srcs = ["EarlyEntryPointProcessorTest.java"],
    compiler_deps = [
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:early_entry_point",
        "//java/dagger/hilt/android/components",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "@androidsdk//:platforms/android-32/android.jar",
    ],
    deps = [
        "//java/dagger/hilt/android/testing/compile",
        "//third_party/java/compile_testing",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

java_library(
    name = "InstallInModule",
    srcs = ["InstallInModule.java"],
    deps = [
        "//:dagger_with_compiler",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/components",
    ],
)
