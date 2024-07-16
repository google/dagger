# Copyright (C) 2024 The Dagger Authors.
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

# Starlark tests for jarjar

load("//tools/jarjar:jarjar.bzl", "jarjar_library")

# Test target used for validating rule.
jarjar_library(
    name = "test_target",
    testonly = 1,
    jars = [
        ":test-library1.jar",
        ":test-library2.jar",
    ],
    merge_meta_inf_files = ["utilities/libs.dep"],
)

# Test that validates jarjar with merged META-INF files.
sh_test(
    name = "validate_test_target",
    srcs = [":jarjar_validator.sh"],
    args = [
        "$(location :test_target.jar)",
        "utilities/libs.dep",
        "$(location :expected_libs.dep)",
    ],
    data = [
        ":expected_libs.dep",
        ":test_target.jar",
    ],
)
