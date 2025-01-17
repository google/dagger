package(default_visibility = ["//:src"])

android_library(
    name = "main_lib",
    srcs = glob(["*.java"]),
    manifest = "AndroidManifest.xml",
    resource_files = glob(["res/**"]),
    deps = [
        "//:android",
        "//:android-support",
        "//:dagger_with_compiler",
        "@androidsdk//com.android.support:appcompat-v7-25.0.0",
        "@androidsdk//com.android.support:support-annotations-25.0.0",
        "@androidsdk//com.android.support:support-v4-25.0.0",
    ],
)

android_binary(
    name = "espresso_main",
    manifest = "AndroidManifest.xml",
    deps = [
        "//java/dagger/example/android/espresso/main:main_lib"
    ]
)