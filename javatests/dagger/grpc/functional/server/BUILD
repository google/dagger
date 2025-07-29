# Functional tests for Dagger-gRPC

load("@com_google_protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@com_google_protobuf//bazel:proto_library.bzl", "proto_library")

package(default_visibility = ["//:src"])

# TODO(dpb): enable tests once java_grpc_library is ready in bazel:
# https://github.com/grpc/grpc-java/issues/2756

java_proto_library(
    name = "coffee_service_java_proto",
    deps = [":protos"],
)

proto_library(
    name = "protos",
    srcs = glob(["*.proto"]),
)
