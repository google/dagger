# Dagger Hilt Coffee Maker Example (Gradle)

This project contains the standalone Gradle-based Android application
implementation of the Coffee Maker example using **Dagger Hilt**, mirroring the
Bazel Hilt example in `examples/bazel`.

It demonstrates core Hilt concepts for Android:

- `@HiltAndroidApp` application setup
- Module declaration with `@InstallIn(SingletonComponent.class)`
- Standard Dagger bindings (`@Binds`, `@Inject`) in Hilt
- Testing with `@HiltAndroidTest`, `@BindValue`, and Robolectric

## Project Structure

```
.
├── build.gradle
├── settings.gradle
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   └── java/
    │       └── example/
    │           ├── common/
    │           │   ├── CoffeeLogger.java
    │           │   ├── CoffeeMaker.java
    │           │   ├── ElectricHeater.java
    │           │   ├── Heater.java
    │           │   ├── Pump.java
    │           │   └── Thermosiphon.java
    │           └── hilt/
    │               ├── CoffeeApp.java
    │               ├── HeaterModule.java
    │               └── PumpModule.java
    └── test/
        └── java/
            └── example/
                └── hilt/
                    ├── CoffeeAppFakeHeaterTest.java
                    └── CoffeeAppFakePumpTest.java
```

## Opening in Android Studio

1. Launch Android Studio.
2. Select **File** > **Open...** (or click **Open** on the Welcome screen).
3. Navigate to and select this directory (`examples/gradle/hilt`).
4. Android Studio will import the Gradle project, sync Android dependencies, and configure the project.

## Running Tests

Run the Robolectric unit tests from the command line using Gradle:

```bash
./gradlew test
```

Or from within Android Studio:

- Right-click on `src/test/java/example/hilt` in the Project tree and select
  **Run 'Tests in 'example.hilt''**.
