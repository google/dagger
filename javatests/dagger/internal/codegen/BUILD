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
#   Tests for the Dagger compiler/codegen

load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kt_jvm_library")
load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX")
load("//:test_defs.bzl", "GenJavaTests")
load("//java/dagger/testing/compile:macros.bzl", "compiler_test")

package(default_visibility = ["//:src"])

kt_jvm_library(
    name = "kotlin_sources",
    srcs = [
        "KotlinInjectedQualifier.kt",
        "KotlinObjectWithMemberInjection.kt",
    ],
    deps = [
        "//java/dagger:core",
    ],
)

# TODO(bcorso): Move this into a subpackage.
java_library(
    name = "compilers",
    srcs = [
        "CompilerMode.java",
        "Compilers.java",
        "JavaFileBuilder.java",
    ],
    deps = [
        "//java/dagger/internal/codegen:package_info",
        "//java/dagger/internal/codegen:processor",
        "//third_party/java/compile_testing",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "@com_google_auto_value_auto_value//jar",
    ],
)

java_library(
    name = "InvalidInjectConstructor",
    srcs = ["InvalidInjectConstructor.java"],
    # Note: We purposely leave off the dagger processor here.
    deps = [
        "//third_party/java/jsr330_inject",
    ],
)

GenJavaTests(
    name = "compiler_tests",
    srcs = glob(
        ["*.java"],
        exclude = [
            "CompilerMode.java",
            "Compilers.java",
            "JavaFileBuilder.java",
            "ComponentValidationKtTest.java",
            "InvalidInjectConstructor.java",
        ],
    ),
    functional = False,
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    plugins = ["//java/dagger/internal/codegen/bootstrap"],
    deps = [
        ":InvalidInjectConstructor",
        ":compilers",
        ":kotlin_sources",
        "//java/dagger:core",
        "//java/dagger/internal/codegen:package_info",
        "//java/dagger/internal/codegen:processor",
        "//java/dagger/internal/codegen/base",
        "//java/dagger/internal/codegen/binding",
        "//java/dagger/internal/codegen/bindinggraphvalidation",
        "//java/dagger/internal/codegen/compileroption",
        "//java/dagger/internal/codegen/javac",
        "//java/dagger/internal/codegen/javapoet",
        "//java/dagger/internal/codegen/kotlin",
        "//java/dagger/internal/codegen/langmodel",
        "//java/dagger/internal/codegen/validation",
        "//java/dagger/internal/codegen/writing",
        "//java/dagger/internal/codegen/xprocessing",
        "//java/dagger/model/testing",
        "//java/dagger/producers",
        "//java/dagger/spi",
        "//third_party/java/auto:common",
        "//third_party/java/auto:value",
        "//third_party/java/compile_testing",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/util/concurrent",
        "//third_party/java/javapoet",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
        "//third_party/java/mockito",
        "//third_party/java/truth",
        "@com_google_auto_value_auto_value//jar",
    ],
)

compiler_test(
    name = "ComponentValidationKtTest",
    srcs = ["ComponentValidationKtTest.java"],
    compiler_deps = [
        "//java/dagger:core",
        "//java/dagger/internal/codegen:package_info",
        "//java/dagger/internal/codegen:processor",
        "//java/dagger/internal/codegen/base",
        "//java/dagger/internal/codegen/binding",
        "//java/dagger/internal/codegen/bindinggraphvalidation",
        "//java/dagger/internal/codegen/compileroption",
        "//java/dagger/internal/codegen/javapoet",
        "//java/dagger/internal/codegen/langmodel",
        "//java/dagger/internal/codegen/validation",
        "//java/dagger/internal/codegen/writing",
        "//java/dagger/model/testing",
        "//java/dagger/producers",
        "//java/dagger/spi",
    ],
    deps = [
        "//third_party/java/guava/collect",
        "//third_party/java/junit",
        "//third_party/java/truth",
        "@maven//:com_github_tschuchortdev_kotlin_compile_testing",
    ],
)
