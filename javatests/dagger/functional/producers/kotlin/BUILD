# Copyright (C) 2024 The Dagger Authors.
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
#
# Description:
#   Producers test code with Kotlin language features

load("@io_bazel_rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")
load("//:test_defs.bzl", "GenJavaTests")

package(default_visibility = ["//:src"])

GenJavaTests(
    name = "ObjectModuleTest",
    srcs = ["ObjectModuleTest.java"],
    deps = [
        ":ObjectModuleClasses",
        "//third_party/java/guava/util/concurrent",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

kt_jvm_library(
    name = "ObjectModuleClasses",
    srcs = [
        "ObjectModuleClasses.kt",
    ],
    deps = [
        "//third_party/java/dagger",
        "//third_party/java/dagger:producers",
        "//third_party/java/guava/util/concurrent",
    ],
)
