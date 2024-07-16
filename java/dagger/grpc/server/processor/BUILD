load("@rules_java//java:defs.bzl", "java_library", "java_plugin")
load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "POM_VERSION",
)
load("//tools/javadoc:javadoc.bzl", "javadoc_library")
load("//tools/maven:maven.bzl", "dagger_pom_file")

package(default_visibility = ["//:src"])

java_library(
    name = "processor",
    srcs = glob(["*.java"]),
    javacopts = DOCLINT_HTML_AND_SYNTAX,
    tags = ["maven_coordinates=com.google.dagger:dagger-grpc-server-processor:" + POM_VERSION],
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

dagger_pom_file(
    name = "pom",
    artifact_id = "dagger-grpc-server-processor",
    artifact_name = "Dagger gRPC Server Processor",
    targets = [":processor"],
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

javadoc_library(
    name = "javadoc",
    srcs = [":javadoc-srcs"],
    root_packages = ["dagger.grpc.server.processor"],
    deps = [":processor"],
)
