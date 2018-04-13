package(default_visibility = ["//:src"])

load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX")

java_library(
    name = "processor",
    srcs = glob(["*.java"]),
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    tags = ["maven_coordinates=com.google.dagger:dagger-grpc-server-processor:${project.version}"],
    deps = [
        "//:dagger_with_compiler",
        "//java/dagger/grpc/server:annotations",
        "//third_party:auto_common",
        "//third_party:auto_service",
        "//third_party:google_java_format",
        "//third_party:guava",
        "//third_party:javapoet",
        "//third_party:jsr250_annotations",
    ],
)

java_plugin(
    name = "plugin",
    generates_api = 1,
    processor_class = "dagger.grpc.server.processor.GrpcServiceProcessor",
    deps = [":processor"],
)

load("//tools:mvn.bzl", "dagger_pom_file")

dagger_pom_file(
    name = "pom.xml",
    artifact_id = "dagger-grpc-server-processor",
    artifact_name = "Dagger gRPC Server Processor",
    targets = [":processor"],
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
