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

_EXTERNAL_JAVADOC_LINKS = [
    "https://docs.oracle.com/javase/8/docs/api/",
    "https://developer.android.com/reference/",
    "https://google.github.io/guava/releases/21.0/api/docs/",
    "https://docs.oracle.com/javaee/7/api/",
]

def _check_non_empty(value, name):
  if not value:
    fail("%s must be non-empty" % name)

def _android_jar(android_api_level):
  if android_api_level == -1:
    return None
  return Label("@androidsdk//:platforms/android-%s/android.jar" % android_api_level)

def _javadoc_libary(ctx):
  _check_non_empty(ctx.attr.root_packages, "root_packages")

  inputs = []
  for src_attr in ctx.attr.srcs:
    inputs.extend(src_attr.files.to_list())

  classpath = depset()
  for dep in ctx.attr.deps:
    for transitive_dep in dep.java.transitive_deps:
      classpath += [transitive_dep]
  if ctx.attr._android_jar:
    classpath += ctx.attr._android_jar.files

  inputs += classpath.to_list()

  include_packages = ":".join(ctx.attr.root_packages)
  javadoc_command = [
      ctx.file._javadoc_binary.path,
      '-sourcepath $(find * -type d -name "*java" -print0 | tr "\\0" :)',
      include_packages,
      "-use",
      "-subpackages", include_packages,
      "-encoding UTF8",
      "-classpath", ":".join([jar.path for jar in classpath.to_list()]),
      "-notimestamp",
      '-bottom "Copyright &copy; 2012&ndash;2017 The Dagger Authors. All rights reserved."',
      "-d tmp",
      "-Xdoclint:-missing",
      "-quiet",
  ]

  if ctx.attr.doctitle:
    javadoc_command.append('-doctitle "%s"' % ctx.attr.doctitle)

  if ctx.attr.exclude_packages:
    javadoc_command.append("-exclude %s" % ":".join(ctx.attr.exclude_packages))

  for link in _EXTERNAL_JAVADOC_LINKS:
    javadoc_command.append("-linkoffline {0} {0}".format(link))

  jar_command = "%s cf %s -C tmp ." % (ctx.file._jar_binary.path, ctx.outputs.jar.path)

  ctx.action(
      inputs = inputs + ctx.files._jdk,
      command = "%s && %s" % (" ".join(javadoc_command), jar_command),
      outputs = [ctx.outputs.jar])

javadoc_library = rule(
    attrs = {
        "srcs": attr.label_list(allow_files = True),
        "deps": attr.label_list(),
        "doctitle": attr.string(default = ""),
        "root_packages": attr.string_list(),
        "exclude_packages": attr.string_list(),
        "android_api_level": attr.int(default = -1),
        "_android_jar": attr.label(
            default = _android_jar,
            allow_single_file = True,
        ),
        "_javadoc_binary": attr.label(
            default = Label("@local_jdk//:bin/javadoc"),
            allow_single_file = True,
        ),
        "_jar_binary": attr.label(
            default = Label("@local_jdk//:bin/jar"),
            allow_single_file = True,
        ),
        "_jdk": attr.label(
            default = Label("@local_jdk//:jdk-default"),
            allow_files = True,
        ),
    },
    outputs = {"jar": "%{name}.jar"},
    implementation = _javadoc_libary,
)
"""
Generates a Javadoc jar path/to/target/<name>.jar.

Arguments:
  srcs: source files to process
  deps: targets that contain references to other types referenced in Javadoc. This can be the
      java_library/android_library target(s) for the same sources
  root_packages: Java packages to include in generated Javadoc. Any subpackages not listed in
      exclude_packages will be included as well
  exclude_packages: Java packages to exclude from generated Javadoc
  android_api_level: If Android APIs are used, the API level to compile against to generate
      Javadoc
  doctitle: title for Javadoc's index.html. See javadoc -doctitle
"""
