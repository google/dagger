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
#   Hilt android modules for standard components.

package(default_visibility = ["//:src"])

android_library(
    name = "modules",
    srcs = glob(["*.java"]),
    deps = [
        "//:dagger_with_compiler",
        "//java/dagger/hilt:install_in",
        "//java/dagger/hilt/android/components",
        "//java/dagger/hilt/android/components:view_model_component",
        "//java/dagger/hilt/android/internal/builders",
        "//java/dagger/hilt/android/internal/lifecycle",
        "//java/dagger/hilt/android/lifecycle",
        "//java/dagger/hilt/android/qualifiers",
        "@maven//:androidx_activity_activity",
        "@maven//:androidx_annotation_annotation",
        "@maven//:androidx_fragment_fragment",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel",
        "@maven//:androidx_lifecycle_lifecycle_viewmodel_savedstate",
        "@maven//:androidx_savedstate_savedstate",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
