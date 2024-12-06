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
#   Functional tests for Dagger

load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX")
load("//:test_defs.bzl", "GenJavaTests")

package(default_visibility = ["//:src"])

GenJavaTests(
    name = "AssistedFactoryAsQualifiedBindingTest",
    srcs = ["AssistedFactoryAsQualifiedBindingTest.java"],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    test_only_deps = [
        "//third_party/java/guava/collect",  # TODO(b/): This shouldn't be needed.
        "//:dagger_with_compiler",
        "//third_party/java/truth",
        "//third_party/java/junit",
    ],
)

GenJavaTests(
    name = "AssistedFactoryBindsTest",
    srcs = ["AssistedFactoryBindsTest.java"],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    test_only_deps = [
        "//:dagger_with_compiler",
        "//third_party/java/truth",
        "//third_party/java/junit",
    ],
)

GenJavaTests(
    name = "AssistedFactoryDuplicatedParamNamesTest",
    srcs = ["AssistedFactoryDuplicatedParamNamesTest.java"],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    test_only_deps = [
        "//:dagger_with_compiler",
        "//third_party/java/truth",
        "//third_party/java/junit",
    ],
)

GenJavaTests(
    name = "AssistedFactoryInaccessibleTest",
    srcs = ["AssistedFactoryInaccessibleTest.java"],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    test_only_deps = [
        "//:dagger_with_compiler",
        "//third_party/java/truth",
        "//third_party/java/junit",
        "//javatests/dagger/functional/assisted/subpackage",
    ],
)

GenJavaTests(
    name = "AssistedFactoryParameterizedTest",
    srcs = ["AssistedFactoryParameterizedTest.java"],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    test_only_deps = [
        "//:dagger_with_compiler",
        "//third_party/java/truth",
        "//third_party/java/junit",
    ],
)

GenJavaTests(
    name = "AssistedFactoryTest",
    srcs = ["AssistedFactoryTest.java"],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    test_only_deps = [
        "//:dagger_with_compiler",
        "//third_party/java/truth",
        "//third_party/java/junit",
    ],
)

GenJavaTests(
    name = "AssistedFactoryWithArrayTypesTest",
    srcs = ["AssistedFactoryWithArrayTypesTest.java"],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    test_only_deps = [
        "//:dagger_with_compiler",
        "//third_party/java/truth",
        "//third_party/java/junit",
    ],
)

GenJavaTests(
    name = "AssistedFactoryWithAssistedInjectParamTest",
    srcs = ["AssistedFactoryWithAssistedInjectParamTest.java"],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    test_only_deps = [
        "//:dagger_with_compiler",
        "//third_party/java/truth",
        "//third_party/java/junit",
    ],
)

GenJavaTests(
    name = "AssistedFactoryWithMultibindingsTest",
    srcs = ["AssistedFactoryWithMultibindingsTest.java"],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    test_only_deps = [
        "//third_party/java/guava/collect",  # TODO(b/): This shouldn't be needed.
        "//:dagger_with_compiler",
        "//third_party/java/truth",
        "//third_party/java/junit",
    ],
)

GenJavaTests(
    name = "AssistedFactoryWithQualifiedTypesTest",
    srcs = ["AssistedFactoryWithQualifiedTypesTest.java"],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    test_only_deps = [
        "//:dagger_with_compiler",
        "//third_party/java/truth",
        "//third_party/java/junit",
    ],
)
