# Description:
#   ErrorProne refactorings and static analysis for Dagger

package(default_visibility = ["//:src"])

java_library(
    name = "errorprone",
    srcs = glob(["*.java"]),
    deps = [
        "//java/dagger:core",
        "@google_bazel_common//third_party/java/error_prone:check_api",
        "@google_bazel_common//third_party/java/guava",
        "@local_jdk//:lib/tools.jar",
    ],
)
