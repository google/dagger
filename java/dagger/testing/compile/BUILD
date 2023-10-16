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
#   Helpers class for java compiler tests.

load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//:src"])

java_library(
    name = "compile",
    testonly = 1,
    srcs = [
        "CompilerProcessors.java",
        "CompilerTests.java",
    ],
    exports = [
        "//java/dagger/internal/codegen/xprocessing:xprocessing-testing",
    ],
    deps = [
        "//java/dagger/internal/codegen:processor",
        "//java/dagger/internal/codegen/extension",
        "//java/dagger/internal/codegen/xprocessing",
        "//java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//java/dagger/spi",
        "//third_party/java/auto:value",
        "//third_party/java/compile_testing",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/io",
        "//third_party/java/junit",
        "@maven//:com_google_devtools_ksp_symbol_processing_api",
    ],
)
