# Dagger & Hilt Gradle Examples

This directory contains standalone Gradle-based examples for Dagger and Hilt,
configured to open and run out-of-the-box in Android Studio and IntelliJ IDEA.

## Available Examples

* **[`coffee/`](coffee/)**: A pure Java standalone application demonstrating
  standard Dagger 2 dependency injection from the
  [Dagger Basic Usage Tutorial](https://dagger.dev/tutorial/).
  - Run with `./gradlew run` or open in Android Studio / IntelliJ IDEA.
* **[`hilt/`](hilt/)**: An Android application demonstrating **Dagger Hilt**,
  mirroring the Bazel example in `examples/bazel`.
  - Includes `@HiltAndroidApp`, `@InstallIn(SingletonComponent.class)`, and
    Robolectric unit tests with `@HiltAndroidTest` and `@BindValue`.
  - Run tests with `./gradlew test` or open in Android Studio.
