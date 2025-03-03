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
#   Runtime flags to control Hilt behavior for rollout of changes. These flags are usually
#   meant to be temporary and so defaults may change with releases and then these flags
#   may eventually be removed, just like compiler options with similar purposes.

load("@rules_android//rules:rules.bzl", "android_library")

package(default_visibility = ["//:src"])

android_library(
    name = "fragment_get_context_fix",
    srcs = [
        "FragmentGetContextFix.java",
    ],
    deps = [
        "//:dagger_with_compiler",
        "//hilt-android/main/java/dagger/hilt/android:entry_point_accessors",
        "//hilt-android/main/java/dagger/hilt/android/components",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//hilt-core/main/java/dagger/hilt/internal:preconditions",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
