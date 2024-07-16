# A framework supporting Dagger-injected gRPC servers.

load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "DOCLINT_REFERENCES",
    "POM_VERSION",
)
load("//tools/javadoc:javadoc.bzl", "javadoc_library")
load("//tools/maven:maven.bzl", "dagger_pom_file")

package(default_visibility = ["//:src"])

ANNOTATIONS_SRCS = [
    "CallScoped.java",
    "ForGrpcService.java",
    "GrpcService.java",
]

java_library(
    name = "annotations",
    srcs = ANNOTATIONS_SRCS,
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    tags = ["maven_coordinates=com.google.dagger:dagger-grpc-server-annotations:" + POM_VERSION],
    deps = [
        "//third_party/java/jsr330_inject",
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
    tags = ["maven_coordinates=com.google.dagger:dagger-grpc-server:" + POM_VERSION],
    exports = [":annotations"],
    deps = [
        "//:dagger_with_compiler",
        "//third_party/java/auto:value",
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

dagger_pom_file(
    name = "annotations-pom",
    artifact_id = "dagger-grpc-server-annotations",
    artifact_name = "Dagger gRPC Server Annotations",
    targets = [":annotations"],
)

dagger_pom_file(
    name = "server-pom",
    artifact_id = "dagger-grpc-server",
    artifact_name = "Dagger gRPC Server",
    targets = [":server"],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(["*.java"]),
)

javadoc_library(
    name = "javadoc",
    srcs = [":javadoc-srcs"],
    root_packages = ["dagger.grpc.server"],
    deps = [
        ":annotations",
        ":server",
    ],
)
