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
#   Internal Hilt android testing libraries

load("@rules_android//rules:rules.bzl", "android_library")
load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//:src"])

java_library(
    name = "test_injector",
    testonly = 1,
    srcs = [
        "TestInjector.java",
    ],
)

android_library(
    name = "internal_test_root",
    srcs = [
        "InternalTestRoot.java",
    ],
    deps = [
        "//hilt-core/main/java/dagger/hilt:generates_root_input",
    ],
)

android_library(
    name = "early_test_singleton_component_creator",
    testonly = 1,
    srcs = ["EarlySingletonComponentCreator.java"],
)

android_library(
    name = "test_application_component_manager",
    testonly = 1,
    srcs = ["TestApplicationComponentManager.java"],
    deps = [
        ":early_test_singleton_component_creator",
        ":test_component_data",
        ":test_injector",
        "//hilt-android/main/java/dagger/hilt/internal:test_singleton_component_manager",
        "//hilt-core/main/java/dagger/hilt/internal:component_manager",
        "//hilt-core/main/java/dagger/hilt/internal:preconditions",
        "//java/dagger/hilt/android/testing:on_component_ready_runner",
        "@maven//:junit_junit",
    ],
)

android_library(
    name = "test_component_data",
    testonly = 1,
    srcs = [
        "TestComponentData.java",
        "TestComponentDataSupplier.java",
    ],
    deps = [
        ":test_injector",
        "//hilt-core/main/java/dagger/hilt/internal:component_manager",
    ],
)

android_library(
    name = "test_application_component_manager_holder",
    testonly = 1,
    srcs = ["TestApplicationComponentManagerHolder.java"],
    deps = [
        "//hilt-core/main/java/dagger/hilt/internal:component_manager",
    ],
)

android_library(
    name = "mark_that_rules_ran_rule",
    testonly = 1,
    srcs = ["MarkThatRulesRanRule.java"],
    deps = [
        ":test_application_component_manager",
        ":test_application_component_manager_holder",
        "//hilt-android/main/java/dagger/hilt/android/internal",
        "//hilt-core/main/java/dagger/hilt/internal:component_manager",
        "//hilt-core/main/java/dagger/hilt/internal:preconditions",
        "@maven//:androidx_test_core",
        "@maven//:junit_junit",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["**/*"]),
)
