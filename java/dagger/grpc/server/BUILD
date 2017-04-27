# A framework supporting Dagger-injected gRPC servers.

package(default_visibility = ["//:src"])

load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX", "DOCLINT_REFERENCES")

ANNOTATIONS_SRCS = [
    "CallScoped.java",
    "ForGrpcService.java",
    "GrpcService.java",
]

java_library(
    name = "annotations",
    srcs = ANNOTATIONS_SRCS,
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    deps = [
        "//third_party:jsr330_inject",
    ],
)

# TODO(dpb): Split out the grpc:inprocess and grpc:netty deps into separate libraries.
java_library(
    name = "server",
    srcs = glob(
        ["*.java"],
        exclude = ANNOTATIONS_SRCS,
    ),
    exported_plugins = ["//java/dagger/grpc/server/processor:plugin"],
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    exports = [":annotations"],
    deps = [
        "//:dagger_with_compiler",
        "//third_party:auto_value",
        "//third_party:grpc_context",
        "//third_party:grpc_core",
        "//third_party:grpc_netty",
        "//third_party:grpc_protobuf",
        "//third_party:guava",
        "//third_party:jsr305_annotations",
        "//third_party:jsr330_inject",
        "//third_party:protobuf",
    ],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(["*.java"]),
)

load("//tools:javadoc.bzl", "javadoc_library")

javadoc_library(
    name = "javadoc",
    srcs = [":javadoc-srcs"],
    root_packages = ["dagger.grpc.server"],
    deps = [
        ":annotations",
        ":server",
    ],
)
