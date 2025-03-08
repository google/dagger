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

# Description:
#   Internal Hilt Android managers

load("@rules_android//rules:rules.bzl", "android_library")

package(default_visibility = ["//:src"])

android_library(
    name = "component_supplier",
    srcs = ["ComponentSupplier.java"],
)

android_library(
    name = "managers",
    srcs = [
        "ActivityComponentManager.java",
        "ActivityRetainedComponentManager.java",
        "ApplicationComponentManager.java",
        "BroadcastReceiverComponentManager.java",
        "FragmentComponentManager.java",
        "SavedStateHandleModule.java",
        "ServiceComponentManager.java",
        "ViewComponentManager.java",
    ],
    exports = [
        ":saved_state_handle_holder",
    ],
    deps = [
        ":component_supplier",
        ":saved_state_handle_holder",
        "//:dagger_with_compiler",
        "//hilt-android/main/java/dagger/hilt/android:activity_retained_lifecycle",
        "//hilt-android/main/java/dagger/hilt/android:entry_point_accessors",
        "//hilt-android/main/java/dagger/hilt/android:unstable_api",
        "//hilt-android/main/java/dagger/hilt/android/components",
        "//hilt-android/main/java/dagger/hilt/android/internal",
        "//hilt-android/main/java/dagger/hilt/android/internal/builders",
        "//hilt-android/main/java/dagger/hilt/android/internal/lifecycle",
        "//hilt-android/main/java/dagger/hilt/android/lifecycle:activity_retained_saved_state",
        "//hilt-android/main/java/dagger/hilt/android/scopes",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//hilt-core/main/java/dagger/hilt/internal:component_manager",
        "//hilt-core/main/java/dagger/hilt/internal:preconditions",
        "@maven//:androidx_activity_activity",
        "@maven//:androidx_annotation_annotation",
        "@maven//:androidx_annotation_annotation_experimental",
        "@maven//:androidx_annotation_annotation_jvm",
        "@maven//:androidx_fragment_fragment",
        "@maven//:androidx_lifecycle_lifecycle_common",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
    ],
)

android_library(
    name = "saved_state_handle_holder",
    srcs = ["SavedStateHandleHolder.java"],
    deps = [
        "//hilt-android/main/java/dagger/hilt/android/internal",
        "//hilt-core/main/java/dagger/hilt/internal:preconditions",
        "@maven//:androidx_activity_activity",
        "@maven//:androidx_annotation_annotation",
        "@maven//:androidx_annotation_annotation_jvm",
        "@maven//:androidx_fragment_fragment",
        "@maven//:androidx_lifecycle_lifecycle_common",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
