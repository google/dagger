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

COMPILER_SRCS = glob(
    ["src/main/java/**/*.java"],
)

COMPILER_PLUGINS = [
    # TODO(cgruber): Enable Beta checker.
]

COMPILER_SHARED_DEPS = [
    "//third_party:auto_service",
    "//third_party:auto_value",
    "//third_party:auto_common",
    "//third_party:error_prone_annotations",
    "//third_party:google_java_format",
    "//third_party:javapoet",
    "@local_jdk//:lib/tools.jar",
    "//third_party:jsr305_annotations",
    "//third_party:jsr330_inject",
    "//core",
    "//producers",
]

java_library(
    name = "compiler",
    srcs = COMPILER_SRCS,
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    plugins = COMPILER_PLUGINS,
    deps = COMPILER_SHARED_DEPS + [
        "//third_party:guava",
    ],
)

java_plugin(
    name = "component-codegen",
    generates_api = 1,
    processor_class = "dagger.internal.codegen.ComponentProcessor",
    deps = [":compiler"],
)

load("//:test_defs.bzl", "GenJavaTests")

GenJavaTests(
    name = "compiler_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    plugins = [":component-codegen"],
    deps = [
        ":compiler",
        "//core",
        "//producers",
        "//third_party:auto_common",
        "//third_party:auto_value",
        "//third_party:compile_testing",
        "//third_party:guava",
        "//third_party:javapoet",
        "//third_party:jsr330_inject",
        "//third_party:junit",
        "//third_party:mockito",
        "//third_party:truth",
    ],
)

test_suite(name = "AllTests")

test_suite(
    name = "UnitAndFunctionalTests",
    tests = [
        ":AllTests",
        "//compiler/src/it/functional-tests:AllTests",
        "//compiler/src/it/producers-functional-tests:AllTests",
        "//compiler/src/it/tck:AllTests",
    ],
)
