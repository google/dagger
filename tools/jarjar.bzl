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

def jarjar_deps():
  """
  Deps that are necessary to build jarjar_binary() but aren't used in the regular build process for
  Dagger, so they are organized separately from the workspace file.
  """
  native.maven_jar(
      name = "org_codehaus_plexus_plexus_utils",
      artifact = "org.codehaus.plexus:plexus-utils:3.0.20",
      sha1 = "e121ed37af8ee3928952f6d8a303de24e019aab0",
  )

  native.maven_jar(
      name = "org_eclipse_sisu_org_eclipse_sisu_plexus",
      artifact = "org.eclipse.sisu:org.eclipse.sisu.plexus:0.3.0",
      sha1 = "3f53953a998d03b9b0f7d5098f63119e434af0ef",
  )

  native.maven_jar(
      name = "org_apache_ant_ant_launcher",
      artifact = "org.apache.ant:ant-launcher:1.9.6",
      sha1 = "d75dd4c39ba06401f20e7afffb861d268baec6bc",
  )

  native.maven_jar(
      name = "org_apache_maven_maven_plugin_api",
      artifact = "org.apache.maven:maven-plugin-api:3.3.3",
      sha1 = "3b78a7e40707be313c4d5449ba514c9747e1c731",
  )

  native.maven_jar(
      name = "org_eclipse_sisu_org_eclipse_sisu_inject",
      artifact = "org.eclipse.sisu:org.eclipse.sisu.inject:0.3.0",
      sha1 = "6c25adce9ca9af097728ed57834e8807e3b6e2b5",
  )

  native.maven_jar(
      name = "org_ow2_asm_asm",
      artifact = "org.ow2.asm:asm:5.0.4",
      sha1 = "0da08b8cce7bbf903602a25a3a163ae252435795",
  )

  native.maven_jar(
      name = "org_ow2_asm_asm_tree",
      artifact = "org.ow2.asm:asm-tree:5.0.4",
      sha1 = "396ce0c07ba2b481f25a70195c7c94922f0d1b0b",
  )

  native.maven_jar(
      name = "javax_enterprise_cdi_api",
      artifact = "javax.enterprise:cdi-api:1.0",
      sha1 = "44c453f60909dfc223552ace63e05c694215156b",
  )

  native.maven_jar(
      name = "org_ow2_asm_asm_commons",
      artifact = "org.ow2.asm:asm-commons:5.0.4",
      sha1 = "5a556786086c23cd689a0328f8519db93821c04c",
  )

  native.maven_jar(
      name = "org_apache_maven_maven_model",
      artifact = "org.apache.maven:maven-model:3.3.3",
      sha1 = "73ba535c2e3a1381aeab131598010b3a723d4b47",
  )

  native.maven_jar(
      name = "org_apache_maven_maven_artifact",
      artifact = "org.apache.maven:maven-artifact:3.3.3",
      sha1 = "d9f439dfef726e54eebb390ff38dd27356901528",
  )

  native.maven_jar(
      name = "org_pantsbuild_jarjar",
      artifact = "org.pantsbuild:jarjar:1.6.3",
      sha1 = "cf54d4b142f5409c394095181c8d308a81869622",
  )

  native.maven_jar(
      name = "org_codehaus_plexus_plexus_classworlds",
      artifact = "org.codehaus.plexus:plexus-classworlds:2.5.2",
      sha1 = "4abb111bfdace5b8167db4c0ef74644f3f88f142",
  )

  native.maven_jar(
      name = "org_apache_ant_ant",
      artifact = "org.apache.ant:ant:1.9.6",
      sha1 = "80e2063b01bab3c79c2d84e4ed5e73868394c85a",
  )

  native.maven_jar(
      name = "org_codehaus_plexus_plexus_component_annotations",
      artifact = "org.codehaus.plexus:plexus-component-annotations:1.5.5",
      sha1 = "c72f2660d0cbed24246ddb55d7fdc4f7374d2078",
  )

def jarjar_library(name, deps, rules_file):
  """
  Combines `deps` into one jar file named <name>.jar and jarjar with the rules specified in
  `rules_file`.

  See: https://github.com/pantsbuild/jarjar
  """
  native.genrule(
      name = name,
      srcs = deps + [
          rules_file,
          "//tools:jarjar_deploy.jar",
      ],
      tools = [
          "//tools:jarjar_library_impl",
          "@local_jdk//:bin/jar",
          "@local_jdk//:bin/java",
          "@local_jdk//:jre",
      ],
      outs = [name + ".jar"],
      cmd = """
      export JAVA_HOME=$(JAVABASE)
      $(location //tools:jarjar_library_impl) $@ "{deps}" {rules} \
        $(location //tools:jarjar_deploy.jar) \
        $$(readlink $(location @local_jdk//:bin/jar)) \
        $$(readlink $(location @local_jdk//:bin/java)) \
        $(@D)
      """.format(
          deps=" ".join(["$(location %s)" % dep for dep in deps]),
          rules="$(location %s)" % rules_file),
  )
