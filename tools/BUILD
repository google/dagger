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

# Description:
#   Tools for Dagger

load("@bazel_skylib//:bzl_library.bzl", "bzl_library")
load(":maven_info.bzl", "maven_info_tests")

package(default_visibility = ["//:src"])

maven_info_tests()

exports_files([
    "pom-template.xml",
])

bzl_library(
    name = "maven_bzl",
    srcs = ["maven.bzl"],
    deps = [":maven_info_bzl"],
)

bzl_library(
    name = "maven_info_bzl",
    srcs = ["maven_info.bzl"],
    deps = ["@bazel_skylib//lib:unittest"],
)

bzl_library(
    name = "dejetify_bzl",
    srcs = ["dejetify.bzl"],
)
