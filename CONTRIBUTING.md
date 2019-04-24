# How to contribute

We'd love to accept your patches and contributions to this project. There are
just a few small guidelines you need to follow.

## Contributor License Agreement

Contributions to any Google project must be accompanied by a Contributor License
Agreement. This is necessary because you own the copyright to your changes, even
after your contribution becomes part of this project. So this agreement simply
gives us permission to use and redistribute your contributions as part of the
project. Head over to <https://cla.developers.google.com/> to see your current
agreements on file or to sign a new one.

You generally only need to submit a CLA once, so if you've already submitted one
(even if it was for a different project), you probably don't need to do it
again.

## Code reviews

All submissions, including submissions by project members, require review. We
use GitHub pull requests for this purpose. Consult [GitHub Help] for more
information on using pull requests.

[GitHub Help]: https://help.github.com/articles/about-pull-requests/

## Building Dagger

Dagger is built with [`bazel`](https://bazel.build).

### Building Dagger from the command line

*   [Install Bazel](https://docs.bazel.build/versions/master/install.html)
*   Build the Dagger project with `bazel build <target>`
    *   Learn more about Bazel targets [here][bazel targets].
    *   If you see an error similar to `ERROR: missing input file
        '@androidsdk//:build-tools/26.0.2/aapt'`, install the missing build
        tools version with the android `sdkmanager` tool.
*   Run tests with `bazel test <target>`, or `bazel test //...` to run all
    tests
*   You can install the Dagger libraries in your **local maven repository** by
    running the `./util/install-local-snapshot.sh` script.
    *   It will build the libraries and install them with a `LOCAL-SNAPSHOT`
        version.

[bazel targets]: https://docs.bazel.build/versions/master/build-ref.html

### Importing the Dagger project in IntelliJ/Android Studio

*   Visit `Preferences > Plugins` in the IDE menu.
    *   Search for `bazel` and install the plugin.
    *   If no result shows up, click on `Search in repositories`, search for
        `bazel` and install the plugin.
*   Select `Import Bazel Project`.
*   Input the path to the Dagger project under `workspace`, click `Next`.
*   Select `Generate from BUILD file`, type `BUILD` in the `Build file` input,
    click `Next`.
*   [Android Studio only] In the `Project View` form, uncomment one of the
    `android_sdk_platform` lines. Pick one that you have installed, then click
    `Finish`.
*   If you get an error on Bazel sync, `Cannot run program "bazel"`, then:
    *   In the command line, run `where bazel` and copy the output  (e.g.
        `/usr/local/bin/bazel`)
    *   In Android Studio, go to `Preferences > Bazel Settings` and replace
        `Bazel binary location` with what you just copied.
*   Note that the first sync can take a long time. When build files are changed,
    you can run partial syncs (which should be faster) from the file menu.
*   [Android Studio only] To view the Dagger project structure, open the
    `Project` view and switch the top selector from `Android` to `Project`.
