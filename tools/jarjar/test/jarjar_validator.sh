#!/bin/bash
if diff <(unzip -p "$1" "META-INF/$2") "$3"; then
  echo Passed
  exit 0
else
  echo Failed
  exit 1
fi
