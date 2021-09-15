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

# Description:
#   A processor for @dagger.hilt.codegen.OriginatingElement.

package(default_visibility = ["//:src"])

java_plugin(
    name = "processor",
    generates_api = 1,
    processor_class = "dagger.hilt.processor.internal.originatingelement.OriginatingElementProcessor",
    deps = [":processor_lib"],
)

java_library(
    name = "processor_lib",
    srcs = [
        "OriginatingElementProcessor.java",
    ],
    deps = [
        "//java/dagger/hilt/processor/internal:base_processor",
        "//java/dagger/hilt/processor/internal:classnames",
        "//java/dagger/hilt/processor/internal:processor_errors",
        "//java/dagger/hilt/processor/internal:processors",
        "//java/dagger/internal/guava:collect",
        "@google_bazel_common//third_party/java/auto:common",
        "@google_bazel_common//third_party/java/auto:service",
        "@google_bazel_common//third_party/java/incap",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
