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

java_library(
    name = "jsr_250",
    exports = ["@javax_annotation_jsr250_api//jar"],
)

java_library(
    name = "jsr305_annotations",
    exports = ["@com_google_code_findbugs_jsr305//jar"],
)

java_library(
    name = "jsr330_inject",
    exports = ["@javax_inject_javax_inject//jar"],
)

java_library(
    name = "jsr330_inject_tck",
    exports = ["@javax_inject_javax_inject_tck//jar"],
)

java_library(
    name = "error_prone_annotations",
    tags = ["maven:compile_only"],
    exports = ["@com_google_errorprone_error_prone_annotations//jar"],
)

java_library(
    name = "junit",
    testonly = 1,
    exports = [
        "@junit_junit//jar",
        "@org_hamcrest_hamcrest_core//jar",
    ],
)

java_library(
    name = "truth",
    testonly = 1,
    exports = ["@com_google_truth_truth//jar"],
)

java_library(
    name = "truth8",
    testonly = 1,
    exports = [
        ":truth",
        "@com_google_truth_extensions_truth_java8_extension//jar",
    ],
)

java_library(
    name = "guava",
    exports = ["@com_google_guava_guava//jar"],
)

java_library(
    name = "guava_testlib",
    testonly = 1,
    exports = ["@com_google_guava_guava_testlib//jar"],
)

java_library(
    name = "auto_common",
    tags = ["maven:shaded"],
    exports = ["@com_google_auto_auto_common//jar"],
)

java_plugin(
    name = "auto_value_processor",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    visibility = ["//visibility:private"],
    deps = [
        ":auto_common",
        ":auto_service",
        "//third_party:guava",
        "@com_google_auto_value_auto_value//jar",
    ],
)

java_plugin(
    name = "auto_annotation_processor",
    processor_class = "com.google.auto.value.processor.AutoAnnotationProcessor",
    visibility = ["//visibility:private"],
    deps = [
        ":auto_common",
        ":auto_service",
        "//third_party:guava",
        "@com_google_auto_value_auto_value//jar",
    ],
)

java_library(
    name = "auto_value",
    exported_plugins = [
        ":auto_value_processor",
        ":auto_annotation_processor",
    ],
    tags = ["maven:compile_only"],
    exports = [
        ":jsr_250",
        "@com_google_auto_value_auto_value//jar",
    ],
)

java_plugin(
    name = "auto_factory_processor",
    generates_api = 1,
    processor_class = "com.google.auto.factory.processor.AutoFactoryProcessor",
    visibility = ["//visibility:private"],
    deps = [
        ":auto_common",
        ":auto_service",
        "//third_party:guava",
        "@com_google_auto_factory_auto_factory//jar",
        "@com_squareup_javawriter//jar",
    ],
)

java_library(
    name = "auto_factory",
    exported_plugins = [":auto_factory_processor"],
    exports = ["@com_google_auto_factory_auto_factory//jar"],
)

java_plugin(
    name = "auto_service_processor",
    processor_class = "com.google.auto.service.processor.AutoServiceProcessor",
    visibility = ["//visibility:private"],
    deps = [
        ":auto_common",
        "//third_party:guava",
        "@com_google_auto_service_auto_service//jar",
    ],
)

java_library(
    name = "auto_service",
    exported_plugins = [":auto_service_processor"],
    tags = ["maven:compile_only"],
    exports = ["@com_google_auto_service_auto_service//jar"],
)

java_library(
    name = "google_java_format",
    exports = [
        "@com_google_googlejavaformat_google_java_format//jar",
    ],
    runtime_deps = [
        ":guava",
        "@com_google_errorprone_javac//jar",
    ],
)

java_library(
    name = "javapoet",
    exports = ["@com_squareup_javapoet//jar"],
)

java_library(
    name = "mockito",
    testonly = 1,
    exports = ["@org_mockito_mockito_core//jar"],
    runtime_deps = [
        "@org_hamcrest_hamcrest_core//jar",
        "@org_objenesis_objenesis//jar",
    ],
)

java_library(
    name = "grpc",
    exports = ["@io_grpc_grpc_all//jar"],
)

java_library(
    name = "grpc_core",
    exports = ["@io_grpc_grpc_core//jar"],
)

java_library(
    name = "grpc_netty",
    exports = ["@io_grpc_grpc_netty//jar"],
)

java_library(
    name = "grpc_context",
    exports = ["@io_grpc_grpc_context//jar"],
)

java_library(
    name = "grpc_protobuf",
    exports = ["@io_grpc_grpc_protobuf//jar"],
)

java_library(
    name = "grpc_stub",
    exports = ["@io_grpc_grpc_stub//jar"],
)

java_library(
    name = "protobuf",
    exports = ["@com_google_protobuf_protobuf_java//jar"],
)

java_library(
    name = "compile_testing",
    testonly = 1,
    exports = [
        "@com_google_testing_compile_compile_testing//jar",
    ],
    runtime_deps = [
        ":auto_value",
        ":jsr305_annotations",
        "//third_party:error_prone_annotations",
        "//third_party:guava",
        "//third_party:junit",
        "//third_party:truth",
        "@local_jdk//:lib/tools.jar",
    ],
)
