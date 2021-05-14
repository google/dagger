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
#
# Description:
#  Builds and run tests related to AggregatedDepsProcessor.

load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kt_android_library")

package(default_visibility = ["//:src"])

kt_android_library(
    name = "testmodules",
    testonly = True,
    srcs = glob(["*.kt"]),
    deps = [
        "//:dagger_with_compiler",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/components",
        "@google_bazel_common//third_party/java/jsr330_inject",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
