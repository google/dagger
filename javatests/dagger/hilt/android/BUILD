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
load("//third_party/kotlin/build_extensions:rules.bzl", "kt_android_library")

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
    javacopts = ["-Adagger.hilt.shareTestComponents=true"],
    manifest = "AndroidManifest.xml",
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:bind_value",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:custom_test_application",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:uninstall_modules",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/dagger",
        "//third_party/java/junit",
        "//third_party/java/robolectric",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "ActivityScenarioRuleTest",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        ":multi_test_root_tests",
    ],
)

android_local_test(
    name = "CustomTestApplicationTest",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        ":multi_test_root_tests",
    ],
)

android_local_test(
    name = "MultiTestRoot1Test",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        ":multi_test_root_tests",
    ],
)

android_local_test(
    name = "MultiTestRoot2Test",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        ":multi_test_root_tests",
    ],
)

android_local_test(
    name = "AliasOfMultipleScopesTest",
    srcs = ["AliasOfMultipleScopesTest.java"],
    manifest = "AndroidManifest.xml",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_rule",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-android/main/java/dagger/hilt/android/qualifiers",
        "//hilt-android/main/java/dagger/hilt/android/scopes",
        "//hilt-core/main/java/dagger/hilt:define_component",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//hilt-core/main/java/dagger/hilt/migration:alias_of",
        "//third_party/java/truth",
    ],
)

android_library(
    name = "custom_inject_classes",
    srcs = ["CustomInjectClasses.java"],
    deps = [
        "//:android_local_test_exports",
        "//hilt-android/main/java/dagger/hilt/android:hilt_android_app",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-android/main/java/dagger/hilt/android/migration:custom_inject",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
    ],
)

android_local_test(
    name = "CustomInjectTest",
    size = "small",
    srcs = ["CustomInjectTest.java"],
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        ":custom_inject_classes",
        "//:android_local_test_exports",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "EarlyEntryPointHiltAndroidAppRuntimeTest",
    size = "small",
    srcs = ["EarlyEntryPointHiltAndroidAppRuntimeTest.java"],
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        ":EarlyEntryPointHiltAndroidAppRuntimeClasses",
        "//:android_local_test_exports",
        "//hilt-android/main/java/dagger/hilt/android:early_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

android_library(
    name = "EarlyEntryPointHiltAndroidAppRuntimeClasses",
    srcs = ["EarlyEntryPointHiltAndroidAppRuntimeClasses.java"],
    deps = [
        "//:android_local_test_exports",
        "//hilt-android/main/java/dagger/hilt/android:early_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:hilt_android_app",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-core/main/java/dagger/hilt:entry_point",
    ],
)

android_local_test(
    name = "EarlyEntryPointHiltAndroidTestRuntimeTest",
    size = "small",
    srcs = [
        "EarlyEntryPointHiltAndroidTestRuntimeClasses.java",
        "EarlyEntryPointHiltAndroidTestRuntimeTest.java",
    ],
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:early_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-core/main/java/dagger/hilt:define_component",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/dagger",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "EarlyEntryPointCustomApplicationTest",
    size = "small",
    srcs = [
        "EarlyEntryPointCustomApplicationClasses.java",
        "EarlyEntryPointCustomApplicationTest.java",
    ],
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:custom_test_application",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:early_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/dagger",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "EarlyEntryPointNoEntryPointsDefinedTest",
    size = "small",
    srcs = ["EarlyEntryPointNoEntryPointsDefinedTest.java"],
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:early_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/dagger",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "FragmentContextOnAttachTest",
    size = "small",
    srcs = ["FragmentContextOnAttachTest.java"],
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:bind_value",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-android/main/java/dagger/hilt/android/flags:fragment_get_context_fix",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/dagger",
        "//third_party/java/truth",
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
    ],
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "ModuleTest",
    srcs = ["ModuleTest.java"],
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "InjectionTest",
    size = "small",
    srcs = ["InjectionTest.java"],
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
        "//third_party/java/truth",
    ],
)

# InstallInObjectModuleTest and InternalKtModuleTest are excluded because they don't build
# due to https://github.com/bazelbuild/bazel/issues/16087

android_local_test(
    name = "DefaultViewModelFactoryTest",
    srcs = ["DefaultViewModelFactoryTest.java"],
    manifest = "AndroidManifest.xml",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:bind_value",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:hilt_view_model",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/androidx/fragment",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "PackagePrivateConstructorTest",
    srcs = ["PackagePrivateConstructorTest.java"],
    manifest = "AndroidManifest.xml",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//javatests/dagger/hilt/android/testsubpackage:PackagePrivateConstructorTestClasses",
    ],
)

