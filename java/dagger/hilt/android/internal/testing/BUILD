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

package(default_visibility = ["//:src"])

java_library(
    name = "test_injector",
    testonly = 1,
    srcs = ["TestInjector.java"],
)

android_library(
    name = "internal_test_root",
    srcs = [
        "InternalTestRoot.java",
    ],
    deps = [
        "//java/dagger/hilt:generates_root_input",
    ],
)

android_library(
    name = "test_application_component_manager",
    testonly = 1,
    srcs = ["TestApplicationComponentManager.java"],
    deps = [
        "//java/dagger/hilt/android/internal/managers:component_supplier",
        "//java/dagger/hilt/android/testing:on_component_ready_runner",
        "//java/dagger/hilt/internal:component_manager",
        "//java/dagger/hilt/internal:preconditions",
        "@google_bazel_common//third_party/java/junit",
    ],
)

android_library(
    name = "test_application_component_manager_holder",
    testonly = 1,
    srcs = ["TestApplicationComponentManagerHolder.java"],
)

android_library(
    name = "test_instance_holder",
    testonly = 1,
    srcs = ["TestInstanceHolder.java"],
)
