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

load("@rules_java//java:defs.bzl", "java_library", "java_plugin")
load(
    "//:build_defs.bzl",
    "POM_VERSION",
)

package(default_visibility = ["//:src"])

java_library(
    name = "processor",
    srcs = glob(
        ["*.java"],
        exclude = ["package-info.java"],
    ),
    plugins = [
        "//dagger-compiler/main/java/dagger/internal/codegen/bootstrap",
    ],
    tags = ["maven_coordinates=com.google.dagger:dagger-compiler:" + POM_VERSION],
    deps = [
        ":package_info",
        "//dagger-compiler/main/java/dagger/internal/codegen/base",
        "//dagger-compiler/main/java/dagger/internal/codegen/binding",
        "//dagger-compiler/main/java/dagger/internal/codegen/bindinggraphvalidation",
        "//dagger-compiler/main/java/dagger/internal/codegen/compileroption",
        "//dagger-compiler/main/java/dagger/internal/codegen/componentgenerator",
        "//dagger-compiler/main/java/dagger/internal/codegen/kotlin",
        "//dagger-compiler/main/java/dagger/internal/codegen/processingstep",
        "//dagger-compiler/main/java/dagger/internal/codegen/validation",
        "//dagger-compiler/main/java/dagger/internal/codegen/writing",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing",
        "//dagger-runtime/main/java/dagger:core",
        "//dagger-spi",
        "//third_party/java/auto:service",
        "//third_party/java/error_prone:annotations",
        "//third_party/java/google_java_format",
        "//third_party/java/guava/collect",
        "//third_party/java/incap",
        "@maven//:com_google_devtools_ksp_symbol_processing_api",
    ],
)

java_library(
    name = "package_info",
    srcs = ["package-info.java"],
    tags = ["maven:merged"],
    deps = ["//third_party/java/error_prone:annotations"],
)

java_plugin(
    name = "component-codegen",
    generates_api = 1,
    output_licenses = ["unencumbered"],
    processor_class = "dagger.internal.codegen.ComponentProcessor",
    tags = [
        "annotation=dagger.Component;" +
        "genclass=${package}.Dagger${outerclasses}${classname}",
        "annotation=dagger.producers.ProductionComponent;" +
        "genclass=${package}.Dagger${outerclasses}${classname}",
        "annotation=dagger.MapKey;" +
        "genclass=${package}.${outerclasses}${classname}Creator",
    ],
    deps = [":processor"],
)
