#!/bin/bash
if [ $# -lt 1 ]; then
  echo "usage $0 <ssl-key> [<param> ...]"
  exit 1;
fi
key=$1
shift

#validate key
keystatus=$(gpg --list-keys | grep ${key} | awk '{print $1}')
if [ "${keystatus}" != "pub" ]; then
  echo "Could not find public key with label ${key}"
  echo -n "Available keys from: "
  gpg --list-keys | grep --invert-match '^sub'

  exit 64
fi

mvn "$@" -P '!examples' -P sonatype-oss-release \
    -Dgpg.skip=false -Dgpg.keyname=${key} \
    clean site:jar deploy
