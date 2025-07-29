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

# Description:
#    Macros for processor plugins.

load("@rules_java//java:defs.bzl", "java_plugin")

def compat_processor_plugin(
        name,
        javac_processor_name,
        processor_lib_dep,
        generates_api = False,
        tags = [],
        visibility = None):

    full_path = native.package_name()
    java_index = max(0, full_path.rfind("/java/"))
    pkgs = full_path[java_index:].split("/")
    pkg = ".".join(pkgs[pkgs.index("java") + 1:])

    java_plugin(
        name = name,
        generates_api = generates_api,
        processor_class = pkg + "." + javac_processor_name,
        deps = [processor_lib_dep],
        tags = tags,
        visibility = visibility,
    )
