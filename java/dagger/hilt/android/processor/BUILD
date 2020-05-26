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
#   Hilt android processors.

load("//tools:maven.bzl", "POM_VERSION_ALPHA", "gen_maven_artifact")

package(default_visibility = ["//:src"])

java_library(
    name = "artifact-lib",
    tags = ["maven_coordinates=com.google.dagger:hilt-android-compiler:" + POM_VERSION_ALPHA],
    visibility = ["//visibility:private"],
    exports = [
        "//java/dagger/hilt/android/processor/internal/androidentrypoint:processor_lib",
        "//java/dagger/hilt/android/processor/internal/bindvalue:bind_value_processor_lib",
        "//java/dagger/hilt/android/processor/internal/customtestapplication:processor_lib",
        "//java/dagger/hilt/android/processor/internal/testroot:test_root_processor",
        "//java/dagger/hilt/android/processor/internal/uninstallmodules:processor_lib",
        "//java/dagger/hilt/processor/internal/aggregateddeps:processor_lib",
        "//java/dagger/hilt/processor/internal/aliasof:processor_lib",
        "//java/dagger/hilt/processor/internal/definecomponent:processor_lib",
        "//java/dagger/hilt/processor/internal/generatesrootinput:processor_lib",
        "//java/dagger/hilt/processor/internal/originatingelement:processor_lib",
        "//java/dagger/hilt/processor/internal/root:processor_lib",
        "//java/dagger/internal/codegen:processor",
    ],
)

gen_maven_artifact(
    name = "artifact",
    artifact_id = "hilt-android-compiler",
    artifact_name = "Hilt Android Processor",
    artifact_target = ":artifact-lib",
    javadoc_android_api_level = 29,
    javadoc_root_packages = [
        "dagger.hilt.processor",
        "dagger.hilt.android.processor",
    ],
    javadoc_srcs = [
        "//java/dagger/hilt:hilt_processing_filegroup",
    ],
    shaded_deps = ["@com_google_auto_auto_common//jar"],
    shaded_rules = ["rule com.google.auto.common.** dagger.shaded.auto.common.@1"],
    deps = [
        "//java/dagger/hilt/android/processor/internal:android_classnames",
        "//java/dagger/hilt/android/processor/internal:utils",
        "//java/dagger/hilt/android/processor/internal/androidentrypoint:android_generators",
        "//java/dagger/hilt/android/processor/internal/androidentrypoint:compiler_options",
        "//java/dagger/hilt/android/processor/internal/androidentrypoint:metadata",
        "//java/dagger/hilt/android/processor/internal/androidentrypoint:processor_lib",
        "//java/dagger/hilt/android/processor/internal/bindvalue:bind_value_processor_lib",
        "//java/dagger/hilt/android/processor/internal/customtestapplication:processor_lib",
        "//java/dagger/hilt/android/processor/internal/testroot:test_root_processor_lib",
        "//java/dagger/hilt/android/processor/internal/uninstallmodules:processor_lib",
        "//java/dagger/hilt/codegen:originating_element",
        "//java/dagger/hilt/codegen:package_info",
        "//java/dagger/hilt/processor/internal:base_processor",
        "//java/dagger/hilt/processor/internal:classnames",
        "//java/dagger/hilt/processor/internal:component_descriptor",
        "//java/dagger/hilt/processor/internal:component_names",
        "//java/dagger/hilt/processor/internal:components",
        "//java/dagger/hilt/processor/internal:processor_errors",
        "//java/dagger/hilt/processor/internal:processors",
        "//java/dagger/hilt/processor/internal/aggregateddeps:annotation",
        "//java/dagger/hilt/processor/internal/aggregateddeps:component_dependencies",
        "//java/dagger/hilt/processor/internal/aggregateddeps:processor_lib",
        "//java/dagger/hilt/processor/internal/aliasof:alias_ofs",
        "//java/dagger/hilt/processor/internal/aliasof:processor_lib",
        "//java/dagger/hilt/processor/internal/definecomponent:define_components",
        "//java/dagger/hilt/processor/internal/definecomponent:processor_lib",
        "//java/dagger/hilt/processor/internal/generatesrootinput:generates_root_inputs",
        "//java/dagger/hilt/processor/internal/generatesrootinput:processor_lib",
        "//java/dagger/hilt/processor/internal/originatingelement:processor_lib",
        "//java/dagger/hilt/processor/internal/root:processor_lib",
        "//java/dagger/hilt/processor/internal/root:root_metadata",
        "//java/dagger/hilt/processor/internal/root:root_type",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
