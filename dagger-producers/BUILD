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
#   An asynchronous dependency injection system that extends JSR-330.

load("//:build_defs.bzl", "POM_VERSION")
load("//tools/maven:maven.bzl", "gen_maven_artifact")

package(default_visibility = ["//:src"])

gen_maven_artifact(
    name = "artifact",
    artifact_coordinates = "com.google.dagger:dagger-producers:" + POM_VERSION,
    artifact_name = "Dagger Producers",
    artifact_target = "//dagger-producers/main/java/dagger/producers",
    artifact_target_maven_deps = [
        "com.google.dagger:dagger",
        "com.google.guava:failureaccess",
        "com.google.guava:guava",  # TODO(bcorso): Remove guava dependency and ban it?
        "javax.inject:javax.inject",
        "org.checkerframework:checker-compat-qual",
    ],
    javadoc_exclude_packages = [
        "dagger.producers.internal",
        "dagger.producers.monitoring.internal",
    ],
    javadoc_root_packages = ["dagger.producers"],
    javadoc_srcs = ["//dagger-producers/main/java/dagger/producers:producers-srcs"],
    # TODO(bcorso): Look more into why auto/common shading isn't needed here.
)
