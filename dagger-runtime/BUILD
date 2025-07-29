# Copyright (C) 2025 The Dagger Authors.
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

load(
    "//:build_defs.bzl",
    "POM_VERSION",
)
load("//tools/maven:maven.bzl", "gen_maven_artifact")

package(default_visibility = ["//:src"])

gen_maven_artifact(
    name = "artifact",
    artifact_coordinates = "com.google.dagger:dagger:" + POM_VERSION,
    artifact_name = "Dagger",
    artifact_target = "//dagger-runtime/main/java/dagger:core",
    artifact_target_maven_deps = [
        "org.jspecify:jspecify",
        "jakarta.inject:jakarta.inject-api",
        "javax.inject:javax.inject",
    ],
    javadoc_root_packages = ["dagger"],
    javadoc_srcs = ["//dagger-runtime/main/java/dagger:javadoc-srcs"],
    proguard_specs = ["//dagger-runtime/main/resources:proguard"],
    r8_specs = ["//dagger-runtime/main/resources:r8"],
)
