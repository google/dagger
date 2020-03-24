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
#   Internal code for implementing Hilt android processors.

package(default_visibility = ["//:src"])

java_library(
    name = "android_classnames",
    srcs = [
        "AndroidClassNames.java",
    ],
    deps = [
        "@google_bazel_common//third_party/java/javapoet",
    ],
)

# TODO(user): Merge this into other utils
java_library(
    name = "utils",
    srcs = [
        "MoreTypes.java",
    ],
    deps = [
        "//java/dagger/internal/guava:base",
        "//java/dagger/internal/guava:collect",
    ],
)
