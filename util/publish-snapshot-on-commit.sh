# see https://coderwall.com/p/9b_lfq

set -eu

if [ "$TRAVIS_REPO_SLUG" == "google/dagger" ] && \
   [ "$TRAVIS_JDK_VERSION" == "$JDK_FOR_PUBLISHING" ] && \
   [ "$TRAVIS_PULL_REQUEST" == "false" ] && \
   [ "$TRAVIS_BRANCH" == "master" ]; then
  echo -e "Publishing maven snapshot...\n"

  sh $(dirname $0)/execute-deploy.sh \
    "HEAD-SNAPSHOT" \
    "sonatype-nexus-snapshots" \
    "https://oss.sonatype.org/content/repositories/snapshots" \
    "--settings=util/settings.xml"

  echo -e "Published maven snapshot"
else
  echo -e "Not publishing snapshot for jdk=${TRAVIS_JDK_VERSION} and branch=${TRAVIS_BRANCH}"
fi
