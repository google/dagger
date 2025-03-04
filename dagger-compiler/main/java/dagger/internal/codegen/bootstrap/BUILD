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
#   Bootstrap libraries for building Dagger with Dagger.

load("@rules_java//java:defs.bzl", "java_import", "java_plugin")

package(default_visibility = ["//:src"])

java_plugin(
    name = "bootstrap",
    generates_api = 1,
    processor_class = "dagger.internal.codegen.BootstrapProcessor",
    deps = [":bootstrap_compiler"],
)

java_import(
    name = "bootstrap_compiler",
    jars = ["bootstrap_compiler_deploy.jar"],
    visibility = ["//visibility:private"],
)
