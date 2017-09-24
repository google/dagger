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

CODEGEN_SRCS = glob(
    ["*.java"],
)

CODEGEN_PLUGINS = [
    # TODO(cgruber): Enable Beta checker.
]

CODEGEN_SHARED_DEPS = [
    "//third_party:auto_service",
    "//third_party:auto_value",
    "//third_party:auto_common",
    "//third_party:error_prone_annotations",
    "//third_party:google_java_format",
    "//third_party:javapoet",
    "@local_jdk//:lib/tools.jar",
    "//third_party:jsr305_annotations",
    "//third_party:jsr330_inject",
    "//java/dagger:core",
    "//java/dagger/producers",
]

java_library(
    name = "codegen",
    srcs = CODEGEN_SRCS,
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    plugins = CODEGEN_PLUGINS,
    deps = CODEGEN_SHARED_DEPS + [
        "//third_party:guava",
    ],
)

load("//tools:javadoc.bzl", "javadoc_library")

javadoc_library(
    name = "codegen-javadoc",
    srcs = CODEGEN_SRCS,
    root_packages = ["dagger.internal.codegen"],
    deps = [":codegen"],
)

java_plugin(
    name = "component-codegen",
    generates_api = 1,
    processor_class = "dagger.internal.codegen.ComponentProcessor",
    deps = [":codegen"],
)
