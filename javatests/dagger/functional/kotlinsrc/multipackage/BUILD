# Copyright (C) 2023 The Dagger Authors.
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
#   Functional tests for Dagger multipackage usages.

load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
)
load("//:test_defs.bzl", "GenKtTests")

# Looks like Dagger's strict deps exemption isn't working properly in Bazel
# so we need to expose transitive deps?
TRANSITIVE_DEPS = [
    "//javatests/dagger/functional/kotlinsrc/multipackage/b",
    "//javatests/dagger/functional/kotlinsrc/multipackage/c",
    "//javatests/dagger/functional/kotlinsrc/multipackage/d",
    "//javatests/dagger/functional/kotlinsrc/multipackage/foo",
    "//javatests/dagger/functional/kotlinsrc/multipackage/grandsub",
]

GenKtTests(
    name = "multipackage",
    srcs = glob(["*.kt"]),
    gen_library_deps = TRANSITIVE_DEPS + [
        "//javatests/dagger/functional/kotlinsrc/multipackage/a",
        "//javatests/dagger/functional/kotlinsrc/multipackage/moduleconstructor",
        "//javatests/dagger/functional/kotlinsrc/multipackage/primitives",
        "//javatests/dagger/functional/kotlinsrc/multipackage/sub",
    ],
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    deps = [
        "//third_party/java/dagger",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)
