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
#   A library for the kythe plugin.

load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//:src"])

java_library(
    name = "kythe",
    srcs = glob(["*.java"]),
    plugins = ["//java/dagger/internal/codegen:component-codegen"],
    deps = [
        ":kythe_plugin",
        "//java/dagger:core",
        "//java/dagger/internal/codegen/base",
        "//java/dagger/internal/codegen/binding",
        "//java/dagger/internal/codegen/javac",
        "//java/dagger/internal/codegen/javapoet",
        "//java/dagger/internal/codegen/kotlin",
        "//java/dagger/internal/codegen/model",
        "//java/dagger/internal/codegen/validation",
        "//java/dagger/internal/codegen/xprocessing",
        "//third_party/java/auto:service",
        "//third_party/java/error_prone:annotations",
        "//third_party/java/guava/collect",
        "//third_party/java/jsr330_inject",
    ],
)

load("@rules_java//java:defs.bzl", "java_import")

# A _deploy.jar consisting of the java_librarys in https://github.com/kythe/kythe needed to build a
# Kythe plugin
# TODO(ronshapiro): replace this with a http_archive of the next release in
# https://github.com/kythe/kythe/releases
java_import(
    name = "kythe_plugin",
    jars = ["kythe_plugin_deploy.jar"],
    neverlink = 1,
)
