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

# Declare the nested workspace so that the top-level workspace doesn't try to
# traverse it when calling `bazel build //...`
local_repository(
    name = "examples_bazel",
    path = "examples/bazel",
)

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "google_bazel_common",
    sha256 = "7e5584a1527390d55c972c246471cffd4c68b4c234d288f6afb52af8619c4560",
    strip_prefix = "bazel-common-d58641d120c2ad3d0afd77b57fbaa78f3a97d914",
    urls = ["https://github.com/google/bazel-common/archive/d58641d120c2ad3d0afd77b57fbaa78f3a97d914.zip"],
)

load("@google_bazel_common//:workspace_defs.bzl", "google_common_workspace_rules")

google_common_workspace_rules()

RULES_JVM_EXTERNAL_TAG = "2.7"

RULES_JVM_EXTERNAL_SHA = "f04b1466a00a2845106801e0c5cec96841f49ea4e7d1df88dc8e4bf31523df74"

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

# rules_python and zlib are required by protobuf.
# TODO(ronshapiro): Figure out if zlib is in fact necessary, or if proto can depend on the
# @bazel_tools library directly. See discussion in
# https://github.com/protocolbuffers/protobuf/pull/5389#issuecomment-481785716
# TODO(cpovirk): Should we eventually get rules_python from "Bazel Federation?"
# https://github.com/bazelbuild/rules_python#getting-started

http_archive(
    name = "rules_python",
    sha256 = "e5470e92a18aa51830db99a4d9c492cc613761d5bdb7131c04bd92b9834380f6",
    strip_prefix = "rules_python-4b84ad270387a7c439ebdccfd530e2339601ef27",
    urls = ["https://github.com/bazelbuild/rules_python/archive/4b84ad270387a7c439ebdccfd530e2339601ef27.tar.gz"],
)

http_archive(
    name = "zlib",
    build_file = "@com_google_protobuf//:third_party/zlib.BUILD",
    sha256 = "629380c90a77b964d896ed37163f5c3a34f6e6d897311f1df2a7016355c45eff",
    strip_prefix = "zlib-1.2.11",
    urls = ["https://github.com/madler/zlib/archive/v1.2.11.tar.gz"],
)

load("@rules_jvm_external//:defs.bzl", "maven_install")

ANDROID_LINT_VERSION = "26.6.2"

maven_install(
    artifacts = [
        "androidx.annotation:annotation:1.1.0",
        "androidx.appcompat:appcompat:1.2.0",
        "androidx.activity:activity:1.1.0",
        "androidx.fragment:fragment:1.2.5",
        "androidx.lifecycle:lifecycle-viewmodel:2.2.0",
        "androidx.lifecycle:lifecycle-viewmodel-savedstate:2.2.0",
        "androidx.multidex:multidex:2.0.1",
        "androidx.savedstate:savedstate:1.0.0",
        "androidx.test:monitor:1.1.1",
        "androidx.test:core:1.1.0",
        "com.google.auto:auto-common:0.11",
        "com.android.support:appcompat-v7:25.0.0",
        "com.android.support:support-annotations:25.0.0",
        "com.android.support:support-fragment:25.0.0",
        "com.android.tools.external.org-jetbrains:uast:%s" % ANDROID_LINT_VERSION,
        "com.android.tools.external.com-intellij:intellij-core:%s" % ANDROID_LINT_VERSION,
        "com.android.tools.external.com-intellij:kotlin-compiler:%s" % ANDROID_LINT_VERSION,
        "com.android.tools.lint:lint:%s" % ANDROID_LINT_VERSION,
        "com.android.tools.lint:lint-api:%s" % ANDROID_LINT_VERSION,
        "com.android.tools.lint:lint-checks:%s" % ANDROID_LINT_VERSION,
        "com.android.tools.lint:lint-tests:%s" % ANDROID_LINT_VERSION,
        "com.android.tools:testutils:%s" % ANDROID_LINT_VERSION,
        "com.github.tschuchortdev:kotlin-compile-testing:1.2.8",
        "com.google.guava:guava:27.1-android",
        "org.jetbrains.kotlin:kotlin-stdlib:1.3.50",
        "org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.1.0",
        "org.robolectric:robolectric:4.3.1",
    ],
    repositories = [
        "https://repo1.maven.org/maven2",
        "https://maven.google.com",
        "https://jcenter.bintray.com/",  # Lint has one trove4j dependency in jCenter
    ],
)

RULES_KOTLIN_VERSION = "legacy-1.4.0-rc3"

RULES_KOTLIN_SHA = "da0e6e1543fcc79e93d4d93c3333378f3bd5d29e82c1bc2518de0dbe048e6598"

http_archive(
    name = "io_bazel_rules_kotlin",
    sha256 = RULES_KOTLIN_SHA,
    urls = ["https://github.com/bazelbuild/rules_kotlin/releases/download/%s/rules_kotlin_release.tgz" % RULES_KOTLIN_VERSION],
)

load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kotlin_repositories", "kt_register_toolchains")

kotlin_repositories()

kt_register_toolchains()

BAZEL_SKYLIB_VERSION = "1.0.2"

BAZEL_SKYLIB_SHA = "97e70364e9249702246c0e9444bccdc4b847bed1eb03c5a3ece4f83dfe6abc44"

http_archive(
    name = "bazel_skylib",
    sha256 = BAZEL_SKYLIB_SHA,
    urls = [
        "https://github.com/bazelbuild/bazel-skylib/releases/download/{version}/bazel-skylib-{version}.tar.gz".format(version = BAZEL_SKYLIB_VERSION),
    ],
)

load("@bazel_skylib//:workspace.bzl", "bazel_skylib_workspace")

bazel_skylib_workspace()
