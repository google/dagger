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

# BUILD rules for https://github.com/google/google-java-format

load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//:src"])

java_library(
    name = "google_java_format",
    exports = [
        "@maven//:com_google_googlejavaformat_google_java_format",
    ],
    runtime_deps = [
        "//third_party/java/error_prone:error_prone_javac",
        "//third_party/java/guava",
    ],
)

alias(
    name = "filer",
    actual = ":google_java_format",
)
