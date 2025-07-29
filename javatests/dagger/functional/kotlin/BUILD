# Copyright (C) 2019 The Dagger Authors.
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
#   Functional test code for Dagger-Android

load("@io_bazel_rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")
load("@rules_java//java:defs.bzl", "java_library")
load("//:test_defs.bzl", "GenJavaTests")

package(default_visibility = ["//:src"])

GenJavaTests(
    name = "PropertyQualifierTest",
    srcs = ["PropertyQualifierTest.java"],
    deps = [
        ":FooWithInjectedQualifier",
        ":PropertyQualifierClasses",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

kt_jvm_library(
    name = "PropertyQualifierClasses",
    srcs = ["PropertyQualifierClasses.kt"],
    # TODO(danysantiago): Remove 'plugins' once kt_jvm_library supports 'exported_plugins'.
    plugins = ["//javatests/dagger/functional/kotlin/processor:plugin"],
    deps = [
        ":FooWithInjectedQualifier",
        ":java_qualifier",
        "//javatests/dagger/functional/kotlin/processor:annotation",
        "//third_party/java/dagger",
    ],
)

kt_jvm_library(
    name = "FooWithInjectedQualifier",
    srcs = ["FooWithInjectedQualifier.kt"],
    deps = [
        ":java_qualifier",
        "//third_party/java/dagger",
    ],
)

java_library(
    name = "java_qualifier",
    srcs = [
        "JavaTestQualifier.java",
        "JavaTestQualifierWithTarget.java",
    ],
    deps = [
        "//third_party/java/dagger",
    ],
)

GenJavaTests(
    name = "ObjectModuleTest",
    srcs = ["ObjectModuleTest.java"],
    deps = [
        ":ObjectModuleClasses",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

kt_jvm_library(
    name = "ObjectModuleClasses",
    srcs = [
        "ObjectModuleClasses.kt",
        "PublicModuleWithNonPublicInclude.java",
    ],
    # TODO(danysantiago): Remove 'plugins' once kt_jvm_library supports 'exported_plugins'.
    plugins = ["//javatests/dagger/functional/kotlin/processor:plugin"],
    deps = [
        "//third_party/java/dagger",
    ],
)

GenJavaTests(
    name = "DependsOnGeneratedCodeTest",
    srcs = ["DependsOnGeneratedCodeTest.java"],
    deps = [
        ":DependsOnGeneratedCodeClasses",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

kt_jvm_library(
    name = "DependsOnGeneratedCodeClasses",
    srcs = ["DependsOnGeneratedCodeClasses.kt"],
    # TODO(danysantiago): Remove 'plugins' once kt_jvm_library supports 'exported_plugins'.
    plugins = ["//javatests/dagger/functional/kotlin/processor:plugin"],
    deps = [
        "//third_party/java/auto:factory",
        "//third_party/java/dagger",
    ],
)

GenJavaTests(
    name = "CompanionModuleTest",
    srcs = ["CompanionModuleTest.java"],
    deps = [
        ":CompanionModuleClasses",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

kt_jvm_library(
    name = "CompanionModuleClasses",
    srcs = ["CompanionModuleClasses.kt"],
    # TODO(danysantiago): Remove 'plugins' once kt_jvm_library supports 'exported_plugins'.
    plugins = ["//javatests/dagger/functional/kotlin/processor:plugin"],
    deps = [
        "//third_party/java/dagger",
    ],
)
