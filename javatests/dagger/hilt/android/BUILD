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

load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kt_android_library")

package(default_visibility = ["//:src"])

# Checks that multiple test roots can be compiled together. This library
# only compiles the sources. they are tested in the android_local_tests.
android_library(
    name = "multi_test_root_tests",
    srcs = [
        "ActivityScenarioRuleTest.java",
        "CustomTestApplicationTest.java",
        "MultiTestRoot1Test.java",
        "MultiTestRoot2Test.java",
        "MultiTestRootExternalModules.java",
    ],
    exports_manifest = 1,
    manifest = "AndroidManifest.xml",
    deps = [
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:android_entry_point",
        "//java/dagger/hilt/android:package_info",
        "//java/dagger/hilt/android/testing:bind_value",
        "//java/dagger/hilt/android/testing:custom_test_application",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "//java/dagger/hilt/android/testing:uninstall_modules",
        "@google_bazel_common//third_party/java/truth",
        "@maven//:androidx_test_core",
        "@maven//:androidx_test_ext_junit",
        "@maven//:junit_junit",
        "@maven//:org_robolectric_robolectric",
    ],
)

android_local_test(
    name = "ActivityScenarioRuleTest",
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        ":multi_test_root_tests",
    ],
)

android_local_test(
    name = "CustomTestApplicationTest",
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        ":multi_test_root_tests",
    ],
)

android_local_test(
    name = "MultiTestRoot1Test",
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        ":multi_test_root_tests",
    ],
)

android_local_test(
    name = "MultiTestRoot2Test",
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        ":multi_test_root_tests",
    ],
)

android_local_test(
    name = "EarlyEntryPointHiltAndroidAppRuntimeTest",
    size = "small",
    srcs = ["EarlyEntryPointHiltAndroidAppRuntimeTest.java"],
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        ":EarlyEntryPointHiltAndroidAppRuntimeClasses",
        "//:android_local_test_exports",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt/android:early_entry_point",
        "//java/dagger/hilt/android:package_info",
        "@google_bazel_common//third_party/java/truth",
        "@maven//:junit_junit",
    ],
)

android_library(
    name = "EarlyEntryPointHiltAndroidAppRuntimeClasses",
    srcs = ["EarlyEntryPointHiltAndroidAppRuntimeClasses.java"],
    deps = [
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt/android:early_entry_point",
        "//java/dagger/hilt/android:hilt_android_app",
        "//java/dagger/hilt/android:package_info",
    ],
)

android_local_test(
    name = "EarlyEntryPointHiltAndroidTestRuntimeTest",
    size = "small",
    srcs = [
        "EarlyEntryPointHiltAndroidTestRuntimeClasses.java",
        "EarlyEntryPointHiltAndroidTestRuntimeTest.java",
    ],
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:define_component",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:early_entry_point",
        "//java/dagger/hilt/android:package_info",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "@google_bazel_common//third_party/java/truth",
    ],
)

android_local_test(
    name = "EarlyEntryPointCustomApplicationTest",
    size = "small",
    srcs = [
        "EarlyEntryPointCustomApplicationClasses.java",
        "EarlyEntryPointCustomApplicationTest.java",
    ],
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:early_entry_point",
        "//java/dagger/hilt/android:package_info",
        "//java/dagger/hilt/android/testing:custom_test_application",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "@google_bazel_common//third_party/java/truth",
    ],
)

android_local_test(
    name = "EarlyEntryPointNoEntryPointsDefinedTest",
    size = "small",
    srcs = ["EarlyEntryPointNoEntryPointsDefinedTest.java"],
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:early_entry_point",
        "//java/dagger/hilt/android:package_info",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "@google_bazel_common//third_party/java/truth",
    ],
)

android_local_test(
    name = "FragmentContextOnAttachTest",
    size = "small",
    srcs = ["FragmentContextOnAttachTest.java"],
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:android_entry_point",
        "//java/dagger/hilt/android:package_info",
        "//java/dagger/hilt/android/testing:bind_value",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "@google_bazel_common//third_party/java/jsr330_inject",
        "@google_bazel_common//third_party/java/truth",
    ],
)

android_local_test(
    name = "AndroidEntryPointBaseClassTest",
    size = "small",
    srcs = [
        "AndroidEntryPointBaseClassOtherPkg.java",
        "AndroidEntryPointBaseClassTest.java",
    ],
    javacopts = [
        # Note: Hilt's bytecode injection doesn't work in Blaze but we disable
        # superclass validation in this test just to verify everything builds.
        "-Adagger.hilt.android.internal.disableAndroidSuperclassValidation=true",
        # Note: Used to test base classes across java packages.
        "-Xep:PackageLocation:OFF",
    ],
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:android_entry_point",
        "//java/dagger/hilt/android:package_info",
        "@google_bazel_common//third_party/java/truth",
    ],
)

android_local_test(
    name = "ModuleTest",
    srcs = ["ModuleTest.java"],
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:package_info",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "@google_bazel_common//third_party/java/jsr330_inject",
        "@google_bazel_common//third_party/java/truth",
    ],
)

android_local_test(
    name = "InjectionTest",
    size = "small",
    srcs = ["InjectionTest.java"],
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:android_entry_point",
        "//java/dagger/hilt/android:package_info",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "@google_bazel_common//third_party/java/jsr330_inject",
        "@google_bazel_common//third_party/java/truth",
    ],
)

