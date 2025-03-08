# Copyright (C) 2021 The Dagger Authors.
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
#  Builds and run tests related to AliasOfProcessor.

load("//java/dagger/testing/compile:macros.bzl", "compiler_test")

package(default_visibility = ["//:src"])

compiler_test(
    name = "AliasOfProcessorTest",
    size = "small",
    srcs = ["AliasOfProcessorTest.java"],
    compiler_deps = [
        "@androidsdk//:platforms/android-34/android.jar",
        "//third_party/java/jsr330_inject",
        "//hilt-android/main/java/dagger/hilt/android:hilt_android_app",
        "//hilt-android/main/java/dagger/hilt/android/components",
        "//hilt-core/main/java/dagger/hilt:define_component",
        "//hilt-core/main/java/dagger/hilt/migration:alias_of",
    ],
    deps = [
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//java/dagger/hilt/android/testing/compile",
        "//third_party/java/junit",
    ],
)
