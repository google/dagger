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
#
# Description:
#   Testing libraries for Hilt.

load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//:src"])

java_library(
    name = "package_info",
    srcs = ["package-info.java"],
    deps = [
        "//third_party/java/jsr305_annotations",
    ],
)

java_library(
    name = "test_install_in",
    testonly = 1,
    srcs = ["TestInstallIn.java"],
    exported_plugins = [
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/aggregateddeps:plugin",
    ],
    exports = [
        "//hilt-core/main/java/dagger/hilt/processor/internal/aggregateddeps:annotation",
    ],
    deps = [
        ":package_info",
        "//hilt-core/main/java/dagger/hilt:generates_root_input",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
