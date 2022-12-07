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
load("//:test_defs.bzl", "GenJavaTests")

package(default_visibility = ["//:src"])

GenJavaTests(
    name = "binds",
    # TODO(bcorso): Move "/binds/subpackage" sources into their own build file so that
    # NotExposed.kt is actaully not exposed. Currently, since kotlin does not support
    # package visibility, we're using internal visibility which means NotExposed.kt is
    # still exposed to classes in this package since they're in the same build unit.
    # The difficulty here is that GenJavaTests creates various variants of each
    # library and test, so e.g. FooTest_FASTINIT would need to depend on the proper
    # variant, like /binds/subpackage:subpackage_FASTINIT library, so we'll likely need
    # to modify/add to our GenJavaTests infrastructure to make this work.
    srcs = glob(["**/*.kt"]),
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    deps = [
        "//:dagger_with_compiler",
        "//third_party/java/auto:factory",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/util/concurrent",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/jsr330_inject",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)
