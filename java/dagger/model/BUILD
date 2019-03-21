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
#   Dagger's core APIs exposed for plugins

package(default_visibility = ["//:src"])

load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
)

INTERNAL_PROXIES = ["BindingGraphProxies.java"]

filegroup(
    name = "model-srcs",
    srcs = glob(
        ["*.java"],
        exclude = INTERNAL_PROXIES,
    ),
)

java_library(
    name = "model",
    srcs = [":model-srcs"],
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    deps = [
        "//java/dagger:core",
        "//java/dagger/internal/codegen:jdk-and-guava-extras",
        "//java/dagger/producers",
        "@google_bazel_common//third_party/java/auto:common",
        "@google_bazel_common//third_party/java/auto:value",
        "@google_bazel_common//third_party/java/error_prone:annotations",
        "@google_bazel_common//third_party/java/guava",
        "@google_bazel_common//third_party/java/javapoet",
        "@google_bazel_common//third_party/java/jsr330_inject",
    ],
)

java_library(
    name = "internal-proxies",
    srcs = INTERNAL_PROXIES,
    tags = ["maven:merged"],
    deps = [
        ":model",
        "@google_bazel_common//third_party/java/guava",
    ],
)
