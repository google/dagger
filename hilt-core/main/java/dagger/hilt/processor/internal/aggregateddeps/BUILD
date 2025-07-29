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
#   A processor that aggregates metadata about Hilt @InstallIn annotations

load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//:src"])

# TODO(bcorso): Remove all AggregatedDeps usage from the processor class path.
java_library(
    name = "annotation",
    srcs = ["AggregatedDeps.java"],
    exports = [
        "//hilt-core/main/java/dagger/hilt/codegen:originating_element",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
