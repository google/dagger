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
#   Dagger Lint Rules

load("//:build_defs.bzl", "POM_VERSION")
load("//tools/maven:maven.bzl", "gen_maven_artifact")

package(default_visibility = ["//:src"])

gen_maven_artifact(
    name = "lint-artifact",
    artifact_coordinates = "com.google.dagger:dagger-lint:" + POM_VERSION,
    artifact_name = "Dagger Lint Rules",
    artifact_target = "//dagger-lint/main/java/dagger/lint:lint-artifact-lib",
    artifact_target_maven_deps = [
        "com.android.tools.external.com-intellij:intellij-core",
        "com.android.tools.external.com-intellij:kotlin-compiler",
        "com.android.tools.external.org-jetbrains:uast",
        "com.android.tools.lint:lint",
        "com.android.tools.lint:lint-api",
    ],
    pom_name = "lint-pom",
)
