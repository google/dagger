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
#   Extra features for the JDK and Guava. This code is merged into both
#   the dagger-compiler and dagger-spi artifacts that are sent to Maven

load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//dagger-spi:internal"])

java_library(
    name = "extension",
    srcs = glob(["*.java"]),
    tags = ["maven:merged"],
    deps = [
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/graph",
        "//third_party/java/jsr305_annotations",
    ],
)
