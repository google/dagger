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
#   Internal code for implementing Hilt android processors.

package(default_visibility = ["//:src"])

java_library(
    name = "android_generators",
    srcs = [
        "ActivityGenerator.java",
        "ApplicationGenerator.java",
        "BroadcastReceiverGenerator.java",
        "FragmentGenerator.java",
        "Generators.java",
        "InjectorEntryPointGenerator.java",
        "ServiceGenerator.java",
        "ViewGenerator.java",
    ],
    deps = [
        ":android_classnames",
        ":metadata",
        ":utils",
        "//:dagger_with_compiler",
        "//java/dagger/hilt/processor/internal:classnames",
        "//java/dagger/hilt/processor/internal:component_descriptor",
        "//java/dagger/hilt/processor/internal:component_names",
        "//java/dagger/hilt/processor/internal:processor_errors",
        "//java/dagger/hilt/processor/internal:processors",
        "//java/dagger/internal/guava:base",
        "//java/dagger/internal/guava:collect",
        "@google_bazel_common//third_party/java/auto:common",
        "@google_bazel_common//third_party/java/javapoet",
    ],
)

java_library(
    name = "metadata",
    srcs = [
        "AndroidEntryPointMetadata.java",
    ],
    deps = [
        ":android_classnames",
        ":compiler_options",
        "//java/dagger/hilt/processor/internal:classnames",
        "//java/dagger/hilt/processor/internal:components",
        "//java/dagger/hilt/processor/internal:processor_errors",
        "//java/dagger/hilt/processor/internal:processors",
        "//java/dagger/internal/guava:base",
        "//java/dagger/internal/guava:collect",
        "@google_bazel_common//third_party/java/auto:common",
        "@google_bazel_common//third_party/java/auto:value",
        "@google_bazel_common//third_party/java/javapoet",
    ],
)

java_library(
    name = "android_classnames",
    srcs = [
        "AndroidClassNames.java",
    ],
    deps = [
        "@google_bazel_common//third_party/java/javapoet",
    ],
)

# TODO(user): Merge this into other utils
java_library(
    name = "utils",
    srcs = [
        "MoreTypes.java",
    ],
    deps = [
        "//java/dagger/internal/guava:base",
        "//java/dagger/internal/guava:collect",
    ],
)

java_library(
    name = "compiler_options",
    srcs = ["HiltCompilerOptions.java"],
)
