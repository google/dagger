# Copyright (C) 2021 The Dagger Authors.
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
    name = "FragmentContextWrapperLeakTest",
    size = "small",
    srcs = ["FragmentContextWrapperLeakTest.java"],
    manifest = "AndroidManifest.xml",
    manifest_values = {
        "minSdkVersion": "14",
    },
    deps = [
        "//:android_local_test_exports",
        "//:dagger_with_compiler",
        "@google_bazel_common//third_party/java/jsr330_inject",
        "@maven//:junit_junit",
        "@google_bazel_common//third_party/java/truth",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:android_entry_point",
        "//java/dagger/hilt/android/lifecycle",
        "//java/dagger/hilt/android/testing:hilt_android_test",
    ],
)
