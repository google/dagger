# see http://benlimmer.com/2013/12/26/automatically-publish-javadoc-to-gh-pages-with-travis-ci/ for details

set -eu

if [ "$TRAVIS_REPO_SLUG" == "google/dagger" ] && \
   [ "$TRAVIS_JDK_VERSION" == "$JDK_FOR_PUBLISHING" ] && \
   [ "$TRAVIS_PULL_REQUEST" == "false" ] && \
   [ "$TRAVIS_BRANCH" == "master" ]; then
  echo -e "Publishing javadoc...\n"
  bazel build //:user-docs.jar
  JAVADOC_JAR="$(pwd)/bazel-bin/user-docs.jar"

  cd $HOME
  git clone --quiet --branch=gh-pages https://${GH_TOKEN}@github.com/google/dagger gh-pages > /dev/null

  cd gh-pages
  git config --global user.email "travis@travis-ci.org"
  git config --global user.name "travis-ci"
  git rm -rf api/latest
  mkdir -p api
  unzip "$JAVADOC_JAR" -d api/latest
  rm -rf api/latest/META-INF/
  git add -f api/latest
  git commit -m "Lastest javadoc on successful travis build $TRAVIS_BUILD_NUMBER auto-pushed to gh-pages"
  git push -fq origin gh-pages > /dev/null

  echo -e "Published Javadoc to gh-pages.\n"
else
  echo -e "Not publishing docs for jdk=${TRAVIS_JDK_VERSION} and branch=${TRAVIS_BRANCH}"
fi
