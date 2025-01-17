android_library(
    name = "android_tests",
        srcs = glob(["*Test.java"]),
        deps = [
            "@junit_junit//jar",
            "@org_hamcrest_hamcrest_core//jar",
            "@androidsdk//com.android.support.test.espresso:espresso-core-2.2.2",
            "@androidsdk//com.android.support.test:runner-0.5",
            "@androidsdk//com.android.support.test:rules-0.5",
            "//java/dagger/example/android/espresso/stubbed:stubbed_lib",
            "//java/dagger/example/android/espresso/main:main_lib" 
        ],
)