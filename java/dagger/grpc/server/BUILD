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
        "@google_bazel_common//third_party/java/jsr330_inject",
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
        "@google_bazel_common//third_party:guava",
        "@google_bazel_common//third_party:protobuf",
        "@google_bazel_common//third_party/java/auto:value",
        "@google_bazel_common//third_party/java/grpc:context",
        "@google_bazel_common//third_party/java/grpc:core",
        "@google_bazel_common//third_party/java/grpc:netty",
        "@google_bazel_common//third_party/java/grpc:protobuf",
        "@google_bazel_common//third_party/java/jsr330_inject",
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
