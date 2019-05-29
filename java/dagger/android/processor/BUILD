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

# Description:
#   Public Dagger API for Android

package(default_visibility = ["//:src"])

load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
)
load("//tools:maven.bzl", "POM_VERSION", "pom_file")

filegroup(
    name = "srcs",
    srcs = glob(["*.java"]),
)

java_library(
    name = "processor",
    srcs = [":srcs"],
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    tags = ["maven_coordinates=com.google.dagger:dagger-android-processor:" + POM_VERSION],
    deps = [
        "@google_bazel_common//third_party/java/guava",
        "@google_bazel_common//third_party/java/auto:service",
        "@google_bazel_common//third_party/java/auto:value",
        "@google_bazel_common//third_party/java/auto:common",
        "@google_bazel_common//third_party/java/incap",
        "@google_bazel_common//third_party/java/javapoet",
        "@google_bazel_common//third_party/java/google_java_format",
        "//java/dagger:core",
        "//java/dagger/model",
        "//java/dagger/spi",
        # https://github.com/bazelbuild/bazel/issues/2517
        ":dagger-android-jar",
    ],
)

# https://github.com/bazelbuild/bazel/issues/2517
# This target serves two (related) purposes:
# 1. Bazel does not allow a java_library to depend on an android_library, even if that java_library
# will be used in a java_plugin.
# 2. It stores the metadata for the "jarimpl" target that we use to work-around Gradle not loading
# aar artifacts that are declared as deps of an annotation processor. Our pom.xml generator reads
# the tags and includes them apppropriately.
java_import(
    name = "dagger-android-jar",
    jars = ["//java/dagger/android:libandroid.jar"],
    tags = ["maven_coordinates=com.google.dagger:dagger-android-jarimpl:" + POM_VERSION],
    visibility = ["//visibility:private"],
)

pom_file(
    name = "pom",
    artifact_id = "dagger-android-processor",
    artifact_name = "Dagger Android Processor",
    targets = [":processor"],
)

java_plugin(
    name = "plugin",
    generates_api = 1,
    processor_class = "dagger.android.processor.AndroidProcessor",
    deps = [":processor"],
)

load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")

javadoc_library(
    name = "processor-javadoc",
    srcs = [":srcs"],
    root_packages = ["dagger.android.processor"],
    deps = [":processor"],
)
