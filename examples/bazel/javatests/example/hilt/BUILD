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

android_local_test(
    name = "CoffeeAppFakePumpTest",
    srcs = [
        "CoffeeAppFakePumpTest.java",
    ],
    manifest_values = {
        "minSdkVersion": "15",
    },
    deps = [
        "//:hilt-android-testing",
        "//java/example/common",
        "//java/example/hilt:heater_module",
        "@maven//:androidx_test_ext_junit",
        "@maven//:androidx_test_runner",
        "@maven//:com_google_truth_truth",
        "@maven//:org_robolectric_annotations",
        "@maven//:org_robolectric_robolectric",
        "@robolectric//bazel:android-all",
    ],
)

android_local_test(
    name = "CoffeeAppFakeHeaterTest",
    srcs = [
        "CoffeeAppFakeHeaterTest.java",
    ],
    manifest_values = {
        "minSdkVersion": "15",
    },
    deps = [
        "//:hilt-android-testing",
        "//java/example/common",
        "//java/example/hilt:pump_module",
        "@maven//:androidx_test_ext_junit",
        "@maven//:androidx_test_runner",
        "@maven//:com_google_truth_truth",
        "@maven//:org_robolectric_annotations",
        "@maven//:org_robolectric_robolectric",
        "@robolectric//bazel:android-all",
    ],
)
