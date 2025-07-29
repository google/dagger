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
#   An example of using dagger in a computerized fake ATM. The User's Guide (https://dagger.dev/users-guide)
#   is a walkthrough that ultimately builds this example.

load("@rules_java//java:defs.bzl", "java_binary", "java_library")

package(default_visibility = ["//:src"])

java_library(
    name = "atm",
    srcs = glob(["*.java"]),
    deps = ["//third_party/java/dagger"],
)

java_binary(
    name = "CommandLineAtm",
    main_class = "dagger.example.atm.CommandLineAtm",
    runtime_deps = [":atm"],
)
