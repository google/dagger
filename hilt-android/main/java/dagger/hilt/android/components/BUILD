# Copyright (C) 2019 The Dagger Authors.
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
#   Hilt Android components

load("@rules_android//rules:rules.bzl", "android_library")
load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//:src"])

android_library(
    name = "components",
    srcs = [
        "ActivityComponent.java",
        "ActivityRetainedComponent.java",
        "FragmentComponent.java",
        "ServiceComponent.java",
        "ViewComponent.java",
        "ViewModelComponent.java",
        "ViewWithFragmentComponent.java",
    ],
    exports = [
        "//hilt-core/main/java/dagger/hilt/components",
    ],
    deps = [
        ":package_info",
        "//hilt-android/main/java/dagger/hilt/android/scopes",
        "//hilt-core/main/java/dagger/hilt:define_component",
        "//hilt-core/main/java/dagger/hilt/components",
    ],
)

java_library(
    name = "package_info",
    srcs = ["package-info.java"],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
