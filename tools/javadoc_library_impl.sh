#!/usr/bin/env sh

# Copyright (C) 2017 The Dagger Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

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

