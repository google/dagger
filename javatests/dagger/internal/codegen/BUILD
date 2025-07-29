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
load("//:test_defs.bzl", "GenCompilerTests")

package(default_visibility = ["//:src"])

# TODO(bcorso): Move this into a subpackage.
KT_LIB_NO_DAGGER_COMPILER_SRCS = [
    "KotlinInjectedQualifier.kt",
    "KotlinObjectWithMemberInjection.kt",
]

# These sources are purposely compiled without the Dagger compiler for certain tests.
kt_jvm_library(
    name = "kt_lib_no_dagger_compiler",
    srcs = KT_LIB_NO_DAGGER_COMPILER_SRCS,
    deps = [
        "//dagger-runtime/main/java/dagger:core",
    ],
)

COMPILERS_SRCS = [
    "CompilerMode.java",
    "Compilers.java",
    "ComponentCreatorTestHelper.java",
    "DaggerModuleMethodSubject.java",
    "GeneratingProcessingStep.java",
    "JavaFileBuilder.java",
    "TestUtils.java",
    "GeneratedLines.java",
    "GeneratingProcessor.java",
]

# TODO(bcorso): Move this into a dagger-testing/ package?
java_library(
    name = "compilers",
    srcs = COMPILERS_SRCS,
    deps = [
        "//dagger-compiler:xprocessing",
        "//dagger-compiler/main/java/dagger/internal/codegen:package_info",
        "//dagger-compiler/main/java/dagger/internal/codegen:processor",
        "//dagger-compiler/main/java/dagger/internal/codegen/base",
        "//dagger-compiler/main/java/dagger/internal/codegen/binding",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xpoet",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//dagger-producers/main/java/dagger/producers",
        "//dagger-runtime/main/java/dagger:core",
        "//java/dagger/testing/compile",
        "//third_party/java/auto:value_processor",
        "//third_party/java/compile_testing",
        "//third_party/java/error_prone:annotations",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/truth",
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
    "//third_party/java/auto:value_processor",
    "//third_party/java/auto:common",
    "//third_party/java/compile_testing",
    "//third_party/java/javapoet",
    "//third_party/java/jsr250_annotations",  # Include @Generated in generated files.
    "//third_party/java/jsr330_inject",
    "//third_party/java/junit",
    "//third_party/java/mockito",
    "//third_party/java/truth",
    "//dagger-runtime/main/java/dagger:core",
    "//dagger-compiler/main/java/dagger/internal/codegen:package_info",
    "//dagger-compiler/main/java/dagger/internal/codegen:processor",
    "//dagger-compiler/main/java/dagger/internal/codegen/base",
    "//dagger-compiler/main/java/dagger/internal/codegen/binding",
    "//dagger-compiler/main/java/dagger/internal/codegen/bindinggraphvalidation",
    "//dagger-compiler/main/java/dagger/internal/codegen/compileroption",
    "//dagger-compiler/main/java/dagger/internal/codegen/javac",
    "//dagger-compiler/main/java/dagger/internal/codegen/kotlin",
    "//dagger-compiler/main/java/dagger/internal/codegen/model",
    "//dagger-compiler/main/java/dagger/internal/codegen/validation",
    "//dagger-compiler/main/java/dagger/internal/codegen/writing",
    "//dagger-compiler:xprocessing",
    "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xpoet",
    "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
    "//dagger-producers/main/java/dagger/producers",
    "//dagger-spi",
    "//dagger-testing/main/java/dagger/model/testing",
    "//dagger-testing/main/java/dagger/spi/model/testing",
    "//java/dagger/testing/compile",
    "//java/dagger/testing/golden",
]

GenCompilerTests(
    name = "large_compiler_tests",
    srcs = LARGE_TESTS,
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    plugins = ["//dagger-compiler/main/java/dagger/internal/codegen/bootstrap"],
    shard_count = 7,
    deps = DEPS,
)

GenCompilerTests(
    name = "medium_compiler_tests",
    srcs = MEDIUM_TESTS,
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    plugins = ["//dagger-compiler/main/java/dagger/internal/codegen/bootstrap"],
    shard_count = 3,
    deps = DEPS,
)

GenCompilerTests(
    name = "compiler_tests",
    srcs = glob(
        ["*.java"],
        exclude = KT_LIB_NO_DAGGER_COMPILER_SRCS + COMPILERS_SRCS + LARGE_TESTS + MEDIUM_TESTS,
    ),
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    plugins = ["//dagger-compiler/main/java/dagger/internal/codegen/bootstrap"],
    deps = DEPS,
)
