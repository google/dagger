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

load("@rules_android//rules:rules.bzl", "android_library", "android_local_test")
load("//:build_defs.bzl", "TEST_MANIFEST_VALUES")

package(default_visibility = ["//:src"])

android_local_test(
    name = "TestInstallInFooTest",
    srcs = ["TestInstallInFooTest.java"],
    manifest = "AndroidManifest.xml",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        ":TestInstallInModules",
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//third_party/java/androidx/fragment",
        "//third_party/java/dagger",
        "//third_party/java/junit",
        "//third_party/java/robolectric",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "TestInstallInBarTest",
    srcs = ["TestInstallInBarTest.java"],
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        ":TestInstallInModules",
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:uninstall_modules",
        "//hilt-android-testing/main/java/dagger/hilt/testing:test_install_in",
        "//third_party/java/dagger",
        "//third_party/java/junit",
        "//third_party/java/robolectric",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "TestInstallInAppTest",
    srcs = ["TestInstallInAppTest.java"],
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        ":TestInstallInApp",
        ":TestInstallInModules",
        "//:android_local_test_exports",
        "//third_party/java/junit",
        "//third_party/java/robolectric",
        "//third_party/java/truth",
    ],
)

android_library(
    name = "TestInstallInApp",
    testonly = True,
    srcs = ["TestInstallInApp.java"],
    deps = [
        ":TestInstallInModules",
        "//:android_local_test_exports",
        "//hilt-android/main/java/dagger/hilt/android:hilt_android_app",
        "//third_party/java/dagger",
    ],
)

android_library(
    name = "TestInstallInModules",
    testonly = True,
    srcs = ["TestInstallInModules.java"],
    deps = [
        "//hilt-android-testing/main/java/dagger/hilt/testing:test_install_in",
        "//hilt-android/main/java/dagger/hilt/android/components",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/androidx/fragment",
        "//third_party/java/dagger",
    ],
)
