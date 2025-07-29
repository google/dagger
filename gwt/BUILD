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
#   GWT-specific files for Dagger

load("@rules_java//java:defs.bzl", "java_library")
load("//:build_defs.bzl", "JAVA_RELEASE_MIN", "POM_VERSION")
load("//tools/maven:maven.bzl", "dagger_pom_file")

package(default_visibility = ["//:src"])

java_library(
    name = "gwt",
    javacopts = JAVA_RELEASE_MIN,
    resource_strip_prefix = "gwt/",
    resources = glob(["**/*.gwt.xml"]),
    tags = ["maven_coordinates=com.google.dagger:dagger-gwt:" + POM_VERSION],
    exports = [
        ":manual_deps",
        "//dagger-runtime/main/java/dagger:core",
    ],
)

java_library(
    name = "manual_deps",
    tags = [
        "maven_coordinates=com.google.dagger:dagger:%s:jar:sources" % POM_VERSION,
        "maven_coordinates=jakarta.inject:jakarta.inject-api:2.0.1:jar:sources",
        "maven_coordinates=javax.inject:javax.inject:1:jar:sources",
        "maven_coordinates=org.jspecify:jspecify:1.0.0:jar:sources",
    ],
    visibility = ["//visibility:private"],
)

dagger_pom_file(
    name = "pom",
    artifact_id = "dagger-gwt",
    artifact_name = "Dagger GWT",
    targets = [
        ":gwt",
        ":manual_deps",
    ],
)
