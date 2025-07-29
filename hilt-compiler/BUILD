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

load("@rules_java//java:defs.bzl", "java_library")
load("//:build_defs.bzl", "POM_VERSION")
load("//tools/maven:maven.bzl", "gen_maven_artifact")

package(default_visibility = ["//:src"])

_ARTIFACT_EXPORTS = [
    "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/androidentrypoint:processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/bindvalue:bind_value_processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/customtestapplication:processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/viewmodel:processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/viewmodel:validation_plugin_lib",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/aggregateddeps:processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/aliasof:processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/definecomponent:processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/earlyentrypoint:processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/generatesrootinput:processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/originatingelement:processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/root:component_tree_deps_processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/root:root_processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/uninstallmodules:processor_lib",
]

_ARTIFACT_TARGET_LIBS = [
    "//hilt-compiler/main/java/dagger/hilt/android/processor/internal:android_classnames",
    "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/androidentrypoint:android_generators",
    "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/androidentrypoint:metadata",
    "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/androidentrypoint:processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/bindvalue:bind_value_processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/customtestapplication:processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/viewmodel:validation_plugin_lib",
    "//hilt-compiler/main/java/dagger/hilt/android/processor/internal/viewmodel:processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal:aggregated_elements",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal:base_processor",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal:classnames",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal:compiler_options",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal:dagger_models",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal:component_descriptor",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal:component_names",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal:components",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal:hilt_processing_env_configs",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal:method_signature",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal:processor_errors",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal:processors",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/aggregateddeps:component_dependencies",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/aggregateddeps:pkg_private_metadata",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/aggregateddeps:processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/aliasof:alias_ofs",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/aliasof:processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/definecomponent:define_components",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/definecomponent:metadatas",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/definecomponent:processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/earlyentrypoint:aggregated_early_entry_point_metadata",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/earlyentrypoint:processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/generatesrootinput:generates_root_inputs",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/generatesrootinput:processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/kotlin",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/optionvalues:optionvalues",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/originatingelement:processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/root:component_tree_deps_processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/root:root_processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/root:root_metadata",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/root:root_type",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/root/ir:ir",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/uninstallmodules:processor_lib",
    "//hilt-compiler/main/java/dagger/hilt/processor/internal/uninstallmodules:aggregated_uninstall_modules_metadata",
]

_ARTIFACT_TARGET_MAVEN_DEPS = [
    "com.google.code.findbugs:jsr305",
    "com.google.dagger:dagger-compiler",
    "com.google.dagger:dagger",
    "com.google.dagger:dagger-spi",
    "com.google.devtools.ksp:symbol-processing-api",
    "com.google.guava:failureaccess",
    "com.google.guava:guava",
    "com.squareup:javapoet",
    "javax.inject:javax.inject",
    "net.ltgt.gradle.incap:incap",
    "org.jetbrains.kotlin:kotlin-metadata-jvm",
    "org.jetbrains.kotlin:kotlin-stdlib",
]

_JAVA_DOC_ROOT_PACKAGES = [
    # Java 11 javadocs requires non-empty root package so use ".internal" as the root package.
    "dagger.hilt.processor.internal",
    "dagger.hilt.android.processor.internal",
]

_JAVA_DOC_SRCS = [
    "//java/dagger/hilt:hilt_processing_filegroup",
]

java_library(
    name = "artifact-lib",
    tags = ["maven_coordinates=com.google.dagger:hilt-compiler:" + POM_VERSION],
    exports = _ARTIFACT_EXPORTS,
)

gen_maven_artifact(
    name = "artifact",
    artifact_coordinates = "com.google.dagger:hilt-compiler:" + POM_VERSION,
    artifact_name = "Hilt Processor",
    artifact_target = ":artifact-lib",
    artifact_target_libs = _ARTIFACT_TARGET_LIBS,
    artifact_target_maven_deps = _ARTIFACT_TARGET_MAVEN_DEPS,
    javadoc_android_api_level = 34,
    javadoc_root_packages = _JAVA_DOC_ROOT_PACKAGES,
    javadoc_srcs = _JAVA_DOC_SRCS,
)

java_library(
    name = "legacy-artifact-lib",
    tags = ["maven_coordinates=com.google.dagger:hilt-android-compiler:" + POM_VERSION],
    exports = _ARTIFACT_EXPORTS,
)

# TODO(bcorso): Remove this artifact. We renamed this artifact to hilt-compiler in Dagger 2.29 as we
# plan to create a non-android Hilt which will share the same compiler artifact as Hilt android.
gen_maven_artifact(
    name = "legacy-artifact",
    artifact_coordinates = "com.google.dagger:hilt-android-compiler:" + POM_VERSION,
    artifact_name = "Hilt Android Processor",
    artifact_target = ":legacy-artifact-lib",
    artifact_target_libs = _ARTIFACT_TARGET_LIBS,
    artifact_target_maven_deps = _ARTIFACT_TARGET_MAVEN_DEPS,
    javadoc_android_api_level = 34,
    javadoc_root_packages = _JAVA_DOC_ROOT_PACKAGES,
    javadoc_srcs = _JAVA_DOC_SRCS,
    pom_name = "legacy-pom",
)
