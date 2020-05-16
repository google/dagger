load("@rules_java//java:defs.bzl", "java_library", "java_plugin")

java_plugin(
    name = "dagger_component_plugin",
    generates_api = 1,
    processor_class = "dagger.internal.codegen.ComponentProcessor",
    visibility = ["//visibility:private"],
    deps = [
        "@dagger_simple_maven//:com_google_dagger_dagger_compiler",
    ],
)

java_library(
    name = "dagger",
    exported_plugins = [":dagger_component_plugin"],
    exports = [
        "@dagger_simple_maven//:com_google_dagger_dagger",
        "@dagger_simple_maven//:javax_inject_javax_inject",
    ],
)

java_library(
    name = "simple",
    srcs = glob(["src/**/*.java"]),
    deps = [
        ":dagger",
        "@dagger_simple_maven//:com_google_guava_guava",
    ],
)
