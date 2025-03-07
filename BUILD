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

load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "define_kt_toolchain")
load("@rules_java//java:defs.bzl", "java_library")
load("//tools/javadoc:javadoc.bzl", "javadoc_library")

package(default_visibility = ["//visibility:public"])

package_group(
    name = "src",
    packages = ["//..."],
)

define_kt_toolchain(
    name = "kotlin_toolchain",
    api_version = "1.6",
    jvm_target = "1.8",
    language_version = "1.6",
)

java_library(
    name = "dagger_with_compiler",
    exported_plugins = ["//dagger-compiler/main/java/dagger/internal/codegen:component-codegen"],
    exports = ["//dagger-runtime/main/java/dagger:core"],
)

java_library(
    name = "producers_with_compiler",
    exports = [
        ":dagger_with_compiler",
        "//dagger-producers/main/java/dagger/producers",
    ],
)

java_library(
    name = "compiler_internals",
    exports = [
        "//dagger-compiler/main/java/dagger/internal/codegen:processor",
        "//dagger-compiler/main/java/dagger/internal/codegen/base",
        "//dagger-compiler/main/java/dagger/internal/codegen/binding",
        "//dagger-compiler/main/java/dagger/internal/codegen/validation",
        "//dagger-compiler/main/java/dagger/internal/codegen/writing",
    ],
)

android_library(
    name = "android",
    exported_plugins = ["//dagger-android-processor:plugin"],
    exports = ["//dagger-android/main/java/dagger/android"],
)

android_library(
    name = "android-support",
    exports = [
        ":android",
        "//dagger-android-support/main/java/dagger/android/support",
    ],
)

android_library(
    name = "android_local_test_exports",
    testonly = 1,
    exports = [
        # TODO(bcorso): see if we can remove jsr250 dep from autovalue to prevent this.
        "@maven//:javax_annotation_javax_annotation_api",  # For @Generated
        "@maven//:org_robolectric_shadows_framework",  # For ActivityController
        "@maven//:androidx_lifecycle_lifecycle_common",  # For Lifecycle.State
        "@maven//:androidx_activity_activity",  # For ComponentActivity
        "@maven//:androidx_test_core",  # For ApplicationProvider
        "@maven//:androidx_test_ext_junit",
        "@maven//:org_robolectric_annotations",
        "@maven//:org_robolectric_robolectric",
        "@robolectric//bazel:android-all",
    ],
)

# coalesced javadocs used for the gh-pages site
javadoc_library(
    name = "user-docs",
    testonly = 1,
    srcs = [
        "//dagger-android-support/main/java/dagger/android/support:support-srcs",
        "//dagger-android/main/java/dagger/android:android-srcs",
        "//dagger-producers/main/java/dagger/producers:producers-srcs",
        "//dagger-runtime/main/java/dagger:javadoc-srcs",
        "//dagger-spi:srcs",
        "//hilt-core:javadoc-srcs",
        "//java/dagger/grpc/server:javadoc-srcs",
        "//java/dagger/hilt:javadoc-srcs",
    ],
    android_api_level = 34,
    # TODO(ronshapiro): figure out how to specify the version number for release builds
    doctitle = "Dagger Dependency Injection API",
    exclude_packages = [
        "dagger.hilt.android.internal",
        "dagger.hilt.internal",
        "dagger.internal",
        "dagger.producers.internal",
        "dagger.producers.monitoring.internal",
    ],
    root_packages = ["dagger"],
    deps = [
        "//dagger-android-support/main/java/dagger/android/support",
        "//dagger-android/main/java/dagger/android",
        "//dagger-producers/main/java/dagger/producers",
        "//dagger-runtime/main/java/dagger:core",
        "//dagger-spi",
        "//java/dagger/grpc/server",
        "//java/dagger/hilt/android:artifact-lib",
        "//java/dagger/hilt/android/testing:artifact-lib",
    ],
)
