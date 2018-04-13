# Copyright (C) 2018 The Dagger Authors.
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
"""Skylark rules to make publishing Maven artifacts simpler.
"""

MavenInfo = provider(
    fields = {
        "coordinates": "The maven coordinates of the target",
        "coordinates_from_deps": """The maven coordinates from the deps of the target. Only
                                 considers the top-level deps and not transitive deps""",
        "coordinates_from_exports": """The maven coordinates from the exports of this target and all
                                    exports that they transitively include.""",
    },
)

_MAVEN_COORDINATES_PREFIX = "maven_coordinates="

def _collect_maven_info_impl(target, ctx):
  coordinates = []
  if hasattr(ctx.rule.attr, "tags"):
    for tag in ctx.rule.attr.tags:
      if tag.startswith(_MAVEN_COORDINATES_PREFIX):
        coordinates.append(tag[len(_MAVEN_COORDINATES_PREFIX):])
      if tag == "maven:compile_only" or tag == "maven:shaded":
        return [MavenInfo(
            coordinates = depset(),
            coordinates_from_deps = depset(),
            coordinates_from_exports = depset()
        )]

  coordinates_from_exports = []
  for export in getattr(ctx.rule.attr, 'exports', []):
    if MavenInfo in export:
      coordinates_from_exports += export[MavenInfo].coordinates.to_list()
      coordinates_from_exports += export[MavenInfo].coordinates_from_exports.to_list()

  coordinates_from_deps = []
  for dep in getattr(ctx.rule.attr, 'deps', []):
    if MavenInfo in dep:
      coordinates_from_deps += dep[MavenInfo].coordinates.to_list()
      coordinates_from_deps += dep[MavenInfo].coordinates_from_exports.to_list()

  return [MavenInfo(
      coordinates = depset(coordinates),
      coordinates_from_deps = depset(coordinates_from_deps),
      coordinates_from_exports = depset(coordinates_from_exports),
  )]

_collect_maven_info = aspect(
    attr_aspects = [
        "deps",
        "exports",
    ],
    implementation = _collect_maven_info_impl,
)

"""Collects the Maven information for targets, their dependencies, and their transitive exports.
"""

def _replace_bazel_deps_impl(ctx):
  template_file = ctx.file.template_file
  deps_xml = ctx.file.deps_xml
  pom_file = ctx.outputs.pom_file
  ctx.actions.run(
      inputs = [template_file, deps_xml],
      executable = ctx.executable._replace_bazel_deps,
      arguments = [template_file.path, deps_xml.path, pom_file.path],
      outputs = [pom_file],
  )

_replace_bazel_deps = rule(
    attrs = {
        "pom_file": attr.output(mandatory = True),
        "template_file": attr.label(
            single_file = True,
            allow_files = True,
        ),
        "deps_xml": attr.label(
            single_file = True,
            allow_files = True,
        ),
        "_replace_bazel_deps": attr.label(
            executable = True,
            cfg = "host",
            allow_files = True,
            default = Label("//tools:replace_bazel_deps"),
        ),
    },
    implementation = _replace_bazel_deps_impl,
)

def _prefix_index_of(item, prefixes):
  """Returns the index of the first value in `prefixes` that is a prefix of `item.
  """
  for index, prefix in enumerate(prefixes):
    if item.startswith(prefix):
      return index
  return len(prefixes)

def _sort_artifacts(artifacts, prefixes):
  """Sorts `artifacts`, preferring group ids that appear earlier in `prefixes`, using a regular
  string comparison to break ties.

  Values in `prefixes` do not need to be complete group ids. For example, passing `prefixes =
  ['io.bazel']` will match `io.bazel.rules:rules-artifact:1.0`. If multiple prefixes match an
  artifact, the first one in `prefixes` will be used.

  _Implementation note_: Skylark does not support passing a comparator function to the `sorted()`
  builtin, so this constructs a list of tuples with elements:
    - `[0]` = an integer corresponding to the index in `prefixes` that matches the artifact
    - `[1]` = parts of the complete artifact, split on `:`. This is used as a tiebreaker when
      multilple artifacts have the same index referenced in `[0]`. The individual parts are used so
      that individual artifacts in the same group are sorted correctly - if just the string is used,
      the colon that separates the artifact name from the version will sort lower than a longer
      name. For example:
      -  `com.google.dagger:dagger:1
      -  `com.google.dagger:dagger-producers:1
      "dagger:" sorts lower than "dagger-".
    - `[2]` = the complete artifact. this is a convenience so that after sorting, the artifact can
    be returned.

  The `sorted` builtin will first compare the index element and if it needs a tiebreaker, will
  recursively compare the contents of the second element.
  """
  indexed = []
  for artifact in artifacts:
    parts = artifact.split(":")
    indexed.append((_prefix_index_of(parts[0], prefixes), parts, artifact))

  return [x[-1] for x in sorted(indexed)]

