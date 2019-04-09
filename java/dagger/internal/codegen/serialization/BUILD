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
#    Serialized forms of types used in the Dagger processor.

package(default_visibility = ["//:src"])

proto_library(
    name = "serialization_proto",
    srcs = ["serialization.proto"],
    visibility = ["//visibility:private"],
)

java_proto_library(
    name = "serialization_java_proto",
    visibility = ["//visibility:private"],
    deps = [":serialization_proto"],
)

java_library(
    name = "serialization",
    srcs = glob(["*.java"]),
    exports = [":serialization_java_proto"],
    deps = [
        "@google_bazel_common//third_party/java/guava",
        "@google_bazel_common//third_party/java/javapoet",
        "@google_bazel_common//third_party/java/protobuf",
    ],
)
