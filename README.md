Dagger 2
========

A fast dependency injector for Android and Java.

About Google's Fork
-------------

Dagger 2 is a compile-time evolution approach to dependency injection.  Taking the approach
started in Dagger 1.x to its ultimate conclusion, Dagger 2.0 eliminates all reflection, and
improves code clarity by removing the traditional ObjectGraph/Injector in favor of
user-specified @Component interfaces. 

This github project represents the Dagger 2 development stream.  The earlier 
[project page][square] (Square, Inc's repository) represents the earlier 1.0 development stream.  
Both versions have benefitted from strong involvement from Square, Google, and other contributors. 

## [Dagger 2's main documentation website can be found here.][website]

Status
------

  - ***Release Version:* 2.0.1**
  - ***Snapshot Version:* 2.1-SNAPSHOT**

Dagger is currently in active development, primarily internally at Google, with regular pushes
to the open-source community.  Snapshot releases are auto-deployed to sonatype's central maven
repository on a clean build with the version `2.1-SNAPSHOT`.

Documentation
-------------

You can [find the dagger documentation here][website] which has extended usage
instructions and other useful information.  Substantial usage information can be
found in the [API documentation][20api].

You can also learn more from [the original proposal][proposal], 
[this talk by Greg Kick][gaktalk], and on the dagger-discuss@googlegroups.com
mailing list. 

Installation
--------

You will need to include the `dagger-2.0.1.jar` in your application's runtime.
In order to activate code generation and generate implementations to manage
your graph you will need to include `dagger-compiler-2.0.1.jar` in your build
at compile time.

In a Maven project, include the `dagger` artifact in the dependencies section
of your `pom.xml` and the `dagger-compiler` artifact as either an `optional` or
`provided` dependency:

```xml
<dependencies>
  <dependency>
    <groupId>com.google.dagger</groupId>
    <artifactId>dagger</artifactId>
    <version>2.0.1</version>
  </dependency>
  <dependency>
    <groupId>com.google.dagger</groupId>
    <artifactId>dagger-compiler</artifactId>
    <version>2.0.1</version>
    <optional>true</optional>
  </dependency>
</dependencies>
```

If you use the beta `dagger-producers` extension (which supplies parallelizable execution graphs),
then add this to your maven configuration:

```xml
<dependencies>
  <dependency>
    <groupId>com.google.dagger</groupId>
    <artifactId>dagger-producers</artifactId>
    <version>2.0-beta</version>
  </dependency>
</dependencies>
```


### Download 

  * 2.x (google/dagger)
    * [Dagger 2.0 Documentation][website]
    * [Dagger 2.0 Javadocs][20api]
    * [Dagger development Javadocs][latestapi] (from the `master` branch on GitHub)
    * [Google's Dagger project site on GitHub][project]
    * <a href="https://plus.google.com/118328287768685565185" rel="publisher">Google+ Dagger Project Page</a>
    * [Google+ Dagger Users Community][community]
  * 1.x (square/dagger)
    * [Square's original Dagger project site on GitHub][square]
    * [Square Open Source Community][squarecommunity]


If you do not use maven, gradle, ivy, or other build systems that consume maven-style binary
artifacts, they can be downloaded directly via the [Maven Central Repository][mavensearch].

Developer snapshots are available from [Sonatype's snapshot repository][dagger-snap], and
are built on a clean build of the GitHub project's master branch.

License
-------

    Copyright 2012 Square, Inc.
    Copyright 2012 Google, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.



 [mavensearch]: http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.google.dagger%22
 [dagger-snap]: https://oss.sonatype.org/content/repositories/snapshots/com/google/dagger/
 [website]: http://google.github.io/dagger
 [latestapi]: http://google.github.io/dagger/api/latest/
 [20api]: http://google.github.io/dagger/api/2.0/
 [gaktalk]: https://www.youtube.com/watch?v=oK_XtfXPkqw
 [proposal]: https://github.com/square/dagger/issues/366
 [project]: http://github.com/google/dagger/
 [community]: https://plus.google.com/communities/111933036769103367883
 [square]: http://github.com/square/dagger/
 [squarecommunity]: https://plus.google.com/communities/109244258569782858265

