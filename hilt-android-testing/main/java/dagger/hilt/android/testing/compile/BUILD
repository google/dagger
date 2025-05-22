# Copyright (C) 2020 The Dagger Authors.
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
#   Tests for internal code for implementing Hilt processors.

load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//:src"])

java_library(
    name = "compile",
    testonly = 1,
    srcs = [
        "HiltCompilerProcessors.java",
        "HiltCompilerTests.java",
    ],
    deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen:processor",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//dagger-spi",
        "//dagger-testing/main/java/dagger/testing/compile",
        "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/androidentrypoint:processor_lib",
        "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/customtestapplication:processor_lib",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:base_processor",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal:hilt_processing_env_configs",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/aggregateddeps:processor_lib",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/aliasof:processor_lib",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/definecomponent:processor_lib",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/earlyentrypoint:processor_lib",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/generatesrootinput:processor_lib",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/originatingelement:processor_lib",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/root:component_tree_deps_processor_lib",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/root:root_processor_lib",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/uninstallmodules:processor_lib",
        "//third_party/java/auto:value",
        "//third_party/java/compile_testing",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/junit",
        "//third_party/kotlin/kotlin_symbol_processing:symbol-processing-api",
    ],
)
