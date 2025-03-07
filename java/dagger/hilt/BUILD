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
#   A library that wraps the Dagger API to make DI usage and testing easier.

package(default_visibility = ["//:src"])

filegroup(
    name = "javadoc-srcs",
    srcs = [
        ":hilt_android_filegroup",
        ":hilt_android_testing_filegroup",
        ":hilt_testing_filegroup",
    ],
)

filegroup(
    name = "hilt_testing_filegroup",
    srcs = [
        "//java/dagger/hilt/testing:srcs_filegroup",
    ],
)

filegroup(
    name = "hilt_android_filegroup",
    srcs = [
        "//java/dagger/hilt/android:srcs_filegroup",
        "//java/dagger/hilt/android/components:srcs_filegroup",
        "//java/dagger/hilt/android/flags:srcs_filegroup",
        "//java/dagger/hilt/android/internal:srcs_filegroup",
        "//java/dagger/hilt/android/internal/builders:srcs_filegroup",
        "//java/dagger/hilt/android/internal/lifecycle:srcs_filegroup",
        "//java/dagger/hilt/android/internal/managers:srcs_filegroup",
        "//java/dagger/hilt/android/internal/migration:srcs_filegroup",
        "//java/dagger/hilt/android/internal/modules:srcs_filegroup",
        "//java/dagger/hilt/android/lifecycle:srcs_filegroup",
        "//java/dagger/hilt/android/migration:srcs_filegroup",
        "//java/dagger/hilt/android/plugin/main:srcs_filegroup",
        "//java/dagger/hilt/android/qualifiers:srcs_filegroup",
        "//java/dagger/hilt/android/scopes:srcs_filegroup",
        "//java/dagger/hilt/internal:srcs_filegroup",
    ],
)

filegroup(
    name = "hilt_android_testing_filegroup",
    srcs = [
        "//java/dagger/hilt/android/internal/testing:srcs_filegroup",
        "//java/dagger/hilt/android/testing:srcs_filegroup",
    ],
)

filegroup(
    name = "hilt_processing_filegroup",
    srcs = [
        "//java/dagger/hilt/android/processor:srcs_filegroup",
        "//java/dagger/hilt/android/processor/internal:srcs_filegroup",
        "//java/dagger/hilt/android/processor/internal/androidentrypoint:srcs_filegroup",
        "//java/dagger/hilt/android/processor/internal/bindvalue:srcs_filegroup",
        "//java/dagger/hilt/android/processor/internal/customtestapplication:srcs_filegroup",
        "//java/dagger/hilt/android/processor/internal/viewmodel:srcs_filegroup",
        "//java/dagger/hilt/processor:srcs_filegroup",
        "//java/dagger/hilt/processor/internal:srcs_filegroup",
        "//java/dagger/hilt/processor/internal/aggregateddeps:srcs_filegroup",
        "//java/dagger/hilt/processor/internal/aliasof:srcs_filegroup",
        "//java/dagger/hilt/processor/internal/definecomponent:srcs_filegroup",
        "//java/dagger/hilt/processor/internal/disableinstallincheck:srcs_filegroup",
        "//java/dagger/hilt/processor/internal/generatesrootinput:srcs_filegroup",
        "//java/dagger/hilt/processor/internal/optionvalues:srcs_filegroup",
        "//java/dagger/hilt/processor/internal/originatingelement:srcs_filegroup",
        "//java/dagger/hilt/processor/internal/root:srcs_filegroup",
        "//java/dagger/hilt/processor/internal/root/ir:srcs_filegroup",
        "//java/dagger/hilt/processor/internal/uninstallmodules:srcs_filegroup",
    ],
)
