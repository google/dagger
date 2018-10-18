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

package(default_visibility = ["//:src"])

load("//tools:maven.bzl", "pom_file", "POM_VERSION")

java_library(
    name = "gwt",
    resource_strip_prefix = "gwt/",
    resources = glob(["**/*.gwt.xml"]),
    tags = ["maven_coordinates=com.google.dagger:dagger-gwt:" + POM_VERSION],
    exports = [
        ":manual_deps",
        "//java/dagger:core",
    ],
)

java_library(
    name = "manual_deps",
    tags = [
        "maven_coordinates=com.google.dagger:dagger:%s:jar:sources" % POM_VERSION,
        "maven_coordinates=javax.inject:javax.inject:1:jar:sources",
    ],
    visibility = ["//visibility:private"],
)

pom_file(
    name = "pom",
    artifact_id = "dagger-gwt",
    artifact_name = "Dagger GWT",
    targets = [
        ":gwt",
        ":manual_deps",
    ],
)
