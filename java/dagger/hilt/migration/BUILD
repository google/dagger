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
#   Libraries for migration.

package(default_visibility = ["//:src"])

java_library(
    name = "alias_of",
    srcs = [
        "AliasOf.java",
    ],
    exported_plugins = [
        "//java/dagger/hilt/processor/internal/aliasof:processor",
    ],
    exports = [
        "//java/dagger/hilt/internal/aliasof",
    ],
    deps = [
        ":package_info",
        "//java/dagger/hilt:generates_root_input",
    ],
)

java_library(
    name = "disable_install_in_check",
    srcs = ["DisableInstallInCheck.java"],
    exported_plugins = [
        "//java/dagger/hilt/processor/internal/disableinstallincheck:processor",
    ],
    exports = [
    ],
    deps = [
        ":package_info",
    ],
)

java_library(
    name = "package_info",
    srcs = ["package-info.java"],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
