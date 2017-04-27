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

android_sdk_repository(
    name = "androidsdk",
    api_level = 25,
    build_tools_version = "25.0.2",
)

bind(
    name = "android_sdk_for_testing",
    actual = "@androidsdk//:files",
)

maven_jar(
    name = "javax_annotation_jsr250_api",
    artifact = "javax.annotation:jsr250-api:1.0",
    sha1 = "5025422767732a1ab45d93abfea846513d742dcf",
)

maven_jar(
    name = "com_google_code_findbugs_jsr305",
    artifact = "com.google.code.findbugs:jsr305:3.0.1",
    sha1 = "f7be08ec23c21485b9b5a1cf1654c2ec8c58168d",
)

maven_jar(
    name = "javax_inject_javax_inject",
    artifact = "javax.inject:javax.inject:1",
    sha1 = "6975da39a7040257bd51d21a231b76c915872d38",
)

maven_jar(
    name = "javax_inject_javax_inject_tck",
    artifact = "javax.inject:javax.inject-tck:1",
    sha1 = "bb0090d50219c265be40fcc8e034dae37fa7be99",
)

maven_jar(
    name = "com_google_guava_guava",
    artifact = "com.google.guava:guava:21.0",
    sha1 = "3a3d111be1be1b745edfa7d91678a12d7ed38709",
)

maven_jar(
    name = "com_google_guava_guava_testlib",
    artifact = "com.google.guava:guava-testlib:21.0-rc1",
    sha1 = "13f0f0dce4e710bb0bb791bd07f6e9858670a865",
)

maven_jar(
    name = "com_google_errorprone_javac",
    artifact = "com.google.errorprone:javac:9-dev-r3297-1-shaded",
    sha1 = "0f6d4998965282068a3feecddc21578d23f17275",
)

maven_jar(
    name = "com_google_googlejavaformat_google_java_format",
    artifact = "com.google.googlejavaformat:google-java-format:1.3",
    sha1 = "949e85e75b3160ce1446aa99d806d5b509631b02",
)

maven_jar(
    name = "com_google_auto_auto_common",
    artifact = "com.google.auto:auto-common:0.8",
    sha1 = "c6f7af0e57b9d69d81b05434ef9f3c5610d498c4",
)

maven_jar(
    name = "com_google_auto_factory_auto_factory",
    artifact = "com.google.auto.factory:auto-factory:1.0-beta3",
    sha1 = "99b2ffe0e41abbd4cc42bf3836276e7174c4929d",
)

maven_jar(
    name = "com_squareup_javawriter",
    artifact = "com.squareup:javawriter:2.5.1",
    sha1 = "54c87b3d91238e5b58e1a436d4916eee680ec959",
)

maven_jar(
    name = "com_google_auto_service_auto_service",
    artifact = "com.google.auto.service:auto-service:1.0-rc2",
    sha1 = "51033a5b8fcf7039159e35b6878f106ccd5fb35f",
)

maven_jar(
    name = "com_google_auto_value_auto_value",
    artifact = "com.google.auto.value:auto-value:1.4-rc1",
    sha1 = "9347939002003a7a3c3af48271fc2c18734528a4",
)

maven_jar(
    name = "com_google_errorprone_error_prone_annotations",
    artifact = "com.google.errorprone:error_prone_annotations:2.0.12",
    sha1 = "8530d22d4ae8419e799d5a5234e0d2c0dcf15d4b",
)

maven_jar(
    name = "junit_junit",
    artifact = "junit:junit:4.11",
    sha1 = "4e031bb61df09069aeb2bffb4019e7a5034a4ee0",
)

maven_jar(
    name = "com_google_testing_compile_compile_testing",
    artifact = "com.google.testing.compile:compile-testing:0.10",
    sha1 = "51e6189be9d2861d1eb22b4009c8f3430319490c",
)

maven_jar(
    name = "org_mockito_mockito_core",
    artifact = "org.mockito:mockito-core:1.9.5",
    sha1 = "c3264abeea62c4d2f367e21484fbb40c7e256393",
)

maven_jar(
    name = "org_hamcrest_hamcrest_core",
    artifact = "org.hamcrest:hamcrest-core:1.3",
    sha1 = "42a25dc3219429f0e5d060061f71acb49bf010a0",
)

maven_jar(
    name = "org_objenesis_objenesis",
    artifact = "org.objenesis:objenesis:1.0",
    sha1 = "9b473564e792c2bdf1449da1f0b1b5bff9805704",
)

maven_jar(
    name = "com_google_truth_truth",
    artifact = "com.google.truth:truth:0.30",
    sha1 = "9d591b5a66eda81f0b88cf1c748ab8853d99b18b",
)

maven_jar(
    name = "com_google_truth_extensions_truth_java8_extension",
    artifact = "com.google.truth.extensions:truth-java8-extension:0.30",
    sha1 = "f3bb5e49001a9b575bcdef9aa8417b6d1ef35509",
)

maven_jar(
    name = "com_squareup_javapoet",
    artifact = "com.squareup:javapoet:1.7.0",
    sha1 = "4fdcf1fc27c1a8f55d1109df986c923152f07759",
)

maven_jar(
    name = "io_grpc_grpc_core",
    artifact = "io.grpc:grpc-core:1.2.0",
    sha1 = "f12a213e2b59a0615df2cc9bed35dc15fd2fee37",
)

maven_jar(
    name = "io_grpc_grpc_netty",
    artifact = "io.grpc:grpc-netty:1.2.0",
    sha1 = "e2682d2dc052898f87433e7a6d03d104ef98df74",
)

maven_jar(
    name = "io_grpc_grpc_context",
    artifact = "io.grpc:grpc-context:1.2.0",
    sha1 = "1932db544cbb427bc18f902c7ebbb3f7e44991df",
)

maven_jar(
    name = "io_grpc_grpc_protobuf",
    artifact = "io.grpc:grpc-protobuf:1.2.0",
    sha1 = "2676852d2dbd20155d9b1a940a456eae5b7445f0",
)

maven_jar(
    name = "io_grpc_grpc_stub",
    artifact = "io.grpc:grpc-stub:1.2.0",
    sha1 = "964dda53b3085bfd17c7aaf51495f9efc8bda36c",
)

maven_jar(
    name = "io_grpc_grpc_all",
    artifact = "io.grpc:grpc-all:1.2.0",
    sha1 = "f32006a1245dfa2d68bf92a1b4cc01831889c95b",
)

maven_jar(
    name = "com_google_protobuf_protobuf_java",
    artifact = "com.google.protobuf:protobuf-java:3.2.0",
    sha1 = "62ccf171a106ff6791507f2d5364c275f9a3131d",
)

http_archive(
    name = "com_google_protobuf",
    sha256 = "ff771a662fb6bd4d3cc209bcccedef3e93980a49f71df1e987f6afa3bcdcba3a",
    strip_prefix = "protobuf-b4b0e304be5a68de3d0ee1af9b286f958750f5e4",
    urls = ["https://github.com/google/protobuf/archive/b4b0e304be5a68de3d0ee1af9b286f958750f5e4.zip"],
)

http_archive(
    name = "com_google_protobuf_java",
    sha256 = "ff771a662fb6bd4d3cc209bcccedef3e93980a49f71df1e987f6afa3bcdcba3a",
    strip_prefix = "protobuf-b4b0e304be5a68de3d0ee1af9b286f958750f5e4",
    urls = ["https://github.com/google/protobuf/archive/b4b0e304be5a68de3d0ee1af9b286f958750f5e4.zip"],
)

load("//tools:jarjar.bzl", "jarjar_deps")

jarjar_deps()
