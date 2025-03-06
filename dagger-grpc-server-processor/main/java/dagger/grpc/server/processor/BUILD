load("@rules_java//java:defs.bzl", "java_library", "java_plugin")
load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX")

package(default_visibility = [
    "//dagger-grpc-server-processor:internal",
])

filegroup(
    name = "srcs",
    srcs = glob(["*.java"]),
)

java_library(
    name = "processor",
    srcs = [":srcs"],
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    deps = [
        "//:dagger_with_compiler",
        "//java/dagger/grpc/server:annotations",
        "//third_party/java/auto:common",
        "//third_party/java/auto:service",
        "//third_party/java/google_java_format",
        "//third_party/java/guava/base",
        "//third_party/java/guava/collect",
        "//third_party/java/guava/io",
        "//third_party/java/javapoet",
        "//third_party/java/jsr250_annotations",
    ],
)

java_plugin(
    name = "plugin",
    generates_api = 1,
    processor_class = "dagger.grpc.server.processor.GrpcServiceProcessor",
    deps = [":processor"],
)
