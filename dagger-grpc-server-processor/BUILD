load("@rules_java//java:defs.bzl", "java_library")
load("//:build_defs.bzl", "POM_VERSION")
load("//tools/maven:maven.bzl", "gen_maven_artifact")

package(default_visibility = ["//:src"])

package_group(
    name = "internal",
    packages = [
        "//dagger-grpc-server-processor/...",
    ],
)

# Note: The dagger-grpc-server-processor artifact only requires the dagger-spi artifact because it
# relies on the shaded classes (e.g. auto-common) included with the dagger-spi artifact.
java_library(
    name = "artifact-lib",
    tags = ["maven_coordinates=com.google.dagger:dagger-grpc-server-processor:" + POM_VERSION],
    exports = [
        "//dagger-grpc-server-processor/main/java/dagger/grpc/server/processor",
        "//dagger-spi",
    ],
)

gen_maven_artifact(
    name = "artifact",
    artifact_coordinates = "com.google.dagger:dagger-grpc-server-processor:" + POM_VERSION,
    artifact_name = "Dagger gRPC Server Processor",
    artifact_target = ":artifact-lib",
    artifact_target_libs = [
        "//dagger-grpc-server-processor/main/java/dagger/grpc/server/processor",
    ],
    artifact_target_maven_deps = [
        "com.google.dagger:dagger",
        "com.google.dagger:dagger-grpc-server-annotations",
        "com.google.dagger:dagger-spi",
        "com.google.googlejavaformat:google-java-format",
        "com.google.guava:failureaccess",
        "com.google.guava:guava",
        "com.squareup:javapoet",
        "javax.annotation:javax.annotation-api",
    ],
    javadoc_root_packages = [
        "dagger.grpc.server.processor",
    ],
    javadoc_srcs = [
        "//dagger-grpc-server-processor/main/java/dagger/grpc/server/processor:srcs",
    ],
    # The shaded deps are inherited from dagger spi. For the shaded rules see util/deploy-dagger.sh
)

alias(
    name = "plugin",
    actual = "//dagger-grpc-server-processor/main/java/dagger/grpc/server/processor:plugin",
    visibility = [
        "//dagger-grpc-server/main/java/dagger/grpc/server:__pkg__",
    ],
)
