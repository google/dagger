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
#   Hilt internal libraries for testing processors.

package(default_visibility = ["//:src"])

java_library(
    name = "injector_entry_point_generator",
    srcs = [
        "InjectorEntryPointGenerator.java",
    ],
    deps = [
        "//java/dagger/hilt/processor/internal:classnames",
        "//java/dagger/hilt/processor/internal:processors",
        "//java/dagger/internal/guava:base",
        "//java/dagger/internal/guava:collect",
        "@google_bazel_common//third_party/java/javapoet",
    ],
)

java_plugin(
    name = "internal_test_root_processor",
    generates_api = 1,
    processor_class = "dagger.hilt.android.processor.internal.testing.InternalTestRootProcessor",
    tags = [
        "annotation=dagger.hilt.android.internal.testing.InternalTestRoot;" +
        "genclass=${package}.${outerclasses}${classname}_Injector;",
    ],
    deps = [
        ":internal_test_root_processor_lib",
    ],
)

java_library(
    name = "internal_test_root_processor_lib",
    srcs = [
        "InternalTestRootProcessor.java",
    ],
    deps = [
        ":injector_entry_point_generator",
        ":internal_test_root_metadata",
        "//java/dagger/hilt/android/processor/internal:android_classnames",
        "//java/dagger/hilt/processor/internal:base_processor",
        "//java/dagger/hilt/processor/internal:classnames",
        "//java/dagger/hilt/processor/internal:processors",
        "//java/dagger/internal/guava:collect",
        "@google_bazel_common//third_party/java/auto:service",
        "@google_bazel_common//third_party/java/incap",
    ],
)

java_library(
    name = "internal_test_root_metadata",
    srcs = [
        "InternalTestRootMetadata.java",
    ],
    deps = [
        "//java/dagger/hilt/processor/internal:classnames",
        "//java/dagger/hilt/processor/internal:processor_errors",
        "//java/dagger/hilt/processor/internal:processors",
        "//java/dagger/internal/guava:annotations",
        "@google_bazel_common//third_party/java/auto:common",
        "@google_bazel_common//third_party/java/auto:value",
        "@google_bazel_common//third_party/java/javapoet",
        "@google_bazel_common//third_party/java/jsr250_annotations",
    ],
)

java_library(
    name = "test_application_generator",
    srcs = [
        "TestApplicationGenerator.java",
    ],
    deps = [
        "//java/dagger/hilt/android/processor/internal:android_classnames",
        "//java/dagger/hilt/processor/internal:classnames",
        "//java/dagger/hilt/processor/internal:component_names",
        "//java/dagger/hilt/processor/internal:processors",
        "//java/dagger/hilt/processor/internal/root:root_metadata",
        "//java/dagger/internal/guava:annotations",
        "//java/dagger/internal/guava:collect",
        "@google_bazel_common//third_party/java/javapoet",
    ],
)
