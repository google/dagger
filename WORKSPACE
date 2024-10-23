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
# Load nested repository
#############################

# Declare the nested workspace so that the top-level workspace doesn't try to
# traverse it when calling `bazel build //...`
local_repository(
    name = "examples_bazel",
    path = "examples/bazel",
)

#############################
# Load Bazel Skylib rules
#############################

BAZEL_SKYLIB_VERSION = "1.5.0"

BAZEL_SKYLIB_SHA = "cd55a062e763b9349921f0f5db8c3933288dc8ba4f76dd9416aac68acee3cb94"

http_archive(
    name = "bazel_skylib",
    sha256 = BAZEL_SKYLIB_SHA,
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-skylib/releases/download/%s/bazel-skylib-%s.tar.gz" % (BAZEL_SKYLIB_VERSION, BAZEL_SKYLIB_VERSION),
        "https://github.com/bazelbuild/bazel-skylib/releases/download/%s/bazel-skylib-%s.tar.gz" % (BAZEL_SKYLIB_VERSION, BAZEL_SKYLIB_VERSION),
    ],
)

load("@bazel_skylib//:workspace.bzl", "bazel_skylib_workspace")

bazel_skylib_workspace()

#############################
# Load rules_java repository
#############################

RULES_JAVA_VERSION = "7.12.1"

http_archive(
    name = "rules_java",
    urls = ["https://github.com/bazelbuild/rules_java/releases/download/%s/rules_java-%s.tar.gz" % (RULES_JAVA_VERSION, RULES_JAVA_VERSION)],
    sha256 = "dfbadbb37a79eb9e1cc1e156ecb8f817edf3899b28bc02410a6c1eb88b1a6862",
)
load("@rules_java//java:repositories.bzl", "rules_java_dependencies", "rules_java_toolchains")
rules_java_dependencies()
rules_java_toolchains()

#############################
# Load Android Sdk
#############################

android_sdk_repository(
    name = "androidsdk",
    api_level = 32,
    build_tools_version = "32.0.0",
)

####################################################
# Load Protobuf repository (needed by bazel-common)
####################################################

http_archive(
    name = "rules_proto",
    # output from `sha256sum` on the downloaded tar.gz file
    sha256 = "66bfdf8782796239d3875d37e7de19b1d94301e8972b3cbd2446b332429b4df1",
    strip_prefix = "rules_proto-4.0.0",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_proto/archive/refs/tags/4.0.0.tar.gz",
        "https://github.com/bazelbuild/rules_proto/archive/refs/tags/4.0.0.tar.gz",
    ],
)

load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies", "rules_proto_toolchains")

rules_proto_dependencies()

rules_proto_toolchains()

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

ROBOLECTRIC_VERSION = "4.11.1"

http_archive(
    name = "robolectric",
    sha256 = "1ea1cfe67848decf959316e80dd69af2bbaa359ae2195efe1366cbdf3e968356",
    strip_prefix = "robolectric-bazel-%s" % ROBOLECTRIC_VERSION,
    urls = ["https://github.com/robolectric/robolectric-bazel/releases/download/%s/robolectric-bazel-%s.tar.gz" % (ROBOLECTRIC_VERSION, ROBOLECTRIC_VERSION)],
)

load("@robolectric//bazel:robolectric.bzl", "robolectric_repositories")

robolectric_repositories()

#############################
# Load Kotlin repository
#############################

RULES_KOTLIN_TAG = "1.9.6"

RULES_KOTLIN_SHA = "3b772976fec7bdcda1d84b9d39b176589424c047eb2175bed09aac630e50af43"

http_archive(
    name = "io_bazel_rules_kotlin",
    sha256 = RULES_KOTLIN_SHA,
    urls = ["https://github.com/bazelbuild/rules_kotlin/releases/download/v%s/rules_kotlin-v%s.tar.gz" % (RULES_KOTLIN_TAG, RULES_KOTLIN_TAG)],
)

load("@io_bazel_rules_kotlin//kotlin:repositories.bzl", "kotlin_repositories", "kotlinc_version")

KOTLIN_VERSION = "1.9.24"

# Get from https://github.com/JetBrains/kotlin/releases/
KOTLINC_RELEASE_SHA = "eb7b68e01029fa67bc8d060ee54c12018f2c60ddc438cf21db14517229aa693b"

kotlin_repositories(
    compiler_release = kotlinc_version(
        release = KOTLIN_VERSION,
        sha256 = KOTLINC_RELEASE_SHA,
    ),
)

register_toolchains("//:kotlin_toolchain")

#############################
# Load Maven dependencies
#############################

RULES_JVM_EXTERNAL_TAG = "4.5"

RULES_JVM_EXTERNAL_SHA = "b17d7388feb9bfa7f2fa09031b32707df529f26c91ab9e5d909eb1676badd9a6"

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@rules_jvm_external//:defs.bzl", "maven_install")

ANDROID_LINT_VERSION = "30.1.0"

ANT_VERSION = "1.9.6"

ASM_VERSION = "9.6"

AUTO_COMMON_VERSION = "1.2.1"

BYTE_BUDDY_VERSION = "1.9.10"

CHECKER_FRAMEWORK_VERSION = "2.5.3"

ECLIPSE_SISU_VERSION = "0.3.0"

ERROR_PRONE_VERSION = "2.14.0"

# NOTE(bcorso): Even though we set the version here, our Guava version in
#  processor code will use whatever version is built into JavaBuilder, which is
#  tied to the version of Bazel we're using.
GUAVA_VERSION = "33.0.0"

