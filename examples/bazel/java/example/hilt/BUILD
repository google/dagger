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

android_binary(
    name = "hilt",
    srcs = ["CoffeeApp.java"],
    manifest = "AndroidManifest.xml",
    resource_files = glob(["res/**"]),
    deps = [
        ":heater_module",
        ":pump_module",
        "//:hilt-android",
        "//java/example/common",
    ],
)

android_library(
    name = "heater_module",
    srcs = ["HeaterModule.java"],
    manifest = "AndroidManifest.xml",
    visibility = ["//javatests/example/hilt:__pkg__"],
    deps = [
        "//:hilt-android",
        "//java/example/common",
    ],
)

android_library(
    name = "pump_module",
    srcs = ["PumpModule.java"],
    manifest = "AndroidManifest.xml",
    visibility = ["//javatests/example/hilt:__pkg__"],
    deps = [
        "//:hilt-android",
        "//java/example/common",
    ],
)
