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

# Description:
#    Macros for building with Bazel.

load("@rules_java//java:defs.bzl", "java_library")
load("//third_party/kotlin/build_extensions:rules.bzl", "kt_android_library")
load("@io_bazel_rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

def compat_kt_android_library(name, **kwargs):
    bazel_kt_android_library(name, kwargs)

def compat_kt_jvm_library(name, **kwargs):
    bazel_kt_jvm_library(name, kwargs)

def compat_java_library(name, **kwargs):
    java_library(
        name = name,
        **kwargs
    )

def bazel_kt_android_library(name, kwargs):
    """A macro that wraps Bazel's kt_android_library.

    This macro wraps Bazel's kt_android_library to output the jars files
    in the expected locations (b/203519416). It also adds a dependency on
    kotlin_stdlib if there are kotlin sources.

    Args:
      name: the name of the library.
      kwargs: Additional arguments of the library.
    """

    # If there are any kotlin sources, add the kotlin_stdlib, otherwise
    # java-only projects may be missing a required runtime dependency on it.
    if any([src.endswith(".kt") for src in kwargs.get("srcs", [])]):
        # Add the kotlin_stdlib, otherwise it will be missing from java-only projects.
        # We use deps rather than exports because exports isn't picked up by the pom file.
        # See https://github.com/google/dagger/issues/3119
        required_deps = ["//third_party/kotlin/kotlin:kotlin_stdlib"]
        kwargs["deps"] = kwargs.get("deps", []) + required_deps

    # TODO(b/203519416): Bazel's kt_android_library outputs its jars under a target
    # suffixed with "_kt". Thus, we have to do a bit of name aliasing to ensure that
    # the jars exist at the expected targets.
    kt_android_library(
        name = "{}_internal".format(name),
        **kwargs
    )

    native.alias(
        name = name,
        actual = ":{}_internal_kt".format(name),
    )

    native.alias(
        name = "lib{}.jar".format(name),
        actual = ":{}_internal_kt.jar".format(name),
    )

    native.alias(
        name = "lib{}-src.jar".format(name),
        actual = ":{}_internal_kt-sources.jar".format(name),
    )

def bazel_kt_jvm_library(name, kwargs):
    """A macro that wraps Bazel's kt_jvm_library.

    This macro wraps Bazel's kt_jvm_library to output the jars files
    in the expected locations (https://github.com/bazelbuild/rules_kotlin/issues/324).

    Args:
      name: the name of the library.
      kwargs: Additional arguments of the library.
    """

    kt_jvm_library(
        name = name,
        **kwargs
    )

    native.alias(
        name = "lib{}-src.jar".format(name),
        actual = ":{}-sources.jar".format(name),
    )
