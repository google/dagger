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

def _check_non_empty(value, name):
  if not value:
    fail("%s must be non-empty" % name)

def javadoc_library(
    name,
    srcs = [],
    deps = [],
    root_packages = [],
    exclude_packages = [],
    android_api_level = None,
    doctitle = None):
  """
  Generates a Javadoc jar $GENDIR/path/to/target/<name>.jar.

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
  _check_non_empty(srcs, "srcs")
  _check_non_empty(root_packages, "root_packages")

  exclude_packages_option = ""
  if exclude_packages:
    exclude_packages_option = "-exclude " + ":".join(exclude_packages)

  deploy_jar_name = name + "_deploy_jar_for_javadoc"
  deploy_jar_label = ":%s_deploy.jar" % deploy_jar_name

  genrule_srcs = srcs + deps + [
      deploy_jar_label,
      "//tools:javadoc_library_impl"
  ]

  bootclasspath_option = ""
  if android_api_level:
    native.android_binary(
        name = deploy_jar_name,
        deps = deps,
        manifest = "//tools:AndroidManifest.xml",
        custom_package = "dummy",
        tags = ["manual"],
    )
    android_jar = "@androidsdk//:platforms/android-%s/android.jar" % android_api_level
    genrule_srcs.append(android_jar)
    bootclasspath_option = "-bootclasspath $(location %s)" % android_jar
  else:
    native.java_binary(
        name = deploy_jar_name,
        runtime_deps = deps,
        main_class = "dummy",
        tags = ["manual"],
    )

  doctitle_option = ""
  if doctitle:
    doctitle_option = "-doctitle \"%s\"" % doctitle

  external_javadoc_links = [
      "https://docs.oracle.com/javase/8/docs/api/",
      "https://developer.android.com/reference/",
      "https://google.github.io/guava/releases/21.0/api/docs/",
      "https://docs.oracle.com/javaee/7/api/"
  ]

  linkoffline_options = ' '.join(
      ["-linkoffline {0} {0}".format(url) for url in external_javadoc_links])

  native.genrule(
      name = name,
      srcs = genrule_srcs,
      outs = [name + ".jar"],
      tools = ["@local_jdk//:bin/javadoc", "@local_jdk//:bin/jar"],
      cmd = """
$(location //tools:javadoc_library_impl) \
  "$(location @local_jdk//:bin/javadoc)" "$(location @local_jdk//:bin/jar)" "$@" \
  "{include_packages}" "{exclude_packages}" "$(location {deploy_jar})" "{bootclasspath}" \
  "{linkoffline}" "{doctitle}" "$(@D)"
      """.format(
          deploy_jar=deploy_jar_label,
          include_packages=":".join(root_packages),
          exclude_packages=exclude_packages_option,
          bootclasspath=bootclasspath_option,
          doctitle=doctitle_option,
          linkoffline=linkoffline_options)
  )
