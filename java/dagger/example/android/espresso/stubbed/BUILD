package(default_visibility = ["//:src"])

android_library(
    name = "stubbed_lib",
    srcs = glob(["*.java"]),
    manifest = 'AndroidManifest.xml',
    deps = [
        "//:android",
        "//:android-support",
        "//:dagger_with_compiler",
        "@androidsdk//com.android.support:appcompat-v7-25.0.0",
        "@androidsdk//com.android.support:support-annotations-25.0.0",
        "@androidsdk//com.android.support:support-v4-25.0.0",
        "//java/dagger/example/android/espresso/main:main_lib"
    ],
)

android_binary(
    name = "espresso_stubbed",    
    manifest = 'AndroidManifest.xml',
    manifest_merger = "android",
    deps = [
        "//java/dagger/example/android/espresso/stubbed:stubbed_lib",
    ]
)