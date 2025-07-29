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
#   Tests for internal code for implementing Hilt processors.

load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX")
load("//:test_defs.bzl", "GenKtLibrary", "GenKtTests")

GenKtTests(
    name = "KotlinAssistedInjectionTest",
    srcs = ["KotlinAssistedInjectionTest.kt"],
    gen_library_deps = [
        ":KotlinAssistedInjectionClasses",
    ],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    deps = [
        "//third_party/java/auto:factory",
        "//third_party/java/dagger",
        "//third_party/java/junit",
        "//third_party/java/truth",
    ],
)

GenKtLibrary(
    name = "KotlinAssistedInjectionClasses",
    srcs = ["KotlinAssistedInjectionClasses.kt"],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    deps = [
        "//third_party/java/auto:factory",
        "//third_party/java/dagger",
    ],
)
