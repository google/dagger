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

load("@rules_android//rules:rules.bzl", "android_library")

package(default_visibility = ["//:src"])

android_library(
    name = "functional",
    srcs = glob(
        ["*.java"],
        exclude = ["*Test.java"],
    ),
    exports_manifest = 1,
    manifest = "AndroidManifest.xml",
    resource_files = glob(["res/**"]),
    deps = [
        "//third_party/java/androidx/fragment",
        "//third_party/java/androidx/appcompat",
        "//third_party/java/androidx/annotation",
        "//third_party/java/dagger",
        "//third_party/java/dagger:android",
        "//third_party/java/dagger:android-support",
        # TODO(ronshapiro): figure out why strict deps is failing without this
        "//third_party/java/jsr250_annotations",
    ],
)
