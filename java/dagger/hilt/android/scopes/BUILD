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

package(default_visibility = ["//:src"])

# Description:
# Hilt scopes.

android_library(
    name = "scopes",
    srcs = [
        "ActivityScoped.java",
        "FragmentScoped.java",
        "ServiceScoped.java",
        "ViewScoped.java",
    ],
    deps = [
        ":package_info",
        "@google_bazel_common//third_party/java/jsr330_inject",
    ],
)

android_library(
    name = "activity_retained_scoped",
    srcs = ["ActivityRetainedScoped.java"],
    deps = [
        ":package_info",
        "@google_bazel_common//third_party/java/jsr330_inject",
    ],
)

android_library(
    name = "view_model_scoped",
    srcs = ["ViewModelScoped.java"],
    deps = [
        ":package_info",
        "@google_bazel_common//third_party/java/jsr330_inject",
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
