# A framework supporting Dagger-injected gRPC servers.

load("@rules_java//java:defs.bzl", "java_library")
load("//:build_defs.bzl", "POM_VERSION")
load("//tools/maven:maven.bzl", "gen_maven_artifact")

package(default_visibility = ["//:src"])

java_library(
    name = "artifact-lib",
    tags = ["maven_coordinates=com.google.dagger:dagger-grpc-server-annotations:" + POM_VERSION],
    exports = [
        "//dagger-grpc-server-annotations/main/java/dagger/grpc/server:annotations",
    ],
)

gen_maven_artifact(
    name = "artifact",
    artifact_coordinates = "com.google.dagger:dagger-grpc-server-annotations:" + POM_VERSION,
    artifact_name = "Dagger gRPC Server Annotations",
    artifact_target = ":artifact-lib",
    artifact_target_libs = [
        "//dagger-grpc-server-annotations/main/java/dagger/grpc/server:annotations",
    ],
    artifact_target_maven_deps = [
        "javax.inject:javax.inject",
    ],
    javadoc_root_packages = [
        "dagger.grpc.server",
    ],
    javadoc_srcs = [
        "//dagger-grpc-server-annotations/main/java/dagger/grpc/server:javadoc-srcs",
    ],
)
