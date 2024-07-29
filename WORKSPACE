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
# Load Android Sdk
#############################

android_sdk_repository(
    name = "androidsdk",
    api_level = 32,
    build_tools_version = "32.0.0",
)

#############################
# Load Kotlin repository
#############################

load("@io_bazel_rules_kotlin//kotlin:repositories.bzl", "kotlin_repositories", "kotlinc_version")

KOTLIN_VERSION = "1.9.23"

# Get from https://github.com/JetBrains/kotlin/releases/
KOTLINC_RELEASE_SHA = "93137d3aab9afa9b27cb06a824c2324195c6b6f6179d8a8653f440f5bd58be88"

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

KSP_VERSION = KOTLIN_VERSION + "-1.0.19"

MAVEN_VERSION = "3.3.3"

# NOTE: This version should match the version declared in MODULE.bazel
ROBOLECTRIC_VERSION = "4.11.1"

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
        "org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.6.2",
        "org.jspecify:jspecify:0.3.0",
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
