# Dagger

[![Maven Central][mavenbadge-svg]][mavencentral]

A fast dependency injector for Java and Android.

Dagger is a compile-time framework for dependency injection. It uses no
reflection or runtime bytecode generation, does all its analysis at
compile-time, and generates plain Java source code.

Dagger is actively maintained by the same team that works on [Guava]. Snapshot
releases are auto-deployed to Sonatype's central Maven repository on every clean
build with the version `HEAD-SNAPSHOT`. The current version builds upon previous
work done at [Square][square].

## Documentation

You can [find the dagger documentation here][website] which has extended usage
instructions and other useful information. More detailed information can be
found in the [API documentation][latestapi].

You can also learn more from [the original proposal][proposal],
[this talk by Greg Kick][gaktalk], and on the dagger-discuss@googlegroups.com
mailing list.

## Installation

### Bazel

First, import the Dagger repository into your `WORKSPACE` file using
[`http_archive`][bazel-external-deps].

Note: The `http_archive` must point to a tagged release of Dagger, not just any
commit. The version of the Dagger artifacts will match the version of the tagged
release.

```python
# Top-level WORKSPACE file

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

DAGGER_TAG = "2.28.1"
DAGGER_SHA = "9e69ab2f9a47e0f74e71fe49098bea908c528aa02fa0c5995334447b310d0cdd"
http_archive(
    name = "dagger",
    strip_prefix = "dagger-dagger-%s" % DAGGER_TAG,
    sha256 = DAGGER_SHA,
    urls = ["https://github.com/google/dagger/archive/dagger-%s.zip" % DAGGER_TAG],
)
```

Next you will need to setup targets that export the proper dependencies
and plugins. Follow the sections below to setup the dependencies you need.

#### Dagger Setup

First, load the Dagger artifacts and repositories, and add them to your list of
[`maven_install`] artifacts.

```python
# Top-level WORKSPACE file

load("@dagger//:workspace_defs.bzl", "DAGGER_ARTIFACTS", "DAGGER_REPOSITORIES")

maven_install(
    artifacts = DAGGER_ARTIFACTS + [...],
    repositories = DAGGER_REPOSITORIES + [...],
)
```

