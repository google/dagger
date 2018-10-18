# Copyright (C) 2018 The Dagger Authors.
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
#   An example of the dagger.spi.BindingGraphPlugin usage

package(default_visibility = ["//:src"])

java_plugin(
    name = "binding-graph-visualizer",
    srcs = glob(["*.java"]),
    deps = [
        "//java/dagger/model",
        "//java/dagger/spi",
        "@google_bazel_common//third_party/java/auto:service",
        "@google_bazel_common//third_party/java/error_prone:annotations",
        "@google_bazel_common//third_party/java/guava",
        "@google_bazel_common//third_party/java/javapoet",
    ],
)
