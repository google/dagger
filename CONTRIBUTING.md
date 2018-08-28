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

* Go to the [Bazel installation page](https://docs.bazel.build/versions/master/install.html) and install Bazel.
  * Learn more about Bazel targets [here](https://docs.bazel.build/versions/master/build-ref.html).
* Clone the Dagger repository: `git clone git@github.com:google/dagger.git dagger`
* Build the Dagger project with `bazel build`
  * If you get the following error: `ERROR: missing input file '@androidsdk//:build-tools/26.0.2/aapt'`, install the missing build tools version with sdkmanager.
* You can install the Dagger libraries in your **local maven repository** by running the `./util/install-local-snapshot.sh` script.
  * It will build the libraries and install them with a `LOCAL-SNAPSHOT` version.
* You can **run all tests** with `bazel test //...` and a single test with the path and class name, e.g. `bazel test ///javatests/dagger/internal/codegen:KeyFactoryTest`.

### Importing the Dagger project in Android Studio

* In *Android Studio*, go to `Preferences > Plugins`.
  * search for `bazel` and install the plugin.
  * If no result shows up, click on `Search in repositories`, search for `bazel` and install the plugin.
  * The Bazel plugin is available for Android Studio 3.2 or greater.
* Select `Import Bazel Project`.
* Input the path to the Dagger project under `workspace`, click `Next`.
* Select `Generate from BUILD file`, type `BUILD` in the `Build file` input, click `Next`.
* In the `Project View` form, uncomment one of the `android_sdk_platform` lines. Pick one that you have installed, then click `Finish`.
  * If you get an error on Bazel sync, `Cannot run program "bazel"`, then:
    * In the command line, run `where bazel` and copy the output  (e.g. `/usr/local/bin/bazel`)
	* In Android Studio, go to `Preferences > Bazel Settings` and replace `Bazel binary location` with what you just copied.
* The first sync can take a long time. When build files are changed, you can run partial syncs (which should be faster) from the file menu.
* To view the Dagger project structure, open the `Project` view and switch the top selector from `Android` to `Project`.
