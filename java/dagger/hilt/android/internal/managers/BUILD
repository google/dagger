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
        "ServiceComponentManager.java",
        "ViewComponentManager.java",
    ],
    deps = [
        ":component_supplier",
        "//:dagger_with_compiler",
        "//java/dagger/hilt:entry_point",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android:activity_retained_lifecycle",
        "//java/dagger/hilt/android/components",
        "//java/dagger/hilt/android/components:view_model_component",
        "//java/dagger/hilt/android/internal",
        "//java/dagger/hilt/android/internal/builders",
        "//java/dagger/hilt/android/scopes:activity_retained_scoped",
        "//java/dagger/hilt/android/scopes:view_model_scoped",
        "//java/dagger/hilt/internal:component_manager",
        "//java/dagger/hilt/internal:preconditions",
        "@maven//:androidx_activity_activity",
        "@maven//:androidx_annotation_annotation",
        "@maven//:androidx_fragment_fragment",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
