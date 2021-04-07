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

package(default_visibility = ["//:src"])

# Checks that multiple test roots can be compiled together. This library
# only compiles the sources. they are tested in the android_local_tests.

android_library(
    name = "uses_component_common",
    srcs = [
        "UsesComponentHelper.java",
        "UsesComponentTestClasses.java",
    ],
    deps = [
        "//:dagger_with_compiler",
        "//java/dagger/hilt:define_component",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android/components",
        "@google_bazel_common//third_party/java/jsr330_inject",
    ],
)

# This has to be split into a separate target in order to avoid
# being compiled as "test bindings" in the compilation unit of
# the test class itself.
android_library(
    name = "uses_component_test_module",
    srcs = ["UsesComponentTestModule.java"],
    deps = [
        ":uses_component_common",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android/components",
    ],
)

android_library(
    name = "shared_component_test_classes",
    srcs = [
        "UsesLocalComponentTestBindingsTest.java",
        "UsesLocalComponentUninstallModuleTest.java",
        "UsesSharedComponent1Test.java",
        "UsesSharedComponent2Test.java",
        "UsesSharedComponentEnclosedTest.java",
        "//javatests/dagger/hilt/android/testsubpackage:UsesLocalComponentTestBindingsTest.java",
        "//javatests/dagger/hilt/android/testsubpackage:UsesSharedComponent1Test.java",
    ],
    exports_manifest = 1,
    javacopts = ["-Adagger.hilt.shareTestComponents=true"],
    manifest = "AndroidManifest.xml",
    deps = [
        ":uses_component_common",
        ":uses_component_test_module",
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:android_entry_point",
        "//java/dagger/hilt/android:hilt_android_app",
        "//java/dagger/hilt/android:package_info",
        "//java/dagger/hilt/android/testing:bind_value",
        "//java/dagger/hilt/android/testing:custom_test_application",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "//java/dagger/hilt/android/testing:uninstall_modules",
        "@google_bazel_common//third_party/java/jsr330_inject",
        "@google_bazel_common//third_party/java/truth",
        "@maven//:androidx_test_core",
        "@maven//:androidx_test_ext_junit",
        "@maven//:junit_junit",
        "@maven//:org_robolectric_annotations",
        "@maven//:org_robolectric_robolectric",
    ],
)

# Separate target that uses @TestInstallIn to replace the global binding
android_library(
    name = "test_install_in_test_classes",
    srcs = [
        "TestInstallInModules.java",
        "UsesSharedComponentTestInstallInTest.java",
    ],
    javacopts = ["-Adagger.hilt.shareTestComponents=true"],
    deps = [
        ":uses_component_common",
        ":uses_component_test_module",
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:hilt_android_app",
        "//java/dagger/hilt/android:package_info",
        "//java/dagger/hilt/android/components",
        "//java/dagger/hilt/android/testing:bind_value",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "//java/dagger/hilt/testing:test_install_in",
        "@google_bazel_common//third_party/java/jsr330_inject",
        "@google_bazel_common//third_party/java/truth",
        "@maven//:androidx_test_core",
        "@maven//:androidx_test_ext_junit",
        "@maven//:junit_junit",
        "@maven//:org_robolectric_annotations",
        "@maven//:org_robolectric_robolectric",
    ],
)

android_local_test(
    name = "UsesLocalComponentTestBindingsTest",
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        ":shared_component_test_classes",
    ],
)

android_local_test(
    name = "UsesLocalComponentUninstallModuleTest",
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        ":shared_component_test_classes",
    ],
)

android_local_test(
    name = "UsesSharedComponent1Test",
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        ":shared_component_test_classes",
    ],
)

android_local_test(
    name = "UsesSharedComponent2Test",
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        ":shared_component_test_classes",
    ],
)

android_local_test(
    name = "UsesSharedComponentEnclosedTest",
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        ":shared_component_test_classes",
    ],
)

android_local_test(
    name = "UsesSharedComponentTestInstallInTest",
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        ":test_install_in_test_classes",
    ],
)
