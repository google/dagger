# Description:
#   ErrorProne refactorings and static analysis for Dagger

load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//:src"])

java_library(
    name = "errorprone",
    srcs = glob(["*.java"]),
    deps = [
        "//java/dagger:core",
        "//third_party/java/error_prone:check_api",
        "//third_party/java/guava/collect",
    ],
)
