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

load("@rules_java//java:defs.bzl", "java_library")

# BUILD rules for androidx.annotation

package(default_visibility = ["//:src"])

# Alias target for androidx.annotation
# Ideally libraries could directly depend on @maven//:androidx_annotation_annotation but:
# 1. Directly depending on annotation doesn't work since it contains no classes,
# and results in missing symbol errors.
# 2. Directly depending on annotation-jvm doesn't work since that can result in
# duplicate class errors when transitive dependencies depend on older versions
# of androidx.annotation
java_library(
    name = "annotation",
    exports = [
        "@maven//:androidx_annotation_annotation",
        "@maven//:androidx_annotation_annotation_jvm",
    ],
)
