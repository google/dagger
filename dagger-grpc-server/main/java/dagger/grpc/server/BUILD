# A framework supporting Dagger-injected gRPC servers.

load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
    "JAVA_RELEASE_MIN",
)

package(default_visibility = ["//:src"])

# TODO(dpb): Split out the grpc:inprocess and grpc:netty deps into separate libraries.
java_library(
    name = "server",
    srcs = glob(["*.java"]),
    exported_plugins = [
        "//dagger-grpc-server-processor:plugin",
    ],
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES + JAVA_RELEASE_MIN,
    exports = ["//dagger-grpc-server-annotations:artifact-lib"],
    deps = [
        "//third_party/java/auto:value",
        "//third_party/java/dagger",
        "//third_party/java/grpc:context",
        "//third_party/java/grpc:core",
        "//third_party/java/grpc:netty",
        "//third_party/java/grpc:protobuf",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/jsr330_inject",
        "//third_party/java/protobuf",
    ],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(["*.java"]),
)