android_local_test(
    name = "InstallInObjectModuleTest",
    srcs = ["InstallInObjectModuleTest.java"],
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        ":InstallInObjectModuleClasses",
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt/android:android_entry_point",
        "//java/dagger/hilt/android:package_info",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "@google_bazel_common//third_party/java/junit",
        "@google_bazel_common//third_party/java/truth",
    ],
)

kt_android_library(
    name = "InstallInObjectModuleClasses",
    testonly = True,
    srcs = ["InstallInObjectModule.kt"],
    deps = [
        "//:dagger_with_compiler",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/components",
    ],
)

android_local_test(
    name = "InternalKtModuleTest",
    size = "small",
    srcs = ["InternalKtModuleTest.java"],
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:package_info",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "//javatests/dagger/hilt/testmodules",
        "@google_bazel_common//third_party/java/truth",
    ],
)

android_local_test(
    name = "DefaultViewModelFactoryTest",
    srcs = ["DefaultViewModelFactoryTest.java"],
    manifest = "AndroidManifest.xml",
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:android_entry_point",
        "//java/dagger/hilt/android:package_info",
        "//java/dagger/hilt/android/lifecycle",
        "//java/dagger/hilt/android/testing:bind_value",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "@google_bazel_common//third_party/java/jsr330_inject",
        "@google_bazel_common//third_party/java/truth",
        "@maven//:androidx_activity_activity",
        "@maven//:androidx_fragment_fragment",
        "@maven//:androidx_lifecycle_lifecycle_common",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
        "@maven//:junit_junit",
    ],
)

android_local_test(
    name = "QualifierInKotlinFieldsTest",
    srcs = ["QualifierInKotlinFieldsTest.java"],
    manifest = "AndroidManifest.xml",
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        ":QualifierInFieldsClass",
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt/android:android_entry_point",
        "//java/dagger/hilt/android:package_info",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "@google_bazel_common//third_party/java/jsr330_inject",
        "@google_bazel_common//third_party/java/truth",
        "@maven//:androidx_activity_activity",
        "@maven//:androidx_fragment_fragment",
        "@maven//:androidx_lifecycle_lifecycle_common",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
        "@maven//:junit_junit",
    ],
)

kt_android_library(
    name = "QualifierInFieldsClass",
    testonly = True,
    srcs = ["QualifierInFieldsClass.kt"],
    deps = [
        "//:dagger_with_compiler",
        "//java/dagger/hilt/android/qualifiers",
    ],
)

android_local_test(
    name = "ActivityRetainedClearedListenerTest",
    srcs = ["ActivityRetainedClearedListenerTest.java"],
    manifest = "AndroidManifest.xml",
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:activity_retained_lifecycle",
        "//java/dagger/hilt/android:android_entry_point",
        "//java/dagger/hilt/android:package_info",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "@google_bazel_common//third_party/java/jsr330_inject",
        "@google_bazel_common//third_party/java/truth",
        "@maven//:androidx_activity_activity",
        "@maven//:androidx_fragment_fragment",
        "@maven//:androidx_lifecycle_lifecycle_common",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
        "@maven//:junit_junit",
    ],
)

android_local_test(
    name = "ActivityInjectedViewModelTest",
    srcs = ["ActivityInjectedViewModelTest.java"],
    manifest = "AndroidManifest.xml",
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:android_entry_point",
        "//java/dagger/hilt/android:package_info",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "@google_bazel_common//third_party/java/jsr330_inject",
        "@google_bazel_common//third_party/java/truth",
        "@maven//:androidx_activity_activity",
        "@maven//:androidx_fragment_fragment",
        "@maven//:androidx_lifecycle_lifecycle_common",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
        "@maven//:junit_junit",
    ],
)

android_local_test(
    name = "ViewModelScopedTest",
    srcs = ["ViewModelScopedTest.java"],
    manifest = "AndroidManifest.xml",
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:android_entry_point",
        "//java/dagger/hilt/android:package_info",
        "//java/dagger/hilt/android/lifecycle",
        "//java/dagger/hilt/android/scopes",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "@google_bazel_common//third_party/java/jsr330_inject",
        "@google_bazel_common//third_party/java/truth",
        "@maven//:androidx_activity_activity",
        "@maven//:androidx_fragment_fragment",
        "@maven//:androidx_lifecycle_lifecycle_common",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
        "@maven//:junit_junit",
    ],
)

android_local_test(
    name = "ViewModelWithBaseTest",
    srcs = ["ViewModelWithBaseTest.java"],
    manifest = "AndroidManifest.xml",
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:android_entry_point",
        "//java/dagger/hilt/android:package_info",
        "//java/dagger/hilt/android/lifecycle",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "@google_bazel_common//third_party/java/jsr330_inject",
        "@google_bazel_common//third_party/java/truth",
        "@maven//:androidx_activity_activity",
        "@maven//:androidx_fragment_fragment",
        "@maven//:androidx_lifecycle_lifecycle_common",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
        "@maven//:junit_junit",
    ],
)

android_local_test(
    name = "ActivityInjectedSavedStateViewModelTest",
    srcs = ["ActivityInjectedSavedStateViewModelTest.java"],
    manifest = "AndroidManifest.xml",
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:android_entry_point",
        "//java/dagger/hilt/android:package_info",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "//java/dagger/internal/guava:base-android",
        "@google_bazel_common//third_party/java/jsr330_inject",
        "@google_bazel_common//third_party/java/truth",
        "@maven//:junit_junit",
    ],
)

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
