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

# BUILD rules for https://github.com/google/error-prone. Note that Bazel already
# applies the Error Prone compiler to all java compilations - this package exports
# dependencies for Error Prone's libraries

load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//:src"])

java_library(
    name = "annotations",
    tags = ["maven:compile_only"],
    exports = ["@maven//:com_google_errorprone_error_prone_annotations"],
)

java_library(
    name = "check_api",
    exports = [
        "@maven//:com_google_errorprone_error_prone_annotation",
        "@maven//:com_google_errorprone_error_prone_check_api",
    ],
    runtime_deps = [
        ":annotations",
        "//third_party/java/checker_framework:dataflow",
        "//third_party/java/diffutils",
        "//third_party/java/jsr305_annotations",
    ],
)
