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

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

#############################
# Upgrade java_tools version
#############################

# These targets added per instructions at
# https://github.com/bazelbuild/java_tools/releases/tag/javac11_v10.7
http_archive(
    name = "remote_java_tools_linux",
    sha256 = "cf57fc238ed5c24c718436ab4178ade5eb838fe56e7c32c4fafe0b6fbdaec51f",
    urls = [
        "https://mirror.bazel.build/bazel_java_tools/releases/javac11/v10.7/java_tools_javac11_linux-v10.7.zip",
        "https://github.com/bazelbuild/java_tools/releases/download/javac11_v10.7/java_tools_javac11_linux-v10.7.zip",
    ],
)

http_archive(
    name = "remote_java_tools_windows",
    sha256 = "a0fc3a3be3ea01a4858d12f56892dd663c02f218104e8c1dc9f3e90d5e583bcb",
    urls = [
        "https://mirror.bazel.build/bazel_java_tools/releases/javac11/v10.7/java_tools_javac11_windows-v10.7.zip",
        "https://github.com/bazelbuild/java_tools/releases/download/javac11_v10.7/java_tools_javac11_windows-v10.7.zip",
    ],
)

http_archive(
    name = "remote_java_tools_darwin",
    sha256 = "51a4cf424d3b26d6c42703cf2d80002f1489ba0d28c939519c3bb9c3d6ee3720",
    urls = [
        "https://mirror.bazel.build/bazel_java_tools/releases/javac11/v10.7/java_tools_javac11_darwin-v10.7.zip",
        "https://github.com/bazelbuild/java_tools/releases/download/javac11_v10.7/java_tools_javac11_darwin-v10.7.zip",
    ],
)

#############################
# Load nested repository
#############################

# Declare the nested workspace so that the top-level workspace doesn't try to
# traverse it when calling `bazel build //...`
local_repository(
    name = "examples_bazel",
    path = "examples/bazel",
)

#############################
# Load Bazel-Common repository
#############################

http_archive(
    name = "google_bazel_common",
    sha256 = "8b6aebdc095c8448b2f6a72bb8eae4a563891467e2d20c943f21940b1c444e38",
    strip_prefix = "bazel-common-3d0e5005cfcbee836e31695d4ab91b5328ccc506",
    urls = ["https://github.com/google/bazel-common/archive/3d0e5005cfcbee836e31695d4ab91b5328ccc506.zip"],
)

load("@google_bazel_common//:workspace_defs.bzl", "google_common_workspace_rules")

google_common_workspace_rules()

#############################
# Load Protobuf dependencies
#############################

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

#############################
# Load Robolectric repository
#############################

ROBOLECTRIC_VERSION = "4.4"

http_archive(
    name = "robolectric",
    sha256 = "d4f2eb078a51f4e534ebf5e18b6cd4646d05eae9b362ac40b93831bdf46112c7",
    strip_prefix = "robolectric-bazel-%s" % ROBOLECTRIC_VERSION,
    urls = ["https://github.com/robolectric/robolectric-bazel/archive/%s.tar.gz" % ROBOLECTRIC_VERSION],
)

load("@robolectric//bazel:robolectric.bzl", "robolectric_repositories")

robolectric_repositories()

#############################
# Load Kotlin repository
#############################

RULES_KOTLIN_COMMIT = "686f0f1cf3e1cc8c750688bb082316b3eadb3cb6"

RULES_KOTLIN_SHA = "1d8758bbf27400a5f9d40f01e4337f6834d2b7864df34e9aa5cf0a9ab6cc9241"

http_archive(
    name = "io_bazel_rules_kotlin_head",
    sha256 = RULES_KOTLIN_SHA,
    strip_prefix = "rules_kotlin-%s" % RULES_KOTLIN_COMMIT,
    type = "zip",
    urls = ["https://github.com/bazelbuild/rules_kotlin/archive/%s.zip" % RULES_KOTLIN_COMMIT],
)

load("@io_bazel_rules_kotlin_head//src/main/starlark/release_archive:repository.bzl", "archive_repository")

archive_repository(
    name = "io_bazel_rules_kotlin",
    source_repository_name = "io_bazel_rules_kotlin_head",
)

load("@io_bazel_rules_kotlin//kotlin:repositories.bzl", "kotlin_repositories", "kotlinc_version")

KOTLIN_VERSION = "1.5.32"

KOTLINC_RELEASE_SHA = "2e728c43ee0bf819eae06630a4cbbc28ba2ed5b19a55ee0af96d2c0ab6b6c2a5"

kotlin_repositories(
    compiler_release = kotlinc_version(
        release = KOTLIN_VERSION,
        sha256 = KOTLINC_RELEASE_SHA,
    ),
)

load("@io_bazel_rules_kotlin//kotlin:core.bzl", "kt_register_toolchains")

kt_register_toolchains()