GRPC_VERSION = "1.2.0"

INCAP_VERSION = "0.2"

KSP_VERSION = KOTLIN_VERSION + "-1.0.20"

MAVEN_VERSION = "3.3.3"

maven_install(
    artifacts = [
        "androidx.annotation:annotation:1.1.0",
        "androidx.annotation:annotation-experimental:1.3.1",
        "androidx.appcompat:appcompat:1.3.1",
        "androidx.activity:activity:1.5.1",
        "androidx.fragment:fragment:1.5.1",
        "androidx.lifecycle:lifecycle-common:2.5.1",
        "androidx.lifecycle:lifecycle-viewmodel:2.5.1",
        "androidx.lifecycle:lifecycle-viewmodel-savedstate:2.5.1",
        "androidx.multidex:multidex:2.0.1",
        "androidx.navigation:navigation-common:2.5.1",
        "androidx.navigation:navigation-fragment:2.5.1",
        "androidx.navigation:navigation-runtime:2.5.1",
        "androidx.savedstate:savedstate:1.2.0",
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
        "com.google.auto:auto-common:%s" % AUTO_COMMON_VERSION,
        "com.google.auto.factory:auto-factory:1.0",
        "com.google.auto.service:auto-service:1.0",
        "com.google.auto.service:auto-service-annotations:1.0",
        "com.google.auto.value:auto-value:1.9",
        "com.google.auto.value:auto-value-annotations:1.9",
        "com.google.code.findbugs:jsr305:3.0.1",
        "com.google.devtools.ksp:symbol-processing:%s" % KSP_VERSION,
        "com.google.devtools.ksp:symbol-processing-api:%s" % KSP_VERSION,
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
        "com.google.truth:truth:1.4.0",
        "com.squareup:javapoet:1.13.0",
        "com.squareup:kotlinpoet:1.11.0",
        "io.github.java-diff-utils:java-diff-utils:4.11",
        "io.grpc:grpc-context:%s" % GRPC_VERSION,
        "io.grpc:grpc-core:%s" % GRPC_VERSION,
        "io.grpc:grpc-netty:%s" % GRPC_VERSION,
        "io.grpc:grpc-protobuf:%s" % GRPC_VERSION,
        "jakarta.inject:jakarta.inject-api:2.0.1",
        "javax.annotation:javax.annotation-api:1.3.2",
        "javax.enterprise:cdi-api:1.0",
        "javax.inject:javax.inject:1",
        "javax.inject:javax.inject-tck:1",
        "junit:junit:4.13",
        "net.bytebuddy:byte-buddy:%s" % BYTE_BUDDY_VERSION,
        "net.bytebuddy:byte-buddy-agent:%s" % BYTE_BUDDY_VERSION,
        "net.ltgt.gradle.incap:incap:%s" % INCAP_VERSION,
        "net.ltgt.gradle.incap:incap-processor:%s" % INCAP_VERSION,
        "org.apache.ant:ant:%s" % ANT_VERSION,
        "org.apache.ant:ant-launcher:%s" % ANT_VERSION,
        "org.apache.maven:maven-artifact:%s" % MAVEN_VERSION,
        "org.apache.maven:maven-model:%s" % MAVEN_VERSION,
        "org.apache.maven:maven-plugin-api:%s" % MAVEN_VERSION,
        "org.checkerframework:checker-compat-qual:%s" % CHECKER_FRAMEWORK_VERSION,
        "org.checkerframework:dataflow:%s" % CHECKER_FRAMEWORK_VERSION,
        "org.checkerframework:javacutil:%s" % CHECKER_FRAMEWORK_VERSION,
        "org.codehaus.plexus:plexus-utils:3.0.20",
        "org.codehaus.plexus:plexus-classworlds:2.5.2",
        "org.codehaus.plexus:plexus-component-annotations:1.5.5",
        "org.eclipse.sisu:org.eclipse.sisu.plexus:%s" % ECLIPSE_SISU_VERSION,
        "org.eclipse.sisu:org.eclipse.sisu.inject:%s" % ECLIPSE_SISU_VERSION,
        "org.hamcrest:hamcrest-core:1.3",
        "org.jetbrains.kotlin:kotlin-annotation-processing-embeddable:%s" % KOTLIN_VERSION,
        "org.jetbrains.kotlin:kotlin-compiler-embeddable:%s" % KOTLIN_VERSION,
        "org.jetbrains.kotlin:kotlin-daemon-embeddable:%s" % KOTLIN_VERSION,
        "org.jetbrains.kotlin:kotlin-stdlib:%s" % KOTLIN_VERSION,
        "org.jetbrains.kotlin:kotlin-metadata-jvm:2.0.0-Beta5",
        "org.jspecify:jspecify:1.0.0",
        "org.mockito:mockito-core:2.28.2",
        "org.pantsbuild:jarjar:1.7.2",
        "org.objenesis:objenesis:1.0",
        "org.ow2.asm:asm:%s" % ASM_VERSION,
        "org.ow2.asm:asm-tree:%s" % ASM_VERSION,
        "org.ow2.asm:asm-commons:%s" % ASM_VERSION,
        "org.robolectric:robolectric:%s" % ROBOLECTRIC_VERSION,
        "org.robolectric:shadows-framework:%s" % ROBOLECTRIC_VERSION,  # For ActivityController
    ],
    repositories = [
        "https://repo1.maven.org/maven2",
        "https://maven.google.com",
    ],
)
