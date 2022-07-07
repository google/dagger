# Functional tests for Dagger-gRPC

load("//net/proto2/compiler/stubby/java:java_stubby_library.bzl", "java_stubby_library")
load("@rules_java//java:defs.bzl", "java_proto_library")

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
    use_java_stubby_library = True,
)

java_stubby_library(
    name = "java_stubby",
    srcs = [":protos"],
    tags = [],
    deps = [":coffee_service_java_proto"],
)
