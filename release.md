---
layout: default
title: Releasing Dagger Publicly
---

* Will be replaced with the ToC
{:toc}

## Overview

At a high level, the steps involved are as listed in the above table of
contents.  Namely, branch for release, update the versions, push to
sonatype, and finalize the release. Each step has some ideosyncracies, and
follows below.

Any specific version numbers should be assumed to be examples and the real,
current version numbers should be substituted.

## Detail

### Preconditions

***NOTE:*** *These preconditions include important minutia of maven
deployments.  Make sure you read the [OSSRH Guide] and the [Sonatype GPG
blog post][GPG].*

Releases involve releasing to Sonatype's managed repository which backs the
maven central repository.  To push bits to sonatype requires:

  1. an account on oss.sonatype.org
  2. permission for that account to push com.google.dagger
  3. a pgp certificate (via gnupg) with a published public key
  4. a [${HOME}/.m2/settings.xml][settings.xml] file containing the credentials
     for the account created in step #1, associated with the server id
     `sonatype-nexus-staging`

The administrative steps above are all documented in Sonatype's
[OSSRH Guide]. The GPG instructions particular to this process can be found
in this [Sonatype GPG blog entry][GPG].


### Create a release branch

First checkout the main project's master branch, and create a branch on which
to do the release work (to avoid clobbering anything on the master branch):

```shell
git clone git@github.com:google/dagger.git dagger_release
cd dagger_release
git checkout -b prepare_release_2_1
bazel test //...
```

This generates a new branch, and does a full build to ensure that what is
currently at the tip of the branch is sound.

Make sure to also update any not-yet-released version numbers in Javadoc to
the the upcoming version.

```shell
sed -i s/"@since 2.NEXT"/"@since 2.<new version>"/g $(find . | grep \\\\.java)
```

### Tag the release

The release tags simply follow the format `dagger-<version>` so simply do this:

```shell
git tag dagger-2.1
```

### Build and deploy the release to sonatype

A convenience script exists to build the codebase with Bazel and deploy the
signed artifacts to Maven Central. 

It's parameter is the label for your GnuPG key which can be seen by running
`gpg --list-keys` which supplies output similar to the following:

```
pub   2048D/D4906B68 2014-12-16
uid                  Christian Edward Gruber (Maven Deployments) <cgruber@google.com>
```

> More detail about GPG and Sonatype repositories [in this blog post][GPG]

Given the above example, you would then run:

```shell
util/deploy-to-maven-central.sh D4906B68
```

... and the script will kick off the maven job, pausing when it first needs to
sign binaries to ask for your GnuPG certificate passphrase (if any).  It then
pushes the binaries and signatures up to sonatype's staging repository.

### Verify the release on sonatype

Log in to `oss.sonatype.org` and select "Staging repositories".  In the
main window, scroll to the botton where a staging repository named roughly
after the groupId (com.google.dagger) will appear.

> ***Note:*** *while this can be inspected, Sonatype performs several checks
> automatically when going through the release lifecycle, so generally it is
> not necessary to further inspect this staging repo.*

Select the repository.  You can check to ensure it is the correct repository by
descending the tree in the lower info window.  If you are convinced it is the
correct one, click on the `close` button (in the upper menu bar) and optionally
enter a message (which will be included in any notifications people have set
up on that repository).  Wait about 60 seconds or so and refresh.

If successful, the `release` button will be visible.

#### What if it goes wrong?

If sonatype's analysis has rejected the release, you can check the information
in the lower info window to see what went wrong.  Failed analyzes will show
in red, and the problem should be remedied and step #3 (Tag the release) should
be re-attempted with `tag -f dagger-<version>` once the fixes have been
committed.  Then subsequent steps repeated.

### Release the bits on oss.sonatype.org to the public repo

Assuming sonatype's validation was successful, press the `release` button,
fill in the optional message, and the repository will be released and
automatically dropped once its contents have been copied out to the master
repository.

At this point, the maven artifact(s) will be available for consumption by
maven builds within a few minutes (though it will not be present on
<http://search.maven.org> for about an hour).

### Push the tag to github

Since the release was committed to the maven repository, the exact project
state used to generate that should be marked.  To push the above-mentioned
tag to github, just do the standard git command:

```shell
git push --tags
```

## Post-release

Create a CL/commit that updates the versions from (for instance)
`2.1-SNAPSHOT` to the next development version (typically `2.2-SNAPSHOT`).
This commit should also contain any changes that were necessary to release
the project which need to be persisted (any upgraded dependencies, etc.)

> ***Note:*** *Generally do not merge this directly into github as that will disrupt
> the standard MOE sync.  It can either be created as a github pull-request and
> the `moe github_pull` command will turn it into a CL, or it can be created
> in a normal internal CL. The change can then by synced-out in the MOE run.*

Once the release is done, and the tag is pushed, the branch can be safely
deleted.

<!-- References -->

[GPG]: http://blog.sonatype.com/2010/01/how-to-generate-pgp-signatures-with-maven
[OSSRH Guide]: http://central.sonatype.org/pages/ossrh-guide.html
[settings.xml]: https://books.sonatype.com/nexus-book/reference/_adding_credentials_to_your_maven_settings.html
