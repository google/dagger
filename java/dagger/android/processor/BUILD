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

# https://github.com/bazelbuild/bazel/issues/2517
java_import(
    name = "dagger_android_import_hack",
    jars = [
        "//java/dagger/android:libandroid.jar",
        "//java/dagger/android/support:libsupport.jar",
    ],
)

java_library(
    name = "processor",
    srcs = glob(["*.java"]),
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    deps = [
        ":dagger_android_import_hack",
        "//core",
        "//third_party:auto_common",
        "//third_party:auto_service",
        "//third_party:auto_value",
        "//third_party:guava",
    ],
)

java_plugin(
    name = "plugin",
    processor_class = "dagger.android.processor.AndroidProcessor",
    deps = [":processor"],
)
