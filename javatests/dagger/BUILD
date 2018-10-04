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
#   A JSR-330 compliant dependency injection system for android and java

package(default_visibility = ["//:src"])

load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX", "DOCLINT_REFERENCES")
load("//:test_defs.bzl", "GenJavaTests")

GenJavaTests(
    name = "core_tests",
    srcs = glob(["**/*.java"]),
    functional = 0,
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    deps = [
        "//java/dagger:core",
        "@google_bazel_common//third_party/java/guava",
        "@google_bazel_common//third_party/java/jsr330_inject",
        "@google_bazel_common//third_party/java/junit",
        "@google_bazel_common//third_party/java/truth",
    ],
)
