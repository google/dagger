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
#   Tests for internal code for implementing Hilt processors.

package(default_visibility = ["//:src"])

java_library(
    name = "android_compilers",
    srcs = ["AndroidCompilers.java"],
    deps = [
        "//java/dagger/hilt/android/processor/internal/androidentrypoint:processor_lib",
        "//java/dagger/hilt/android/processor/internal/customtestapplication:processor_lib",
        "//java/dagger/hilt/android/processor/internal/uninstallmodules:processor_lib",
        "//java/dagger/hilt/android/processor/internal/viewmodelinject:processor_lib",
        "//java/dagger/hilt/processor/internal/aggregateddeps:processor_lib",
        "//java/dagger/hilt/processor/internal/definecomponent:processor_lib",
        "//java/dagger/hilt/processor/internal/generatesrootinput:processor_lib",
        "//java/dagger/hilt/processor/internal/originatingelement:processor_lib",
        "//java/dagger/hilt/processor/internal/root:processor_lib",
        "//java/dagger/internal/codegen:processor",
        "//java/dagger/internal/guava:collect",
        "//java/dagger/testing/compile",
        "@google_bazel_common//third_party/java/compile_testing",
        "@maven//:com_github_tschuchortdev_kotlin_compile_testing",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
