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
    javacopts = SOURCE_7_TARGET_7 + DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    plugins = [
        # TODO(cgruber): Enable Beta checker.
    ],
    tags = ["maven_coordinates=com.google.dagger:dagger-producers:${project.version}"],
    exports = [
        # TODO(dpb): Don't export any of Guava.
        "//third_party:guava",
        "//third_party:jsr330_inject",
    ],
    deps = [
        "//java/dagger:core",
        "//third_party:checker_framework_annotations",
        "//third_party:error_prone_annotations",
        "//third_party:guava",
        "//third_party:jsr330_inject",
    ],
)

load("//tools:mvn.bzl", "dagger_pom_file")

dagger_pom_file(
    name = "pom.xml",
    artifact_id = "dagger-producers",
    artifact_name = "Dagger Producers",
    targets = [":producers"],
)

load("//tools:javadoc.bzl", "javadoc_library")

javadoc_library(
    name = "producers-javadoc",
    srcs = SRCS,
    exclude_packages = [
        "dagger.producers.internal",
        "dagger.producers.monitoring.internal",
    ],
    root_packages = ["dagger.producers"],
    deps = [":producers"],
)
