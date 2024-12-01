# Functional tests for Dagger-gRPC

load("@rules_java//java:defs.bzl", "java_proto_library")

package(default_visibility = ["//:src"])

# TODO(dpb): enable tests once java_grpc_library is ready in bazel:
# https://github.com/grpc/grpc-java/issues/2756

java_proto_library(
    name = "coffee_service_java_proto",
    has_services = True,  # b/241918504
    deps = [":protos"],
)

proto_library(
    name = "protos",
    srcs = glob(["*.proto"]),
)
