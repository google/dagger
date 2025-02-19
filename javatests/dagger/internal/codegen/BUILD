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

load("@io_bazel_rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")
load("@rules_java//java:defs.bzl", "java_library")
load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX")
load("//:test_defs.bzl", "GenJavaTests")

package(default_visibility = ["//:src"])

# These sources are purposely compiled without the Dagger compiler for certain tests.
kt_jvm_library(
    name = "kt_lib_no_dagger_compiler",
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
        "ComponentCreatorTestHelper.java",
        "DaggerModuleMethodSubject.java",
        "GeneratingProcessingStep.java",
        "JavaFileBuilder.java",
        "TestUtils.java",
    ],
    deps = [
        "//java/dagger:core",
        "//java/dagger/internal/codegen:package_info",
        "//java/dagger/internal/codegen:processor",
        "//java/dagger/internal/codegen/base",
        "//java/dagger/internal/codegen/binding",
        "//java/dagger/internal/codegen/xprocessing",
        "//java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//java/dagger/producers",
        "//java/dagger/testing/compile",
        "//third_party/java/compile_testing",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/javapoet",
        "//third_party/java/truth",
        "@maven//:com_google_auto_value_auto_value",
    ],
)

# These are tests with over 25 test cases.
LARGE_TESTS = [
    "ComponentCreatorTest.java",
    "ComponentProcessorTest.java",
    "InjectConstructorFactoryGeneratorTest.java",
    "MembersInjectionTest.java",
    "MissingBindingValidationTest.java",
    "ModuleFactoryGeneratorTest.java",
    "ProducerModuleFactoryGeneratorTest.java",
    "SubcomponentCreatorValidationTest.java",
]

# These are tests with over 10 test cases
MEDIUM_TESTS = [
    "BindsMethodValidationTest.java",
    "DaggerSuperficialValidationTest.java",
    "DelegateRequestRepresentationTest.java",
    "DuplicateBindingsValidationTest.java",
    "IgnoreProvisionKeyWildcardsTest.java",
    "MapMultibindingValidationTest.java",
    "MultibindsValidationTest.java",
    "ProductionComponentProcessorTest.java",
    "ProductionGraphValidationTest.java",
    "SubcomponentValidationTest.java",
]

DEPS = [
    ":compilers",
    ":kt_lib_no_dagger_compiler",
    "//third_party/java/guava/base",
    "//third_party/java/guava/collect",
    "//third_party/java/guava/util/concurrent",
    "//third_party/java/auto:value",
    "@maven//:com_google_auto_value_auto_value",
    "//third_party/java/auto:common",
    "//third_party/java/compile_testing",
    "//third_party/java/javapoet",
    "//third_party/java/jsr250_annotations",  # Include @Generated in generated files.
    "//third_party/java/jsr330_inject",
    "//third_party/java/junit",
    "//third_party/java/mockito",
    "//third_party/java/truth",
    "//java/dagger:core",
    "//java/dagger/internal/codegen:package_info",
    "//java/dagger/internal/codegen:processor",
    "//java/dagger/internal/codegen/base",
    "//java/dagger/internal/codegen/binding",
    "//java/dagger/internal/codegen/bindinggraphvalidation",
    "//java/dagger/internal/codegen/compileroption",
    "//java/dagger/internal/codegen/extension",
    "//java/dagger/internal/codegen/javac",
    "//java/dagger/internal/codegen/javapoet",
    "//java/dagger/internal/codegen/kotlin",
    "//java/dagger/internal/codegen/langmodel",
    "//java/dagger/internal/codegen/model",
    "//java/dagger/internal/codegen/validation",
    "//java/dagger/internal/codegen/writing",
    "//java/dagger/internal/codegen/xprocessing",
    "//java/dagger/internal/codegen/xprocessing:xprocessing-testing",
    "//java/dagger/model/testing",
    "//java/dagger/producers",
    "//java/dagger/spi",
    "//java/dagger/spi/model/testing",
    "//java/dagger/testing/compile",
    "//java/dagger/testing/golden",
]

GenJavaTests(
    name = "large_compiler_tests",
    srcs = LARGE_TESTS,
    functional = False,
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    plugins = ["//java/dagger/internal/codegen/bootstrap"],
    shard_count = 7,
    deps = DEPS,
)

GenJavaTests(
    name = "medium_compiler_tests",
    srcs = MEDIUM_TESTS,
    functional = False,
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    plugins = ["//java/dagger/internal/codegen/bootstrap"],
    shard_count = 3,
    deps = DEPS,
)

GenJavaTests(
    name = "compiler_tests",
    srcs = glob(
        ["*.java"],
        exclude = [
            "CompilerMode.java",
            "Compilers.java",
            "ComponentCreatorTestHelper.java",
            "ComponentProcessorTestClasses.java",
            "DaggerModuleMethodSubject.java",
            "GeneratingProcessingStep.java",
            "InvalidInjectConstructor.java",
            "JavaFileBuilder.java",
            "TestUtils.java",
        ] + LARGE_TESTS + MEDIUM_TESTS,
    ),
    functional = False,
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    plugins = ["//java/dagger/internal/codegen/bootstrap"],
    deps = DEPS,
)
