# Functional tests for Dagger-gRPC

package(default_visibility = ["//:src"])

load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX", "DOCLINT_REFERENCES")
load("//:test_defs.bzl", "GenJavaTests")

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
