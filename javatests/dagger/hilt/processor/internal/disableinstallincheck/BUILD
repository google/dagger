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
#
# Description:
#  Builds and run tests related to DisableInstallInCheckProcessor.

load("//java/dagger/testing/compile:macros.bzl", "compiler_test")

compiler_test(
    name = "DisableInstallInCheckProcessorErrorsTest",
    size = "small",
    srcs = ["DisableInstallInCheckProcessorErrorsTest.java"],
    compiler_deps = [
        "//hilt-core/main/java/dagger/hilt/migration:disable_install_in_check",
        "//third_party/java/dagger",
        "//third_party/java/jsr250_annotations",
        "//hilt-core/main/java/dagger/hilt:entry_point",
        "//hilt-core/main/java/dagger/hilt:install_in",
        "//hilt-android/main/java/dagger/hilt/android/components",
    ],
    deps = [
        "//dagger-compiler:xprocessing",
        "//dagger-compiler/main/java/dagger/internal/codegen/xprocessing:xprocessing-testing",
        "//hilt-android-testing/main/java/dagger/hilt/android/testing/compile",
        "//hilt-compiler/main/java/dagger/hilt/processor/internal/disableinstallincheck:processor_lib",
        "//third_party/java/junit",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
