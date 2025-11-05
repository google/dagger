# Copyright (C) 2022 The Dagger Authors.
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
#   Tests for the Dagger kotlin compiler/codegen

load("//java/dagger/testing/compile:macros.bzl", "kt_compiler_test")

kt_compiler_test(
    name = "KspComponentProcessorTest",
    srcs = ["KspComponentProcessorTest.java"],
    compiler_deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen:package_info",
        "//dagger-compiler/main/java/dagger/internal/codegen:processor",
        "//dagger-compiler/main/java/dagger/internal/codegen/base",
        "//dagger-compiler/main/java/dagger/internal/codegen/binding",
        "//dagger-compiler/main/java/dagger/internal/codegen/bindinggraphvalidation",
        "//dagger-compiler/main/java/dagger/internal/codegen/compileroption",
        "//dagger-compiler/main/java/dagger/internal/codegen/validation",
        "//dagger-compiler/main/java/dagger/internal/codegen/writing",
        "//dagger-producers/main/java/dagger/producers",
        "//dagger-runtime/main/java/dagger:core",
        "//dagger-testing/main/java/dagger/model/testing",
        "//dagger-spi",
    ],
    deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen:package_info",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//third_party/java/guava/collect",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

kt_compiler_test(
    name = "ComponentValidationKtTest",
    srcs = ["ComponentValidationKtTest.java"],
    compiler_deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen:package_info",
        "//dagger-compiler/main/java/dagger/internal/codegen:processor",
        "//dagger-compiler/main/java/dagger/internal/codegen/base",
        "//dagger-compiler/main/java/dagger/internal/codegen/binding",
        "//dagger-compiler/main/java/dagger/internal/codegen/bindinggraphvalidation",
        "//dagger-compiler/main/java/dagger/internal/codegen/compileroption",
        "//dagger-compiler/main/java/dagger/internal/codegen/validation",
        "//dagger-compiler/main/java/dagger/internal/codegen/writing",
        "//dagger-producers/main/java/dagger/producers",
        "//dagger-runtime/main/java/dagger:core",
        "//dagger-testing/main/java/dagger/model/testing",
        "//dagger-spi",
    ],
    deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//third_party/java/guava/collect",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)
