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

load("@rules_java//java:defs.bzl", "java_library")
load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")
load("@google_bazel_common//tools/jarjar:jarjar.bzl", "jarjar_library")
load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "define_kt_toolchain")

package(default_visibility = ["//visibility:public"])

define_kt_toolchain(
    name = "kotlin_toolchain",
    api_version = "1.4",
    jvm_target = "1.8",
    language_version = "1.4",
)

package_group(
    name = "src",
    packages = ["//..."],
)

java_library(
    name = "dagger_with_compiler",
    exported_plugins = ["//java/dagger/internal/codegen:component-codegen"],
    exports = ["//java/dagger:core"],
)

java_library(
    name = "producers_with_compiler",
    exports = [
        ":dagger_with_compiler",
        "//java/dagger/producers",
    ],
)

java_library(
    name = "spi",
    exports = ["//java/dagger/spi"],
)

java_library(
    name = "compiler_internals",
    exports = [
        "//java/dagger/internal/codegen:processor",
        "//java/dagger/internal/codegen/base",
        "//java/dagger/internal/codegen/binding",
        "//java/dagger/internal/codegen/validation",
        "//java/dagger/internal/codegen/writing",
    ],
)

android_library(
    name = "android",
    exported_plugins = ["//java/dagger/android/processor:plugin"],
    exports = ["//java/dagger/android"],
)

android_library(
    name = "android-support",
    exports = [
        ":android",
        "//java/dagger/android/support",
    ],
)

jarjar_library(
    name = "shaded_grpc_server_processor",
    jars = [
        "//java/dagger/grpc/server/processor",
        "//third_party/java/auto:common",
    ],
    rules = [
        "rule com.google.auto.common.** dagger.grpc.shaded.auto.common.@1",
    ],
)

android_library(
    name = "android_local_test_exports",
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
        "//java/dagger:javadoc-srcs",
        "//java/dagger/android:android-srcs",
        "//java/dagger/android/support:support-srcs",
        "//java/dagger/grpc/server:javadoc-srcs",
        "//java/dagger/grpc/server/processor:javadoc-srcs",
        "//java/dagger/hilt:javadoc-srcs",
        "//java/dagger/producers:producers-srcs",
        "//java/dagger/spi:spi-srcs",
    ],
    android_api_level = 32,
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
        "//java/dagger:core",
        "//java/dagger/android",
        "//java/dagger/android/support",
        "//java/dagger/grpc/server",
        "//java/dagger/grpc/server/processor",
        "//java/dagger/hilt/android:artifact-lib",
        "//java/dagger/hilt/android/testing:artifact-lib",
        "//java/dagger/producers",
        "//java/dagger/spi",
    ],
)
