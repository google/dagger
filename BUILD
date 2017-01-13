# Copyright (C) 2017 The Dagger Authors.
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

package(default_visibility = ["//visibility:public"])

package_group(
    name = "src",
    packages = ["//..."],
)

py_test(
    name = "maven_sha1_test",
    srcs = ["tools/maven_sha1_test.py"],
    data = [":WORKSPACE"],
)

java_library(
    name = "dagger_with_compiler",
    exported_plugins = ["//compiler:component-codegen"],
    exports = ["//core"],
)

java_library(
    name = "producers_with_compiler",
    exports = [
        ":dagger_with_compiler",
        "//producers",
    ],
)
