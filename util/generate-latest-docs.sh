set -eux

if [ "$GITHUB_REPOSITORY" == "google/dagger" ] && \
   [ "$GITHUB_EVENT_NAME" == "push" ] && \
   [ "$GITHUB_REF" == "refs/heads/master" ]; then
  echo -e "Publishing javadoc...\n"
  bazel build //:user-docs.jar
  JAVADOC_JAR="$(pwd)/bazel-bin/user-docs.jar"

  cd $HOME
  git clone --quiet --branch=gh-pages https://x-access-token:${GH_TOKEN}@github.com/google/dagger gh-pages > /dev/null

  cd gh-pages
  git config --global user.email "dagger-dev+github@google.com"
  git config --global user.name "Dagger Team"
  git rm -rf api/latest
  mkdir -p api
  unzip "$JAVADOC_JAR" -d api/latest
  rm -rf api/latest/META-INF/
  git add -f api/latest

  # Check if there are any changes before committing, otherwise attempting
  # to commit will fail the build (https://stackoverflow.com/a/2659808).
  if [[ $(git diff-index --quiet HEAD --) || $? == 1 ]]; then
    # The exist status is 1, meaning there are changes to commit
    git commit -m "Latest javadoc on successful Github build $GITHUB_WORKFLOW/$GITHUB_RUN_ID auto-pushed to gh-pages"
    git push -fq origin gh-pages > /dev/null
    echo -e "Published Javadoc to gh-pages.\n"
  else
    # The exist status is 0, meaning there are no changes to commit
    echo -e "Skipping publishing docs since no changes were detected."
  fi
else
  echo -e "Not publishing docs for branch=${$GITHUB_REF}"
fi
