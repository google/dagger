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

compiler_test(
    name = "UninstallModulesProcessorTest",
    srcs = ["UninstallModulesProcessorTest.java"],
    compiler_deps = [
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:uninstall_modules",
        "//hilt-core/main/java/dagger/hilt/migration:disable_install_in_check",
        "//third_party/java/android/android_sdk_linux:android_runtime",
        "//third_party/java/androidx/annotation",
    ],
    deps = [
        "//hilt-android-testing/main/java/dagger/hilt/android/testing/compile",
        "//third_party/java/junit",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
