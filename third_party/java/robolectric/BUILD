# Copyright (C) 2025 The Dagger Authors.
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

load("@rules_java//java:defs.bzl", "java_library")

# BUILD rules for Robolectric

package(default_visibility = ["//:src"])

java_library(
    name = "robolectric",
    testonly = True,
    exports = [
        "@maven//:androidx_test_core",
        "@maven//:androidx_test_ext_junit",
        "@maven//:org_robolectric_robolectric",
    ],
)

alias(
    name = "annotations",
    actual = "@maven//:org_robolectric_annotations",
)
