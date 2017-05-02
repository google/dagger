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

import textwrap

DEP_BLOCK = """
<dependency>
  <groupId>%s</groupId>
  <artifactId>%s</artifactId>
  <version>%s</version>
</dependency>
""".strip()

CLASSIFIER_DEP_BLOCK = """
<dependency>
  <groupId>%s</groupId>
  <artifactId>%s</artifactId>
  <version>%s</version>
  <type>%s</type>
  <classifier>%s</classifier>
</dependency>
""".strip()


def maven_dependency_xml(artifact_string):
  if artifact_string.count(':') is 2:
    format_string = DEP_BLOCK
  else:
    format_string = CLASSIFIER_DEP_BLOCK
  formatted = format_string % tuple(artifact_string.split(':'))
  return '\n'.join(['    %s' %x for x in formatted.split('\n')])

POM_OUTLINE = """<?xml version="1.0" encoding="UTF-8"?>
<!--
 Copyright (C) 2012 The Dagger Authors.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.sonatype.oss</groupId>
    <artifactId>oss-parent</artifactId>
    <version>7</version>
  </parent>

  <groupId>{group}</groupId>
  <artifactId>{artifact}</artifactId>
  <name>{name}</name>
  <version>{version}</version>
  <description>A fast dependency injector for Android and Java.</description>
  <url>https://github.com/google/dagger</url>
  <packaging>{packaging}</packaging>

  <scm>
    <url>http://github.com/google/dagger/</url>
    <connection>scm:git:git://github.com/google/dagger.git</connection>
    <developerConnection>scm:git:ssh://git@github.com/google/dagger.git</developerConnection>
    <tag>HEAD</tag>
  </scm>

  <issueManagement>
    <system>GitHub Issues</system>
    <url>http://github.com/google/dagger/issues</url>
  </issueManagement>

  <licenses>
    <license>
      <name>Apache 2.0</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
    </license>
  </licenses>

  <organization>
    <name>Google, Inc.</name>
    <url>http://www.google.com</url>
  </organization>

  <dependencies>
{deps}
  </dependencies>
</project>
"""

def generate_pom(artifact_string, metadata, deps, version):
  group, artifact, version = artifact_string.split(':')

  deps = deps + metadata.get('manual_dependencies', [])

  return POM_OUTLINE.format(
      group=group,
      artifact=artifact,
      name=metadata['name'],
      version=version,
      packaging=metadata.get('packaging', 'jar'),
      deps='\n'.join(map(maven_dependency_xml, deps)))