Next, load and call [`dagger_rules`](https://github.com/google/dagger/blob/master/workspace_defs.bzl)
in your top-level `BUILD` file:

```python
# Top-level BUILD file

load("@dagger//:workspace_defs.bzl", "dagger_rules")

dagger_rules()
```

This will add the following Dagger build targets:
(Note that these targets already export all of the dependencies and processors
they need).

```python
deps = [
    ":dagger",                  # For Dagger
    ":dagger-spi",              # For Dagger SPI
    ":dagger-producers",        # For Dagger Producers
]
```

#### Dagger Android Setup

First, load the Dagger Android artifacts and repositories, and add them to your
list of [`maven_install`] artifacts.

```python
# Top-level WORKSPACE file

load(
    "@dagger//:workspace_defs.bzl",
    "DAGGER_ANDROID_ARTIFACTS",
    "DAGGER_ANDROID_REPOSITORIES"
)

maven_install(
    artifacts = DAGGER_ANDROID_ARTIFACTS + [...],
    repositories = DAGGER_ANDROID_REPOSITORIES + [...],
)
```

Next, load and call [`dagger_android_rules`](https://github.com/google/dagger/blob/master/workspace_defs.bzl)
in your top-level `BUILD` file:

```python
# Top-level BUILD file

load("@dagger//:workspace_defs.bzl", "dagger_android_rules")

dagger_android_rules()
```

This will add the following Dagger Android build targets:
(Note that these targets already export all of the dependencies and processors
they need).

```python
deps = [
    ":dagger-android",          # For Dagger Android
    ":dagger-android-support",  # For Dagger Android (Support)
]
```

#### Hilt Android Setup

First, load the Hilt Android artifacts and repositories, and add them to your
list of [`maven_install`] artifacts.

```python
# Top-level WORKSPACE file

load(
    "@dagger//:workspace_defs.bzl",
    "HILT_ANDROID_ARTIFACTS",
    "HILT_ANDROID_REPOSITORIES"
)

maven_install(
    artifacts = HILT_ANDROID_ARTIFACTS + [...],
    repositories = HILT_ANDROID_REPOSITORIES + [...],
)
```

Next, load and call [`hilt_android_rules`](https://github.com/google/dagger/blob/master/workspace_defs.bzl)
in your top-level `BUILD` file:

```python
# Top-level BUILD file

load("@dagger//:workspace_defs.bzl", "hilt_android_rules")

hilt_android_rules()
```

This will add the following Hilt Android build targets:
(Note that these targets already export all of the dependencies and processors
they need).

```python
deps = [
    ":hilt-android",            # For Hilt Android
    ":hilt-android-testing",    # For Hilt Android Testing
]
```

### Other build systems

You will need to include the `dagger-2.x.jar` in your application's runtime.
In order to activate code generation and generate implementations to manage
your graph you will need to include `dagger-compiler-2.x.jar` in your build
at compile time.

#### Maven

In a Maven project, include the `dagger` artifact in the dependencies section
of your `pom.xml` and the `dagger-compiler` artifact as an
`annotationProcessorPaths` value of the `maven-compiler-plugin`:

```xml
<dependencies>
  <dependency>
    <groupId>com.google.dagger</groupId>
    <artifactId>dagger</artifactId>
    <version>2.x</version>
  </dependency>
</dependencies>
<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <version>3.6.1</version>
      <configuration>
        <annotationProcessorPaths>
          <path>
            <groupId>com.google.dagger</groupId>
            <artifactId>dagger-compiler</artifactId>
            <version>2.x</version>
          </path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>
  </plugins>
</build>
```

If you are using a version of the `maven-compiler-plugin` lower than `3.5`, add
the `dagger-compiler` artifact with the `provided` scope:

```xml
<dependencies>
  <dependency>
    <groupId>com.google.dagger</groupId>
    <artifactId>dagger</artifactId>
    <version>2.x</version>
  </dependency>
  <dependency>
    <groupId>com.google.dagger</groupId>
    <artifactId>dagger-compiler</artifactId>
    <version>2.x</version>
    <scope>provided</scope>
  </dependency>
</dependencies>
```

If you use the beta `dagger-producers` extension (which supplies
parallelizable execution graphs), then add this to your maven configuration:

```xml
<dependencies>
  <dependency>
    <groupId>com.google.dagger</groupId>
    <artifactId>dagger-producers</artifactId>
    <version>2.x</version>
  </dependency>
</dependencies>
```

#### Gradle
```groovy
// Add Dagger dependencies
dependencies {
  implementation 'com.google.dagger:dagger:2.x'
  annotationProcessor 'com.google.dagger:dagger-compiler:2.x'
}
```

If you're using classes in `dagger.android` you'll also want to include:

```groovy
implementation 'com.google.dagger:dagger-android:2.x'
implementation 'com.google.dagger:dagger-android-support:2.x' // if you use the support libraries
annotationProcessor 'com.google.dagger:dagger-android-processor:2.x'
```

Notes:

-   We use `implementation` instead of `api` for better compilation performance.
    -   See the [Gradle documentation][gradle-api-implementation] for more
        information on how to select appropriately, and the [Android Gradle
        plugin documentation][gradle-api-implementation-android] for Android
        projects.
-   For Kotlin projects, use [`kapt`] in place of `annotationProcessor`.

If you're using the [Android Databinding library][databinding], you may want to
increase the number of errors that `javac` will print. When Dagger prints an
error, databinding compilation will halt and sometimes print more than 100
errors, which is the default amount for `javac`. For more information, see
[Issue 306](https://github.com/google/dagger/issues/306).

```groovy
gradle.projectsEvaluated {
  tasks.withType(JavaCompile) {
    options.compilerArgs << "-Xmaxerrs" << "500" // or whatever number you want
  }
}
```

### Resources

*   [Documentation][website]
*   [Javadocs][latestapi]
*   [GitHub Issues]


If you do not use maven, gradle, ivy, or other build systems that consume
maven-style binary artifacts, they can be downloaded directly via the
[Maven Central Repository][mavencentral].

Developer snapshots are available from Sonatype's
[snapshot repository][dagger-snap], and are built on a clean build of
the GitHub project's master branch.

## Building Dagger

See [the CONTRIBUTING.md docs][Building Dagger].

## License

    Copyright 2012 The Dagger Authors

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

[`bazel`]: https://bazel.build
[bazel-external-deps]: https://docs.bazel.build/versions/master/external.html#depending-on-other-bazel-projects
[`maven_install`]: https://github.com/bazelbuild/rules_jvm_external#exporting-and-consuming-artifacts-from-external-repositories
[Building Dagger]: CONTRIBUTING.md#building-dagger
[dagger-snap]: https://oss.sonatype.org/content/repositories/snapshots/com/google/dagger/
[databinding]: https://developer.android.com/topic/libraries/data-binding/
[gaktalk]: https://www.youtube.com/watch?v=oK_XtfXPkqw
[GitHub Issues]: https://github.com/google/dagger/issues
[gradle-api-implementation]: https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_separation
[gradle-api-implementation-android]: https://developer.android.com/studio/build/dependencies#dependency_configurations
[Guava]: https://github.com/google/guava
[`kapt`]: https://kotlinlang.org/docs/reference/kapt.html
[latestapi]: https://dagger.dev/api/latest/
[mavenbadge-svg]: https://maven-badges.herokuapp.com/maven-central/com.google.dagger/dagger/badge.svg
[mavencentral]: https://search.maven.org/artifact/com.google.dagger/dagger
[project]: http://github.com/google/dagger/
[proposal]: https://github.com/square/dagger/issues/366
[square]: http://github.com/square/dagger/
[website]: https://dagger.dev
