#!/usr/bin/env sh

JAVADOC_BINARY=$1
JAR_BINARY=$2
OUTPUT=$3
INCLUDE_PACKAGES=$4
EXCLUDE_PACKAGES=$5
DEPLOY_JAR=$6
BOOTCLASSPATH=$7
LINKOFFLINE=$8
DOCTITLE=$9
TEMPDIR=$10/tmp

mkdir -p $TEMPDIR

$JAVADOC_BINARY \
  -sourcepath $(find * -type d -regex ".*java" -print0 | tr '\0' :) \
  $INCLUDE_PACKAGES \
  -use \
  -subpackages $INCLUDE_PACKAGES \
  -encoding UTF8 \
  $EXCLUDE_PACKAGES \
  -classpath $DEPLOY_JAR \
  $BOOTCLASSPATH \
  $LINKOFFLINE \
  -notimestamp \
  $DOCTITLE \
  -bottom "Copyright &copy; 2012–2017 The Dagger Authors. All rights reserved." \
  -d $TEMPDIR

$JAR_BINARY cvf $OUTPUT -C $TEMPDIR $TEMPDIR/*
rm -rf $TEMPDIR

