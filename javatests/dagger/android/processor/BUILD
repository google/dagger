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
#
# Description:
#   Tests for Dagger's Android integrations

package(default_visibility = ["//:src"])

load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX")
load("//:test_defs.bzl", "GenJavaTests")

GenJavaTests(
    name = "android_processor_tests",
    srcs = glob(["*.java"]),
    functional = False,
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    deps = [
        "@google_bazel_common//third_party/java/guava",
        "@androidsdk//com.android.support:support-fragment-25.0.0",
        # TODO(ronshapiro): create a common location to define the current Android version
        "@androidsdk//:platforms/android-26/android.jar",
        "@google_bazel_common//third_party/java/compile_testing",
        "//:dagger_with_compiler",
        "@google_bazel_common//third_party/java/junit",
        "@google_bazel_common//third_party/java/truth",
        "@google_bazel_common//third_party/java/truth:truth8",
        "//java/dagger/android",
        "//java/dagger/android/processor",
        "//java/dagger/internal/codegen:processor",
    ],
)