#############################
# Load Maven dependencies
#############################

RULES_JVM_EXTERNAL_TAG = "2.7"

RULES_JVM_EXTERNAL_SHA = "f04b1466a00a2845106801e0c5cec96841f49ea4e7d1df88dc8e4bf31523df74"

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@rules_jvm_external//:defs.bzl", "maven_install")

ANDROID_LINT_VERSION = "26.6.2"

AUTO_COMMON_VERSION = "1.2.1"

# NOTE(bcorso): Even though we set the version here, our Guava version in
#  processor code will use whatever version is built into JavaBuilder, which is
#  tied to the version of Bazel we're using.
GUAVA_VERSION = "27.1"

GRPC_VERSION = "1.2.0"

INCAP_VERSION = "0.2"

BYTE_BUDDY_VERSION = "1.9.10"

CHECKER_FRAMEWORK_VERSION = "2.5.3"

ERROR_PRONE_VERSION = "2.3.2"

maven_install(
    artifacts = [
        "androidx.annotation:annotation:1.1.0",
        "androidx.appcompat:appcompat:1.3.1",
        "androidx.activity:activity:1.3.1",
        "androidx.fragment:fragment:1.3.6",
        "androidx.lifecycle:lifecycle-common:2.3.1",
        "androidx.lifecycle:lifecycle-viewmodel:2.3.1",
        "androidx.lifecycle:lifecycle-viewmodel-savedstate:2.3.1",
        "androidx.multidex:multidex:2.0.1",
        "androidx.savedstate:savedstate:1.0.0",
        "androidx.test:monitor:1.4.0",
        "androidx.test:core:1.4.0",
        "androidx.test.ext:junit:1.1.3",
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
        "com.google.auto:auto-common:%s" % AUTO_COMMON_VERSION,
        "com.google.auto.factory:auto-factory:1.0",
        "com.google.auto.service:auto-service:1.0",
        "com.google.auto.service:auto-service-annotations:1.0",
        "com.google.auto.value:auto-value:1.6",
        "com.google.auto.value:auto-value-annotations:1.6",
        "com.google.code.findbugs:jsr305:3.0.1",
        "com.google.devtools.ksp:symbol-processing-api:1.5.30-1.0.0",
        "com.google.errorprone:error_prone_annotation:%s" % ERROR_PRONE_VERSION,
        "com.google.errorprone:error_prone_annotations:%s" % ERROR_PRONE_VERSION,
        "com.google.errorprone:error_prone_check_api:%s" % ERROR_PRONE_VERSION,
        "com.google.googlejavaformat:google-java-format:1.5",
        "com.google.guava:guava:%s-jre" % GUAVA_VERSION,
        "com.google.guava:guava-testlib:%s-jre" % GUAVA_VERSION,
        "com.google.guava:failureaccess:1.0.1",
        "com.google.guava:guava-beta-checker:1.0",
        "com.google.protobuf:protobuf-java:3.7.0",
        "com.google.testing.compile:compile-testing:0.18",
        "com.google.truth:truth:1.1",
        "com.squareup:javapoet:1.13.0",
        "io.grpc:grpc-context:%s" % GRPC_VERSION,
        "io.grpc:grpc-core:%s" % GRPC_VERSION,
        "io.grpc:grpc-netty:%s" % GRPC_VERSION,
        "io.grpc:grpc-protobuf:%s" % GRPC_VERSION,
        "javax.annotation:jsr250-api:1.0",
        "javax.inject:javax.inject:1",
        "javax.inject:javax.inject-tck:1",
        "junit:junit:4.13",
        "net.bytebuddy:byte-buddy:%s" % BYTE_BUDDY_VERSION,
        "net.bytebuddy:byte-buddy-agent:%s" % BYTE_BUDDY_VERSION,
        "net.ltgt.gradle.incap:incap:%s" % INCAP_VERSION,
        "net.ltgt.gradle.incap:incap-processor:%s" % INCAP_VERSION,
        "org.checkerframework:checker-compat-qual:%s" % CHECKER_FRAMEWORK_VERSION,
        "org.checkerframework:dataflow:%s" % CHECKER_FRAMEWORK_VERSION,
        "org.checkerframework:javacutil:%s" % CHECKER_FRAMEWORK_VERSION,
        "org.hamcrest:hamcrest-core:1.3",
        "org.jetbrains.kotlin:kotlin-stdlib:%s" % KOTLIN_VERSION,
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:%s" % KOTLIN_VERSION,
        "org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.3.0",
        "org.mockito:mockito-core:2.28.2",
        "org.objenesis:objenesis:1.0",
        "org.robolectric:robolectric:4.4",
        "org.robolectric:shadows-framework:4.4",  # For ActivityController
    ],
    repositories = [
        "https://repo1.maven.org/maven2",
        "https://maven.google.com",
    ],
)

#############################
# Load Bazel Skylib rules
#############################

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
