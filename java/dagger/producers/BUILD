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

package(default_visibility = ["//:src"])

load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
    "SOURCE_7_TARGET_7",
)

filegroup(
    name = "producers-srcs",
    srcs = glob(["**/*.java"]),
)

java_library(
    name = "producers",
    srcs = [":producers-srcs"],
    javacopts = SOURCE_7_TARGET_7 + DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    plugins = [
        # TODO(cgruber): Enable Beta checker.
    ],
    exports = [
        # TODO(dpb): Don't export any of Guava.
        "//third_party:guava",
        "//third_party:jsr330_inject",
    ],
    deps = [
        "//java/dagger:core",
        "//third_party:guava",
        "//third_party:jsr305_annotations",
        "//third_party:jsr330_inject",
    ],
)

load("//tools:javadoc.bzl", "javadoc_library")

javadoc_library(
    name = "producers-javadoc",
    srcs = [":producers-srcs"],
    exclude_packages = [
        "dagger.producers.internal",
        "dagger.producers.monitoring.internal",
    ],
    root_packages = ["dagger.producers"],
    deps = [":producers"],
)