DEP_BLOCK = """
<dependency>
  <groupId>{0}</groupId>
  <artifactId>{1}</artifactId>
  <version>{2}</version>
</dependency>
""".strip()

CLASSIFIER_DEP_BLOCK = """
<dependency>
  <groupId>{0}</groupId>
  <artifactId>{1}</artifactId>
  <version>{2}</version>
  <type>{3}</type>
  <classifier>{4}</classifier>
</dependency>
""".strip()

def _deps_xml_impl(ctx):
  mvn_deps = []
  for target in ctx.attr.targets:
    mvn_deps += target[MavenInfo].coordinates_from_deps.to_list()
    mvn_deps += target[MavenInfo].coordinates_from_exports.to_list()

  formatted_deps = []
  for dep in _sort_artifacts(depset(mvn_deps), ctx.attr.preferred_group_ids):
    parts = dep.split(":")
    if len(parts) == 3:
      template = DEP_BLOCK
    elif len(parts) == 5:
      template = CLASSIFIER_DEP_BLOCK
    else:
      fail("Unknown dependency format: %s" % dep)

    formatted_deps.append(template.format(*parts))

  ctx.actions.write(
      content = '\n'.join(formatted_deps),
      output = ctx.outputs.output_file,
  )

_deps_xml = rule(
    attrs = {
        "targets": attr.label_list(
            mandatory = True,
            aspects = [_collect_maven_info],
        ),
        "output_file": attr.output(mandatory = False),
        "preferred_group_ids": attr.string_list(),
    },
    implementation = _deps_xml_impl,
)

# dpb@ - requesting your thoughts here:
#
# DO NOT SUBMIT: The only thing stopping this from being a Skylark rule and not a macro is that
# _replace_bazel_deps calls a python binary to attempt to format the <dependencies> block. This is
# partially a remnant from how I first implemented this, before I used a Skylark aspect. If we
# instead ignore the template_file indentation and provide a "indentation" parameter that can be
# configured, we can turn this into one rule that calls ctx.actions.expand_template once. I think
# that's preferable, but before I do so I wanted to get your opinions on it.
def pom_file(name, targets, template_file, preferred_group_ids=None):
  pom_deps_file = name + ".depsxml"
  _deps_xml(
      name = name + "_deps_xml",
      targets = targets,
      output_file = pom_deps_file,
      preferred_group_ids = preferred_group_ids,
  )

  _replace_bazel_deps(
      name = name + "_replace_bazel_deps",
      pom_file = name,
      template_file = template_file,
      deps_xml = pom_deps_file,
  )

def _dagger_pom_template_impl(ctx):
  ctx.actions.expand_template(
      template = ctx.file._base_template,
      output = ctx.outputs.template,
      substitutions = {
          "{packaging}": ctx.attr.packaging,
          "{artifact_id}": ctx.attr.artifact_id,
          "{artifact_name}": ctx.attr.artifact_name,
          # TODO(ronshapiro): should this be part of the general pom_file rule? It seems like
          # something that other libraries will likely want.
          "{version}": ctx.var.get("maven_version", "LOCAL-SNAPSHOT")
      },
  )

_dagger_pom_template = rule(
    attrs = {
        "artifact_id": attr.string(mandatory = True),
        "artifact_name": attr.string(mandatory = True),
        "packaging": attr.string(default = "jar"),
        "_base_template": attr.label(
            default = Label("//tools:pom-template.xml"),
            allow_files = True,
            single_file = True,
        ),
    },
    outputs = {"template": "%{name}.xml"},
    implementation = _dagger_pom_template_impl,
)

"""Takes a base pom.xml template for all Dagger poms and adds in artifact-specific information that
isn't covered in `pom_file`. The version of the poms defaults to `LOCAL-SNAPSHOT` but can be
overridden with  the `--define=maven_version=<version>` flag to `bazel build`.

Args:
  artifact_id: the `project.artifactId` of the pom
  artifact_name: the `project.name` of the pom
  packaging: the `project.packaging` of the pom. Defaults to `jar`.
"""

def dagger_pom_file(name, artifact_id, artifact_name, targets, packaging=None):
  """Generates a pom.xml file for a Dagger artifact.

  This is separate from `pom_file` as we intend to move that to a shared location for other
  libraries that build with bazel.

  Args:
    artifact_id: the `project.artifactId` of the pom
    artifact_name: the `project.name` of the pom
    targets: the `*_library` targets that this pom.xml file represents
    packaging: the `project.packaging` of the pom. Defaults to `jar`
  """
  template_name = name + "_template"
  _dagger_pom_template(
      name = template_name,
      artifact_id = artifact_id,
      artifact_name = artifact_name,
      packaging = packaging,
  )

  pom_file(
      name = name,
      targets = targets,
      template_file = ":" + template_name,
      preferred_group_ids = [
          "com.google.dagger",
          "com.google"
      ],
  )
