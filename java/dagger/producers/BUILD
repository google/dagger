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
load("//tools:maven.bzl", "POM_VERSION", "gen_maven_artifact")

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
    tags = ["maven_coordinates=com.google.dagger:dagger-producers:" + POM_VERSION],
    exports = [
        # TODO(dpb): Don't export any of Guava.
        "@google_bazel_common//third_party/java/guava",
        "@google_bazel_common//third_party/java/jsr330_inject",
    ],
    deps = [
        "//java/dagger:core",
        "//java/dagger/internal/guava:base",
        "//java/dagger/internal/guava:collect",
        "//java/dagger/internal/guava:concurrent",
        "@google_bazel_common//third_party/java/checker_framework_annotations",
        "@google_bazel_common//third_party/java/error_prone:annotations",
        "@google_bazel_common//third_party/java/jsr330_inject",
    ],
)

gen_maven_artifact(
    name = "artifact",
    artifact_id = "dagger-producers",
    artifact_name = "Dagger Producers",
    artifact_target = ":producers",
    javadoc_exclude_packages = [
        "dagger.producers.internal",
        "dagger.producers.monitoring.internal",
    ],
    javadoc_root_packages = ["dagger.producers"],
    javadoc_srcs = SRCS,
    # TODO(user): Look more into why auto/common shading isn't needed here.
)
