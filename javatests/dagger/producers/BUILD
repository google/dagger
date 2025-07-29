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
#   Tests for dagger.producers

load("@rules_java//java:defs.bzl", "java_test")
load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
    "JAVA_RELEASE_MIN",
)

package(default_visibility = ["//:src"])

java_test(
    name = "ProducedTest",
    srcs = ["ProducedTest.java"],
    javacopts = JAVA_RELEASE_MIN + DOCLINT_REFERENCES + DOCLINT_HTML_AND_SYNTAX,
    deps = [
        "//dagger-producers/main/java/dagger/producers",
        "//dagger-runtime/main/java/dagger:core",
        "//third_party/java/guava:testlib",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/util/concurrent",
        "//third_party/java/junit",
        "//third_party/java/mockito",
        "//third_party/java/truth",
    ],
)
