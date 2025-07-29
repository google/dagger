# Copyright (C) 2022 The Dagger Authors.
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
#   Functional tests for Dagger @Binds usages

load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
)
load("//:test_defs.bzl", "GenKtTests")

GenKtTests(
    name = "BindsTest",
    srcs = [
        "AccessesExposedComponent.kt",
        "BindsTest.kt",
        "Foo.kt",
        "FooOfObjects.kt",
        "FooOfStrings.kt",
        "InterfaceModule.kt",
        "NeedsFactory.kt",
        "SimpleBindingModule.kt",
        "SomeQualifier.kt",
        "TestComponent.kt",
    ],
    gen_library_deps = [
        "//javatests/dagger/functional/kotlinsrc/binds/subpackage",
    ],
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    deps = [
        "//third_party/java/auto:factory",
        "//third_party/java/dagger",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

GenKtTests(
    name = "BindsCollectionsWithoutMultibindingsTest",
    srcs = ["BindsCollectionsWithoutMultibindingsTest.kt"],
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    deps = [
        "//third_party/java/dagger",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

GenKtTests(
    name = "ScopedBindsTest",
    srcs = ["ScopedBindsTest.kt"],
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    deps = [
        "//third_party/java/dagger",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)