android_local_test(
    name = "QualifierInKotlinFieldsTest",
    srcs = ["QualifierInKotlinFieldsTest.java"],
    manifest = "AndroidManifest.xml",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        ":QualifierInFieldsClass",
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//third_party/java/androidx/fragment",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

kt_android_library(
    name = "QualifierInFieldsClass",
    testonly = True,
    srcs = ["QualifierInFieldsClass.kt"],
    deps = [
        "//hilt-android/main/java/dagger/hilt/android/qualifiers",
        "//third_party/java/dagger",
    ],
)

android_local_test(
    name = "OptionalInjectWithHiltTest",
    size = "small",
    srcs = [
        "OptionalInjectWithHiltTest.java",
    ],
    manifest = "AndroidManifest.xml",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        ":OptionalInjectTestClasses",
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-android/main/java/dagger/hilt/android/migration:optional_inject",
        "//third_party/java/androidx/fragment",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "OptionalInjectWithoutHiltTest",
    size = "small",
    srcs = [
        "OptionalInjectWithoutHiltTest.java",
    ],
    manifest = "AndroidManifest.xml",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        ":OptionalInjectTestClasses",
        "//:android_local_test_exports",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-android/main/java/dagger/hilt/android/migration:optional_inject",
        "//third_party/java/androidx/fragment",
        "//third_party/java/truth",
    ],
)

android_library(
    name = "OptionalInjectTestClasses",
    srcs = ["OptionalInjectTestClasses.java"],
    manifest = "AndroidManifest.xml",
    deps = [
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-android/main/java/dagger/hilt/android/migration:optional_inject",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/androidx/fragment",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
    ],
)

android_local_test(
    name = "ActivityRetainedClearedListenerTest",
    srcs = ["ActivityRetainedClearedListenerTest.java"],
    manifest = "AndroidManifest.xml",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:activity_retained_lifecycle",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/androidx/fragment",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "ActivityInjectedViewModelTest",
    srcs = ["ActivityInjectedViewModelTest.java"],
    manifest = "AndroidManifest.xml",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/androidx/fragment",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "ViewModelScopedTest",
    srcs = ["ViewModelScopedTest.java"],
    manifest = "AndroidManifest.xml",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-android/main/java/dagger/hilt/android:view_model_lifecycle",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:hilt_view_model",
        "//hilt-android/main/java/dagger/hilt/android/scopes",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/androidx/fragment",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "ViewModelAssistedTest",
    srcs = ["ViewModelAssistedTest.java"],
    javacopts = ["-Adagger.hilt.enableAssistedInjectViewModels=true"],
    manifest = "AndroidManifest.xml",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-android/main/java/dagger/hilt/android:view_model_lifecycle",
        "//hilt-android/main/java/dagger/hilt/android/internal/lifecycle",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:hilt_view_model",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:hilt_view_model_extensions",
        "//hilt-android/main/java/dagger/hilt/android/scopes",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/android/androidx_test/core",
        "//third_party/java/androidx/fragment",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
        "//third_party/java/truth",
        "//third_party/kotlin/kotlin:kotlin_stdlib",
    ],
)

android_local_test(
    name = "ViewModelWithBaseTest",
    srcs = ["ViewModelWithBaseTest.java"],
    manifest = "AndroidManifest.xml",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:hilt_view_model",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/androidx/fragment",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "ActivityInjectedSavedStateViewModelTest",
    srcs = ["ActivityInjectedSavedStateViewModelTest.java"],
    manifest = "AndroidManifest.xml",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        "//:android_local_test_exports",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

android_library(
    name = "uses_component_common",
    srcs = [
        "UsesComponentHelper.java",
        "UsesComponentTestClasses.java",
    ],
    deps = [
        "//hilt-android/main/java/dagger/hilt/android/components",
        "//hilt-core/main/java/dagger/hilt:define_component",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
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
        "//hilt-android/main/java/dagger/hilt/android/components",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/dagger",
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
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:bind_value",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:custom_test_application",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:uninstall_modules",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
        "//hilt-android/main/java/dagger/hilt/android:hilt_android_app",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
        "//third_party/java/robolectric",
        "//third_party/java/robolectric:annotations",
        "//third_party/java/truth",
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
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:bind_value",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing:hilt_android_test",
        "//hilt-android-testing/main/java/dagger/hilt/testing:test_install_in",
        "//hilt-android/main/java/dagger/hilt/android:hilt_android_app",
        "//hilt-android/main/java/dagger/hilt/android:package_info",
        "//hilt-android/main/java/dagger/hilt/android/components",
        "//hilt-android/main/java/dagger/hilt/android/internal/lifecycle",  # b/247145646
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//third_party/java/dagger",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
        "//third_party/java/robolectric",
        "//third_party/java/robolectric:annotations",
        "//third_party/java/truth",
    ],
)

android_local_test(
    name = "UsesLocalComponentTestBindingsTest",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        ":shared_component_test_classes",
    ],
)

android_local_test(
    name = "UsesLocalComponentUninstallModuleTest",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        ":shared_component_test_classes",
    ],
)

android_local_test(
    name = "UsesSharedComponent1Test",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        ":shared_component_test_classes",
    ],
)

android_local_test(
    name = "UsesSharedComponent2Test",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        ":shared_component_test_classes",
    ],
)

android_local_test(
    name = "UsesSharedComponentEnclosedTest",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        ":shared_component_test_classes",
    ],
)

android_local_test(
    name = "UsesSharedComponentTestInstallInTest",
    manifest_values = TEST_MANIFEST_VALUES,
    deps = [
        ":test_install_in_test_classes",
    ],
)
