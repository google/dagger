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
#   An asynchronous dependency injection system that extends JSR-330.

load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
    "JAVA_RELEASE_MIN",
    "POM_VERSION",
)

package(default_visibility = ["//:src"])

# Work around b/70476182 which prevents Kythe from connecting :producers to the .java files it
# contains.
SRCS = glob(["**/*.java"])

filegroup(
    name = "producers-srcs",
    srcs = SRCS,
)

java_library(
    name = "producers",
    srcs = SRCS,
    javacopts = JAVA_RELEASE_MIN + DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    tags = ["maven_coordinates=com.google.dagger:dagger-producers:" + POM_VERSION],
    exports = [
        "//third_party/java/guava/base",
        "//third_party/java/guava/util/concurrent",
        "//third_party/java/jsr330_inject",
    ],
    deps = [
        "//dagger-runtime/main/java/dagger:core",
        "//third_party/java/checker_framework_annotations",
        "//third_party/java/error_prone:annotations",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/util/concurrent",
        "//third_party/java/jsr330_inject",
    ],
)
