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

# Description:
#   Dagger Android Lint Rules

load("@rules_android//rules:rules.bzl", "android_library")
load("//:build_defs.bzl", "POM_VERSION")
load("//tools/maven:maven.bzl", "gen_maven_artifact")

package(default_visibility = ["//:src"])

# An empty android artifact to distribute and share the Dagger lint rules for
# the Android sub-projects.
android_library(
    name = "lint-android-artifact-lib",
    tags = ["maven_coordinates=com.google.dagger:dagger-lint-aar:" + POM_VERSION],
)

gen_maven_artifact(
    name = "lint-android-artifact",
    artifact_coordinates = "com.google.dagger:dagger-lint-aar:" + POM_VERSION,
    artifact_name = "Dagger Lint Rules AAR Distribution",
    artifact_target = ":lint-android-artifact-lib",
    lint_deps = ["//dagger-lint/main/java/dagger/lint:lint-artifact-lib"],
    manifest = "main/AndroidManifest.xml",
    packaging = "aar",
    pom_name = "lint-android-pom",
)
