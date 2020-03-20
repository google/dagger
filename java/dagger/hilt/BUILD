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
#   A library that wraps the Dagger API to make DI usage and testing easier.

package(default_visibility = ["//:src"])

java_library(
    name = "generate_components",
    srcs = ["GenerateComponents.java"],
    exported_plugins = [
        "//java/dagger/hilt/processor/internal/root:plugin",
    ],
    exports = [
        "//:dagger_with_compiler",
        "//java/dagger/hilt/internal:component_manager",
        "//java/dagger/hilt/internal:generated_component",
    ],
)

java_library(
    name = "install_in",
    srcs = ["InstallIn.java"],
    exported_plugins = [
        "//java/dagger/hilt/processor/internal/aggregateddeps:plugin",
    ],
    exports = [
        "//java/dagger/hilt/processor/internal/aggregateddeps:annotation",
        "@google_bazel_common//third_party/java/jsr250_annotations",
    ],
    deps = [
        "//java/dagger/hilt:generates_root_input",
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
        # TODO(user): Consider using a separate processor to validate @EntryPoint.
        "//java/dagger/hilt/processor/internal/aggregateddeps:plugin",
    ],
    deps = [
        "//java/dagger/hilt:generates_root_input",
        "//java/dagger/hilt/internal:component_manager",
        "//java/dagger/hilt/internal:generated_component",
    ],
)

java_library(
    name = "generates_root_input",
    srcs = [
        "GeneratesRootInput.java",
    ],
    exported_plugins = [
        "//java/dagger/hilt/processor/internal/generatesrootinput:processor",
    ],
    exports = [
        "//java/dagger/hilt/internal/generatesrootinput",
        "@google_bazel_common//third_party/java/jsr250_annotations",
    ],
)

java_library(
    name = "define_component",
    srcs = [
        "DefineComponent.java",
    ],
    exported_plugins = [
        "//java/dagger/hilt/processor/internal/definecomponent:processor",
    ],
    exports = [
        "//java/dagger/hilt/internal/definecomponent",
        "@google_bazel_common//third_party/java/jsr250_annotations",
    ],
    deps = [
        "//java/dagger/hilt:generates_root_input",
        "//java/dagger/hilt/internal/definecomponent",
    ],
)
