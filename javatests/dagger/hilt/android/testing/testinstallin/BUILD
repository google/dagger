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

android_local_test(
    name = "TestInstallInFooTest",
    srcs = ["TestInstallInFooTest.java"],
    manifest_values = {
        "minSdkVersion": "15",
        "targetSdkVersion": "27",
    },
    deps = [
        ":TestInstallInModules",
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "//third_party/java/truth",
        "@maven//:androidx_test_core",
        "@maven//:androidx_test_ext_junit",
        "@maven//:junit_junit",
        "@maven//:org_robolectric_robolectric",
    ],
)

android_local_test(
    name = "TestInstallInBarTest",
    srcs = ["TestInstallInBarTest.java"],
    manifest_values = {
        "minSdkVersion": "15",
        "targetSdkVersion": "27",
    },
    deps = [
        ":TestInstallInModules",
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt/android/testing:hilt_android_test",
        "//java/dagger/hilt/android/testing:uninstall_modules",
        "//java/dagger/hilt/testing:test_install_in",
        "//third_party/java/truth",
        "@maven//:androidx_test_core",
        "@maven//:androidx_test_ext_junit",
        "@maven//:junit_junit",
        "@maven//:org_robolectric_robolectric",
    ],
)

android_local_test(
    name = "TestInstallInAppTest",
    srcs = ["TestInstallInAppTest.java"],
    manifest_values = {
        "minSdkVersion": "15",
        "targetSdkVersion": "27",
    },
    deps = [
        ":TestInstallInApp",
        ":TestInstallInModules",
        "//:android_local_test_exports",
        "//third_party/java/truth",
        "@maven//:androidx_test_core",
        "@maven//:androidx_test_ext_junit",
        "@maven//:junit_junit",
        "@maven//:org_robolectric_robolectric",
    ],
)

android_library(
    name = "TestInstallInApp",
    testonly = True,
    srcs = ["TestInstallInApp.java"],
    deps = [
        ":TestInstallInModules",
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "//java/dagger/hilt/android:hilt_android_app",
    ],
)

android_library(
    name = "TestInstallInModules",
    testonly = True,
    srcs = ["TestInstallInModules.java"],
    deps = [
        "//:dagger_with_compiler",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android/components",
        "//java/dagger/hilt/testing:test_install_in",
    ],
)
