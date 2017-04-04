# Copyright (C) 2017 The Dagger Authors.
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
#   Functional test code for Dagger-Android

package(default_visibility = ["//:src"])

android_library(
    name = "functional",
    srcs = glob(
        ["*.java"],
        exclude = ["*Test.java"],
    ),
    manifest = "AndroidManifest.xml",
    resource_files = glob(["res/**"]),
    deps = [
        "//:android",
        "//:android-support",
        "//:dagger_with_compiler",
        "//third_party:guava",
        "@androidsdk//com.android.support:appcompat-v7-25.0.0",
        "@androidsdk//com.android.support:support-v4-25.0.0",
    ],
)

load("//:test_defs.bzl", "GenRobolectricTests")

GenRobolectricTests(
    name = "functional_tests",
    srcs = glob(["*Test.java"]),
    deps = [
        ":functional",
        "//:android",
        "//:android-support",
        "//:dagger_with_compiler",
        "//third_party:junit",
        "//third_party:truth",
        "@androidsdk//com.android.support:support-v4-25.0.0",
    ],
)
