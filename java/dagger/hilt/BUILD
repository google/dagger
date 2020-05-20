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
        ":package_info",
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
        ":package_info",
        "//java/dagger/hilt:generates_root_input",
        "//java/dagger/hilt/internal:component_manager",
        "//java/dagger/hilt/internal:generated_component",
        "@google_bazel_common//third_party/java/jsr305_annotations",
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
        "//java/dagger/hilt/processor/internal/definecomponent:processor",
    ],
    exports = [
        "//java/dagger/hilt/internal/definecomponent",
        "@google_bazel_common//third_party/java/jsr250_annotations",
    ],
    deps = [
        ":package_info",
        "//java/dagger/hilt:generates_root_input",
        "//java/dagger/hilt/internal/definecomponent",
    ],
)

java_library(
    name = "package_info",
    srcs = ["package-info.java"],
    deps = [
        "@google_bazel_common//third_party/java/jsr305_annotations",
    ],
)

filegroup(
    name = "javadoc-srcs",
    srcs = [
        ":hilt_filegroup",
        ":hilt_testing_filegroup",
    ],
)

filegroup(
    name = "hilt_filegroup",
    srcs = glob(["*"]) + [
        "//java/dagger/hilt/codegen:srcs_filegroup",
        "//java/dagger/hilt/migration:srcs_filegroup",
        "//java/dagger/hilt/internal:srcs_filegroup",
        "//java/dagger/hilt/internal/aliasof:srcs_filegroup",
        "//java/dagger/hilt/internal/definecomponent:srcs_filegroup",
        "//java/dagger/hilt/internal/generatesrootinput:srcs_filegroup",
        "//java/dagger/hilt/android:srcs_filegroup",
        "//java/dagger/hilt/android/components:srcs_filegroup",
        "//java/dagger/hilt/android/qualifiers:srcs_filegroup",
        "//java/dagger/hilt/android/internal/builders:srcs_filegroup",
        "//java/dagger/hilt/android/internal/lifecycle:srcs_filegroup",
        "//java/dagger/hilt/android/internal/managers:srcs_filegroup",
        "//java/dagger/hilt/android/internal/migration:srcs_filegroup",
        "//java/dagger/hilt/android/internal/modules:srcs_filegroup",
        "//java/dagger/hilt/android/migration:srcs_filegroup",
        "//java/dagger/hilt/android/scopes:srcs_filegroup",
        "//java/dagger/hilt/android/plugin:srcs_filegroup",
    ],
)

filegroup(
    name = "hilt_testing_filegroup",
    srcs = [
        "//java/dagger/hilt/android/internal/testing:srcs_filegroup",
        "//java/dagger/hilt/android/testing:srcs_filegroup",
    ],
)

filegroup(
    name = "hilt_processing_filegroup",
    srcs = [
        "//java/dagger/hilt/android/processor:srcs_filegroup",
        "//java/dagger/hilt/android/processor/internal:srcs_filegroup",
        "//java/dagger/hilt/android/processor/internal/androidentrypoint:srcs_filegroup",
        "//java/dagger/hilt/android/processor/internal/bindvalue:srcs_filegroup",
        "//java/dagger/hilt/android/processor/internal/customtestapplication:srcs_filegroup",
        "//java/dagger/hilt/android/processor/internal/testroot:srcs_filegroup",
        "//java/dagger/hilt/android/processor/internal/uninstallmodules:srcs_filegroup",
        "//java/dagger/hilt/processor/internal:srcs_filegroup",
        "//java/dagger/hilt/processor/internal/aggregateddeps:srcs_filegroup",
        "//java/dagger/hilt/processor/internal/aliasof:srcs_filegroup",
        "//java/dagger/hilt/processor/internal/definecomponent:srcs_filegroup",
        "//java/dagger/hilt/processor/internal/disableinstallincheck:srcs_filegroup",
        "//java/dagger/hilt/processor/internal/generatesrootinput:srcs_filegroup",
        "//java/dagger/hilt/processor/internal/originatingelement:srcs_filegroup",
        "//java/dagger/hilt/processor/internal/root:srcs_filegroup",
    ],
)
