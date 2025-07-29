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
load("//:build_defs.bzl", "POM_VERSION")
load("//tools/maven:maven.bzl", "gen_maven_artifact")

# Description:
#   A library that wraps the Dagger API to make DI usage and testing easier.

package(default_visibility = ["//:src"])

filegroup(
    name = "javadoc-srcs",
    srcs = [
        "//hilt-core/main/java/dagger/hilt:srcs_filegroup",
        "//hilt-core/main/java/dagger/hilt/codegen:srcs_filegroup",
        "//hilt-core/main/java/dagger/hilt/components:srcs_filegroup",
        "//hilt-core/main/java/dagger/hilt/internal:srcs_filegroup",
        "//hilt-core/main/java/dagger/hilt/internal/aliasof:srcs_filegroup",
        "//hilt-core/main/java/dagger/hilt/internal/definecomponent:srcs_filegroup",
        "//hilt-core/main/java/dagger/hilt/internal/generatesrootinput:srcs_filegroup",
        "//hilt-core/main/java/dagger/hilt/migration:srcs_filegroup",
        "//hilt-core/main/java/dagger/hilt/processor/internal/aggregateddeps:srcs_filegroup",
    ],
)

java_library(
    name = "artifact-lib",
    tags = ["maven_coordinates=com.google.dagger:hilt-core:" + POM_VERSION],
    exports = [
        "//dagger-runtime/main/java/dagger:core",
        "//hilt-core/main/java/dagger/hilt:define_component",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//hilt-core/main/java/dagger/hilt:generates_root_input",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//hilt-core/main/java/dagger/hilt:package_info",
        "//hilt-core/main/java/dagger/hilt/components",
        "//hilt-core/main/java/dagger/hilt/migration:alias_of",
        "//hilt-core/main/java/dagger/hilt/migration:disable_install_in_check",
    ],
)

gen_maven_artifact(
    name = "artifact",
    artifact_coordinates = "com.google.dagger:hilt-core:" + POM_VERSION,
    artifact_name = "Hilt Core",
    artifact_target = ":artifact-lib",
    artifact_target_libs = [
        "//hilt-core/main/java/dagger/hilt:define_component",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//hilt-core/main/java/dagger/hilt:generates_root_input",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//hilt-core/main/java/dagger/hilt:package_info",
        "//hilt-core/main/java/dagger/hilt/codegen:originating_element",
        "//hilt-core/main/java/dagger/hilt/codegen:package_info",
        "//hilt-core/main/java/dagger/hilt/components",
        "//hilt-core/main/java/dagger/hilt/components:package_info",
        "//hilt-core/main/java/dagger/hilt/internal:component_manager",
        "//hilt-core/main/java/dagger/hilt/internal:generated_component",
        "//hilt-core/main/java/dagger/hilt/internal:preconditions",
        "//hilt-core/main/java/dagger/hilt/internal:test_singleton_component",
        "//hilt-core/main/java/dagger/hilt/internal:unsafe_casts",
        "//hilt-core/main/java/dagger/hilt/internal/aliasof",
        "//hilt-core/main/java/dagger/hilt/internal/definecomponent",
        "//hilt-core/main/java/dagger/hilt/internal/generatesrootinput",
        "//hilt-core/main/java/dagger/hilt/migration:alias_of",
        "//hilt-core/main/java/dagger/hilt/migration:disable_install_in_check",
        "//hilt-core/main/java/dagger/hilt/migration:package_info",
        "//hilt-core/main/java/dagger/hilt/processor/internal/aggregateddeps:annotation",
    ],
    artifact_target_maven_deps = [
        "com.google.code.findbugs:jsr305",
        "com.google.dagger:dagger",
        "javax.inject:javax.inject",
    ],
    artifact_target_maven_deps_banned = [
        "com.google.guava:guava",
        "javax.annotation:javax.annotation-api",
    ],
    javadoc_exclude_packages = [
        "dagger.hilt.internal",
    ],
    javadoc_root_packages = [
        "dagger.hilt",
    ],
    javadoc_srcs = [
        ":javadoc-srcs",
    ],
)
