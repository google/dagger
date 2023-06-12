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
#   Hilt android processors.

package(default_visibility = ["//:src"])

java_plugin(
    name = "plugin",
    generates_api = 1,
    processor_class = "dagger.hilt.android.processor.internal.androidentrypoint.AndroidEntryPointProcessor",
    tags = [
        "annotation=dagger.hilt.android.AndroidEntryPoint;" +
        "genclass=${package}.Hilt_${outerclasses}${classname};" +
        "genclass=${package}.${outerclasses}${classname}_EntryPoint",
    ],
    deps = [
        ":processor_lib",
    ],
)

java_library(
    name = "processor_lib",
    srcs = [
        "AndroidEntryPointProcessingStep.java",
        "AndroidEntryPointProcessor.java",
    ],
    deps = [
        ":android_generators",
        ":metadata",
        "//java/dagger/hilt/android/processor/internal:android_classnames",
        "//java/dagger/hilt/processor/internal:base_processor",
        "//java/dagger/hilt/processor/internal:compiler_options",
        "//java/dagger/hilt/processor/internal:processor_errors",
        "//java/dagger/hilt/processor/internal/optionvalues",
        "//java/dagger/internal/codegen/xprocessing",
        "//third_party/java/auto:service",
        "//third_party/java/guava/collect",
        "//third_party/java/incap",
        "//third_party/java/javapoet",
    ],
)

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
        ":metadata",
        "//java/dagger/hilt/android/processor/internal:android_classnames",
        "//java/dagger/hilt/processor/internal:classnames",
        "//java/dagger/hilt/processor/internal:component_names",
        "//java/dagger/hilt/processor/internal:processor_errors",
        "//java/dagger/hilt/processor/internal:processors",
        "//java/dagger/internal/codegen/extension",
        "//java/dagger/internal/codegen/xprocessing",
        "//third_party/java/auto:common",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/javapoet",
        "@maven//:org_jetbrains_kotlin_kotlin_stdlib",
    ],
)

java_library(
    name = "metadata",
    srcs = [
        "AndroidEntryPointMetadata.java",
    ],
    deps = [
        "//java/dagger/hilt/android/processor/internal:android_classnames",
        "//java/dagger/hilt/processor/internal:compiler_options",
        "//java/dagger/hilt/processor/internal:components",
        "//java/dagger/hilt/processor/internal:processor_errors",
        "//java/dagger/hilt/processor/internal:processors",
        "//java/dagger/hilt/processor/internal/kotlin",
        "//java/dagger/internal/codegen/extension",
        "//java/dagger/internal/codegen/xprocessing",
        "//third_party/java/auto:value",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/javapoet",
    ],
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)
