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

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "google_bazel_common",
    strip_prefix = "bazel-common-3ce0644a7d7da09218b96b3218f409ea8f8c84e6",
    urls = ["https://github.com/google/bazel-common/archive/3ce0644a7d7da09218b96b3218f409ea8f8c84e6.zip"],
)

load("@google_bazel_common//:workspace_defs.bzl", "google_common_workspace_rules")

google_common_workspace_rules()

maven_jar(
    name = "org_jetbrains_kotlinx_kotlinx_metadata_jvm",
    artifact = "org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.1.0",
)

maven_jar(
    name = "org_jetbrains_kotlin_kotlin_stdlib",
    artifact = "org.jetbrains.kotlin:kotlin-stdlib:1.3.41",
)

maven_jar(
    name = "org_jetbrains_annotations",
    artifact = "org.jetbrains:annotations:13.0",
)

# This fixes an issue with protobuf starting to use zlib by default in 3.7.0.
# TODO(ronshapiro): Figure out if this is in fact necessary, or if proto can depend on the
# @bazel_tools library directly. See discussion in
# https://github.com/protocolbuffers/protobuf/pull/5389#issuecomment-481785716
bind(
    name = "zlib",
    actual = "@bazel_tools//third_party/zlib",
)
