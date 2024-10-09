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
load("@io_bazel_rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")
load("@rules_java//java:defs.bzl", "java_plugin")

package(default_visibility = ["//:src"])

java_plugin(
    name = "plugin",
    generates_api = 1,
    processor_class = "dagger.functional.kotlin.processor.TestGeneratedTypeProcessor",
    deps = [":processor"],
)

kt_jvm_library(
    name = "processor",
    srcs = ["TestGeneratedTypeProcessor.kt"],
    deps = [
        "//third_party/java/auto:service",
        "//third_party/java/javapoet",
    ],
)

kt_jvm_library(
    name = "annotation",
    srcs = ["TriggerGeneratedTypeProcessor.kt"],
)
