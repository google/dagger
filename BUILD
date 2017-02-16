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

package(default_visibility = ["//visibility:public"])

package_group(
    name = "src",
    packages = ["//..."],
)

py_test(
    name = "maven_sha1_test",
    srcs = ["maven_sha1_test.py"],
    data = ["WORKSPACE"],
)

java_library(
    name = "dagger_with_compiler",
    exported_plugins = ["//compiler:component-codegen"],
    exports = ["//core"],
)

java_library(
    name = "producers_with_compiler",
    exports = [
        ":dagger_with_compiler",
        "//producers",
    ],
)

load("//tools:jarjar.bzl", "jarjar_library")

genrule(
    name = "rules_file",
    outs = ["rules_file.txt"],
    cmd = "echo \"rule com.google.auto.common.** dagger.shaded.auto.common.@1\" > $@",
)

jarjar_library(
    name = "shaded_compiler",
    rules_file = ":rules_file.txt",
    deps = [
        "//compiler",
        "@com_google_auto_auto_common//jar",
    ],
)

load("//tools:javadoc.bzl", "javadoc_library")

# coalesced javadocs used for the gh-pages site
javadoc_library(
    name = "user-docs",
    srcs = [
        "//core/src/main/java/dagger:javadoc-srcs",
        "//java/dagger/android:android-srcs",
        "//java/dagger/android/support:support-srcs",
        "//producers:producers-srcs",
    ],
    android_api_level = 25,
    # TODO(ronshapiro): figure out how to specify the version number for release builds
    doctitle = "Dagger Dependency Injection API",
    exclude_packages = [
        "dagger.internal",
        "dagger.producers.internal",
        "dagger.producers.monitoring.internal",
    ],
    root_packages = ["dagger"],
    deps = [
        "//core/src/main/java/dagger:core",
        "//java/dagger/android",
        "//producers",
    ],
)
