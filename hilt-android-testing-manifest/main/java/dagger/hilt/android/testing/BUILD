# Copyright (C) 2026 The Dagger Authors.
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

load("@rules_android//rules:rules.bzl", "android_library")

package(default_visibility = ["//:src"])

android_library(
    name = "hilt_component_activity",
    testonly = 1,
    srcs = ["HiltComponentActivity.java"],
    deps = [
        "@maven//:androidx_activity_activity",
        "//hilt-android/main/java/dagger/hilt/android:android_entry_point",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
