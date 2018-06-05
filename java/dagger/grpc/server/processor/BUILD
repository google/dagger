package(default_visibility = ["//:src"])

load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX")

java_library(
    name = "processor",
    srcs = glob(["*.java"]),
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    deps = [
        "//:dagger_with_compiler",
        "//java/dagger/grpc/server:annotations",
        "@google_bazel_common//third_party:google_java_format",
        "@google_bazel_common//third_party:guava",
        "@google_bazel_common//third_party/java/auto:common",
        "@google_bazel_common//third_party/java/auto:service",
        "@google_bazel_common//third_party/java/javapoet",
        "@google_bazel_common//third_party/java/jsr250_annotations",
    ],
)

java_plugin(
    name = "plugin",
    generates_api = 1,
    processor_class = "dagger.grpc.server.processor.GrpcServiceProcessor",
    deps = [":processor"],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(["*.java"]),
)

load("//tools:javadoc.bzl", "javadoc_library")

javadoc_library(
    name = "javadoc",
    srcs = [":javadoc-srcs"],
    root_packages = ["dagger.grpc.server.processor"],
    deps = [":processor"],
)
