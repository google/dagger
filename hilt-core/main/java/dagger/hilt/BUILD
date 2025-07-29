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

load("@rules_java//java:defs.bzl", "java_library")

# Description:
#   A library that wraps the Dagger API to make DI usage and testing easier.

package(default_visibility = ["//:src"])

java_library(
    name = "install_in",
    srcs = ["InstallIn.java"],
    exported_plugins = [
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/aggregateddeps:plugin",
    ],
    exports = [
        "//hilt-core/main/java/dagger/hilt/processor/internal/aggregateddeps:annotation",
    ],
    deps = [
        ":generates_root_input",
        ":package_info",
    ],
)

java_library(
    name = "entry_point",
    srcs = [
        "EntryPoint.java",
        "EntryPoints.java",
    ],
    exported_plugins = [
        # This is required so that we can fail if @InstallIn is missing.
        # TODO(bcorso): Consider using a separate processor to validate @EntryPoint.
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/aggregateddeps:plugin",
    ],
    proguard_specs = ["proguard-rules.pro"],
    deps = [
        ":generates_root_input",
        ":package_info",
        "//hilt-core/main/java/dagger/hilt/internal:component_manager",
        "//hilt-core/main/java/dagger/hilt/internal:generated_component",
        "//hilt-core/main/java/dagger/hilt/internal:preconditions",
        "//hilt-core/main/java/dagger/hilt/internal:test_singleton_component",
        "//third_party/java/jsr305_annotations",
    ],
)

java_library(
    name = "generates_root_input",
    srcs = [
        "GeneratesRootInput.java",
    ],
    exported_plugins = [
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/generatesrootinput:processor",
    ],
    exports = [
        "//hilt-core/main/java/dagger/hilt/internal/generatesrootinput",
    ],
    deps = [
        ":package_info",
    ],
)

java_library(
    name = "define_component",
    srcs = [
        "DefineComponent.java",
    ],
    exported_plugins = [
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/definecomponent:processor",
    ],
    exports = [
        "//hilt-core/main/java/dagger/hilt/internal/definecomponent",
    ],
    deps = [
        ":generates_root_input",
        ":package_info",
        "//hilt-core/main/java/dagger/hilt/internal/definecomponent",
    ],
)

java_library(
    name = "package_info",
    srcs = ["package-info.java"],
    deps = [
        "//third_party/java/jsr305_annotations",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
