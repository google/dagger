# A framework supporting Dagger-injected gRPC servers.

load("@rules_java//java:defs.bzl", "java_library")
load(
    "//:build_defs.bzl",
    "DOCLINT_HTML_AND_SYNTAX",
    "JAVA_RELEASE_MIN",
)

package(default_visibility = ["//:src"])

java_library(
    name = "annotations",
    srcs = glob(["*.java"]),
    javacopts = DOCLINT_HTML_AND_SYNTAX + JAVA_RELEASE_MIN,
    deps = [
        "//third_party/java/jsr330_inject",
    ],
)

filegroup(
    name = "javadoc-srcs",
    srcs = glob(["*.java"]),
)
