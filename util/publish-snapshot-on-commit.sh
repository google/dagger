# see https://coderwall.com/p/9b_lfq

set -eux

if [ "$GITHUB_REPOSITORY" == "google/dagger" ] && \
   [ "$GITHUB_EVENT_NAME" == "push" ] && \
   [ "$GITHUB_REF" == "refs/heads/master" ]; then
  echo -e "Publishing maven snapshot...\n"

  bash $(dirname $0)/deploy-all.sh \
    "deploy:deploy-file" \
    "HEAD-SNAPSHOT" \
    "-DrepositoryId=sonatype-nexus-snapshots" \
    "-Durl=https://oss.sonatype.org/content/repositories/snapshots" \
    "--settings=$(dirname $0)/settings.xml"

  echo -e "Published maven snapshot"
else
  echo -e "Not publishing snapshot for branch=${$GITHUB_REF}"
fi
