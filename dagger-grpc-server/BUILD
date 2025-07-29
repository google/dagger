# A framework supporting Dagger-injected gRPC servers.

load("@rules_java//java:defs.bzl", "java_library")
load("//:build_defs.bzl", "POM_VERSION")
load("//tools/maven:maven.bzl", "gen_maven_artifact")

package(default_visibility = ["//:src"])

java_library(
    name = "artifact-lib",
    tags = ["maven_coordinates=com.google.dagger:dagger-grpc-server:" + POM_VERSION],
    exports = [
        "//dagger-grpc-server/main/java/dagger/grpc/server",
    ],
)

gen_maven_artifact(
    name = "artifact",
    artifact_coordinates = "com.google.dagger:dagger-grpc-server:" + POM_VERSION,
    artifact_name = "Dagger gRPC Server",
    artifact_target = ":artifact-lib",
    artifact_target_libs = [
        "//dagger-grpc-server/main/java/dagger/grpc/server",
    ],
    artifact_target_maven_deps = [
        "com.google.dagger:dagger",
        "com.google.dagger:dagger-grpc-server-annotations",
        "com.google.guava:failureaccess",
        "com.google.guava:guava",
        "com.google.protobuf:protobuf-java",
        "io.grpc:grpc-context",
        "io.grpc:grpc-core",
        "io.grpc:grpc-netty",
        "io.grpc:grpc-protobuf",
        "javax.inject:javax.inject",
    ],
    javadoc_root_packages = [
        "dagger.grpc.server",
    ],
    javadoc_srcs = [
        "//dagger-grpc-server/main/java/dagger/grpc/server:javadoc-srcs",
    ],
)
