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
    name = "BindValueTest",
    srcs = ["BindValueTest.java"],
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:bind_value",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//third_party/java/jsr330_inject",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "BindValueIntoMapTest",
    size = "small",
    srcs = ["BindValueIntoMapTest.java"],
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:bind_value",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//third_party/java/auto:value",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "BindValueIntoSetTest",
    size = "small",
    srcs = ["BindValueIntoSetTest.java"],
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:bind_value",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "BindElementsIntoSetTest",
    size = "small",
    srcs = ["BindElementsIntoSetTest.java"],
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:bind_value",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//third_party/java/dagger",
        "//third_party/java/guava/collect",
        "//third_party/java/jsr330_inject",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "TestRootModulesTest",
    size = "small",
    srcs = ["TestRootModulesTest.java"],
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
    ],
)

android_local_test(
    name = "HiltAndroidRuleTest",
    size = "small",
    srcs = ["HiltAndroidRuleTest.java"],
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        ":HiltAndroidRuleTestApp",
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_rule",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//third_party/java/dagger",
        "//third_party/java/guava/collect",
        "//third_party/java/truth",
    ],
)

android_library(
    name = "HiltAndroidRuleTestApp",
    srcs = ["HiltAndroidRuleTestApp.java"],
    deps = [
        "//:android_local_test_exports",
        "//hilt-android/main/java/dagger/hilt/android:hilt_android_app",
    ],
)

android_local_test(
    name = "DelayComponentReadyTest",
    srcs = ["DelayComponentReadyTest.java"],
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:bind_value",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "SkipTestInjectionTest",
    srcs = ["SkipTestInjectionTest.java"],
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:skip_test_injection",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "SkipTestInjectionAnnotationTest",
    srcs = ["SkipTestInjectionAnnotationTest.java"],
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:skip_test_injection",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
        "//third_party/java/truth",
    ],
)
