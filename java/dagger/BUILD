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
#   A JSR-330 compliant dependency injection system for android and java

load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "JAVA_RELEASE_MIN",
    "POM_VERSION",
)
load("//tools/maven:maven.bzl", "gen_maven_artifact")

package(default_visibility = ["//:src"])

java_library(
    name = "core",
    srcs = glob(["**/*.java"]),
    javacopts = JAVA_RELEASE_MIN + DOCLINT_HTML_AND_SYNTAX,
    tags = ["maven_coordinates=com.google.dagger:dagger:" + POM_VERSION],
    exports = ["//third_party/java/jsr330_inject"],
    deps = [
        "//third_party/java/jsr330_inject",
        "@maven//:jakarta_inject_jakarta_inject_api",
    ],
)

gen_maven_artifact(
    name = "artifact",
    artifact_coordinates = "com.google.dagger:dagger:" + POM_VERSION,
    artifact_name = "Dagger",
    artifact_target = ":core",
    artifact_target_maven_deps = [
        "jakarta.inject:jakarta.inject-api",
        "javax.inject:javax.inject",
    ],
    javadoc_root_packages = ["dagger"],
    javadoc_srcs = [":javadoc-srcs"],
    proguard_specs = [":proguard.pro"],
    r8_specs = [":r8.pro"],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(["**/*"]),
)
