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

## How to build dagger locally

TODO THESE ARE WIP NOTES

* Go to [http://bazel.build](http://bazel.build) , click on GET BAZEL, install bazel
* Clone the repository: `git clone git@github.com:google/dagger.git dagger`
* Install the IntelliJ Bazel plugin: go https://docs.bazel.build/versions/master/ide.html, click on the link, click on Download

 => Run Android Studio > About => Look at the weird version number => download the corresponding plugin.
* In IntelliJ, click on `Plugins`, `Install plugin from disk...` and select `aswb_bazel.zip`
* Android Studio > Import Bazel Project
* Add the dagger folder as the bazel workspace
* Generate from BUILD file => type "BUILD"

Next screen: under additional_languages: 

Make suer that there's a android_sdk_platform line. Any of them should be fine, but make sure you have that version install => TODO ADD REMINDER FOR HOW TO CHECK

Under targets should be "// ...all"

=> COPY PASTE HERE THE EXPECT FINAL RESULT

=> Then wait, it's indexing.




COPIED FROM README:

Dagger is built with [`bazel`]. The tests can be run with `bazel test //...`.
`util/install-local-snapshot.sh` will build all of the Dagger libraries and
install a copy in your local maven repository with the version `LOCAL-SNAPSHOT`.

