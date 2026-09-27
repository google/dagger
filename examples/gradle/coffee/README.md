# Dagger Coffee Maker Example (Gradle)

This project contains the standalone Gradle-based implementation of the Coffee Maker example from the [Dagger Basic Usage Guide](https://dagger.dev/tutorial/).

It demonstrates core Dagger concepts:

- `@Inject`-annotated constructors
- Dependency binding via `@Binds` in `@Module` interfaces
- Component definition via `@Component`
- Lazy injection (`Lazy<Heater>`)
- Singleton scoping (`@Singleton`)

## Project Structure

```
.
├── build.gradle
├── settings.gradle
├── src/
│   └── main/
│       └── java/
│           └── example/
│               ├── common/
│               │   ├── CoffeeLogger.java     # Singleton logger
│               │   ├── CoffeeMaker.java      # Coffee maker brewing logic
│               │   ├── ElectricHeater.java   # Electric heater implementation
│               │   ├── Heater.java           # Heater interface
│               │   ├── Pump.java             # Pump interface
│               │   └── Thermosiphon.java     # Thermosiphon pump implementation
│               └── dagger/
│                   ├── CoffeeApp.java        # Entry point and @Component definition
│                   ├── HeaterModule.java     # Heater bindings module
│                   └── PumpModule.java       # Pump bindings module
```

## Opening in Android Studio or IntelliJ IDEA

1. Launch Android Studio or IntelliJ IDEA.
2. Select **File** > **Open...** (or click **Open** on the Welcome screen).
3. Navigate to and select this directory (`examples/gradle/coffee`).
4. The IDE will automatically detect the Gradle project, import dependencies, and configure the Java toolchain.
5. In your IDE's run configurations or project file tree, navigate to `CoffeeApp.java` and click the Run button next to `main()`.

## Running from the Command Line

Run the application using the Gradle wrapper (or local Gradle):

```bash
./gradlew run
```

Or using system Gradle:

```bash
gradle run
```

### Expected Output

```
~ ~ ~ heating ~ ~ ~
=> => pumping => =>
 [_]P coffee! [_]P
```
