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
#   Dagger-specific extensions to the javax.lang.model APIs

package(default_visibility = ["//:src"])

java_library(
    name = "langmodel",
    srcs = glob(["*.java"]),
    plugins = ["//java/dagger/internal/codegen:bootstrap_compiler_plugin"],
    tags = ["maven:merged"],
    deps = [
        "//java/dagger:core",
        "@google_bazel_common//third_party/java/auto:common",
        "@google_bazel_common//third_party/java/guava",
        "@google_bazel_common//third_party/java/javapoet",
    ],
)
