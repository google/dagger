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
"""Macros for building compiler tests."""

load("@rules_java//java:defs.bzl", "java_binary", "java_test")
load("@io_bazel_rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

def compiler_test(name, size = "large", compiler_deps = None, **kwargs):
    """Generates a java_test that tests java compilation with the given compiler deps.

    This macro separates the compiler dependencies from the test dependencies to avoid
    1-version violations. For example, this often happens when the java_test uses java
    dependencies but the compiler test expects the android version of the dependencies.

    Args:
      name: The name of the java_test.
      size: The size of the test (default "large" since this test does disk I/O).
      compiler_deps: The deps needed during compilation.
      **kwargs: The parameters to pass to the generated java_test.

    Returns:
      None
    """

    # This JAR is loaded at runtime and contains the dependencies used by the compiler during tests.
    # We separate these dependencies from the java_test dependencies to avoid 1 version violations.
    java_binary(
        name = name + "_compiler_deps",
        testonly = 1,
        tags = ["notap"],
        visibility = ["//visibility:private"],
        main_class = "Object.class",
        runtime_deps = compiler_deps,
    )

    # Add the compiler deps jar, generated above, to the test's data.
    kwargs["data"] = kwargs.get("data", []) + [name + "_compiler_deps_deploy.jar"]

    # Need to check for srcs since for Kotlin tests we use a runtime dep on the kt_jvm_library
    # target. We don't need to worry about adding a compile testing dep since kt_compiler_test
    # adds that in the kt_jvm_library. Adding this dep automatically is merely a convenience
    # for cases with srcs anyway.
    if kwargs.get("srcs", None):
        # Add a dep to allow usage of CompilerTests.
        kwargs["deps"] = kwargs.get("deps", []) + ["//java/dagger/testing/compile"]
    java_test(name = name, size = size, **kwargs)

def kt_compiler_test(name, srcs = [], deps = [], **kwargs):
    """Generates a java_test that tests java compilation with the given compiler deps.

    This macro works the same as the above compiler_test, but for Kotlin sources.

    Args:
      name: The name of the java_test.
      srcs: Source files for the test (typically should include Kotlin sources). If no
            sources are needed, just use compiler_test with runtime_deps.
      deps: Deps for compiling the files in srcs.
      **kwargs: The parameters to pass to compiler_test

    Returns:
      None
    """
    kt_jvm_library(
        name = name + "_ktlib",
        testonly = 1,
        srcs = srcs,
        deps = deps + ["//java/dagger/testing/compile"],
        visibility = ["//visibility:private"],
    )

    compiler_test(
        name = name,
        runtime_deps = [
            ":" + name + "_ktlib",
        ],
        **kwargs
    )
