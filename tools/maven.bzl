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

"""Macros to simplify generating maven files.
"""

load("@google_bazel_common//tools/maven:pom_file.bzl", default_pom_file = "pom_file")
load(":maven_info.bzl", "MavenInfo", "collect_maven_info")
load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")
load("@google_bazel_common//tools/jarjar:jarjar.bzl", "jarjar_library")

def pom_file(name, targets, artifact_name, artifact_id, packaging = None, **kwargs):
    default_pom_file(
        name = name,
        targets = targets,
        preferred_group_ids = [
            "com.google.dagger",
            "com.google",
        ],
        template_file = "//tools:pom-template.xml",
        substitutions = {
            "{artifact_name}": artifact_name,
            "{artifact_id}": artifact_id,
            "{packaging}": packaging or "jar",
        },
        excluded_artifacts = ["com.google.auto:auto-common"],
        **kwargs
    )

def gen_maven_artifact(
        name,
        artifact_name,
        artifact_id,
        artifact_target,
        testonly = 0,
        pom_name = "pom",
        packaging = None,
        javadoc_srcs = None,
        javadoc_root_packages = None,
        javadoc_exclude_packages = None,
        javadoc_android_api_level = None,
        deps = None,
        shaded_deps = None,
        shaded_rules = None,
        manifest = None,
        lint_deps = None):
    _gen_maven_artifact(
        name,
        artifact_name,
        artifact_id,
        artifact_target,
        testonly,
        pom_name,
        packaging,
        javadoc_srcs,
        javadoc_root_packages,
        javadoc_exclude_packages,
        javadoc_android_api_level,
        deps,
        shaded_deps,
        shaded_rules,
        manifest,
        lint_deps
    )


def _gen_maven_artifact(
        name,
        artifact_name,
        artifact_id,
        artifact_target,
        testonly,
        pom_name,
        packaging,
        javadoc_srcs,
        javadoc_root_packages,
        javadoc_exclude_packages,
        javadoc_android_api_level,
        deps,
        shaded_deps,
        shaded_rules,
        manifest,
        lint_deps):
    """Generates the files required for a maven artifact.

    This macro generates the following targets:
        * ":pom": The pom file for the given target and deps
        * ":<NAME>": The artifact file for the given target and deps
        * ":<NAME>-src": The sources jar file for the given target and deps
        * ":<NAME>-javadoc": The javadocs jar file for the given target and deps

    This macro also validates a few things. First, it validates that the
    given "target" is a maven artifact (i.e. the "tags" attribute contains
    "maven_coordinates=..."). Second, it calculates the list of transitive
    dependencies of the target that are not owned by another maven artifact,
    and validates that the given "deps" matches exactly.

    Args:
      name: The name associated with the various output targets.
      artifact_target: The target containing the maven_coordinates.
      artifact_name: The name of the maven artifact.
      artifact_id: The id of the maven artifact.
      testonly: True if the jar should be testonly.
      packaging: The packaging of the maven artifact. E.g. "aar"
      pom_name: The name of the pom file (or "pom" if absent).
      javadoc_srcs: The srcs for the javadocs.
      javadoc_root_packages: The root packages for the javadocs.
      javadoc_exclude_packages: The packages to exclude from the javadocs.
      javadoc_android_api_level: The android api level for the javadocs.
      deps: The required deps to include with the target.
      shaded_deps: The shaded deps for the jarjar.
      shaded_rules: The shaded rules for the jarjar.
      manifest: The AndroidManifest.xml to bundle in when packaing an 'aar'.
      lint_deps: The lint targets to be bundled in when packaging an 'aar'.
    """

    _validate_maven_deps(
        name = name + "-validation",
        testonly = 1,
        target = artifact_target,
        deps = deps,
    )

    shaded_deps = shaded_deps or []
    shaded_rules = shaded_rules or []
    artifact_targets = [artifact_target] + (deps or [])
    lint_deps = lint_deps or []

    # META-INF resources files that can be combined by appending lines.
    merge_meta_inf_files = [
        "gradle/incremental.annotation.processors",
    ]

    pom_file(
        name = pom_name,
        testonly = testonly,
        artifact_id = artifact_id,
        artifact_name = artifact_name,
        packaging = packaging,
        targets = artifact_targets,
    )

    if (packaging == "aar"):
        jarjar_library(
            name = name + "-classes",
            testonly = testonly,
            jars = artifact_targets + shaded_deps,
            rules = shaded_rules,
            merge_meta_inf_files = merge_meta_inf_files,
        )
        if lint_deps:
            # jarjar all lint artifacts since an aar only contains a single lint.jar.
            jarjar_library(
                name = name + "-lint",
                jars = lint_deps,
            )
            lint_jar_name = name + "-lint.jar"
        else:
            lint_jar_name = None

        _package_android_library(
            name = name + "-android-lib",
            manifest = manifest,
            classesJar = name + "-classes.jar",
            lintJar = lint_jar_name,
        )

        # Copy intermediate outputs to final one.
        native.genrule(
            name = name,
            srcs = [name + "-android-lib"],
            outs = [name + ".aar"],
            cmd = "cp $< $@",
        )
    else:
        jarjar_library(
            name = name,
            testonly = testonly,
            jars = artifact_targets + shaded_deps,
            rules = shaded_rules,
            merge_meta_inf_files = merge_meta_inf_files,
        )

    jarjar_library(
        name = name + "-src",
        testonly = testonly,
        jars = [_src_jar(dep) for dep in artifact_targets],
        merge_meta_inf_files = merge_meta_inf_files,
    )

    if javadoc_srcs != None:
        javadoc_library(
            name = name + "-javadoc",
            srcs = javadoc_srcs,
            testonly = testonly,
            root_packages = javadoc_root_packages,
            exclude_packages = javadoc_exclude_packages,
            android_api_level = javadoc_android_api_level,
            deps = artifact_targets,
        )
    else:
        # Build an empty javadoc because Sonatype requires javadocs
        # even if the jar is empty.
        # https://central.sonatype.org/pages/requirements.html#supply-javadoc-and-sources
        native.java_binary(
            name = name + "-javadoc",
        )

def _src_jar(target):
    if target.startswith(":"):
        target = Label("//" + native.package_name() + target)
    else:
        target = Label(target)
    return "//%s:lib%s-src.jar" % (target.package, target.name)

def _validate_maven_deps_impl(ctx):
    """Validates the given Maven target and deps

    Validates that the given "target" is a maven artifact (i.e. the "tags"
    attribute contains "maven_coordinates=..."). Second, it calculates the
    list of transitive dependencies of the target that are not owned by
    another maven artifact, and validates that the given "deps" matches
    exactly.
    """
    target = ctx.attr.target
    if not target[MavenInfo].artifact:
        fail("\t[Error]: %s is not a maven artifact" % target.label)

    deps = [dep.label for dep in getattr(ctx.attr, "deps", [])]

    all_transitive_deps = target[MavenInfo].all_transitive_deps.to_list()
    maven_transitive_deps = target[MavenInfo].maven_transitive_deps.to_list()
    required_deps = [dep for dep in all_transitive_deps if dep not in maven_transitive_deps]

    missing_deps = [str(dep) for dep in required_deps if dep not in deps]
    if missing_deps:
        fail("\t[Error]: Found missing deps: \n\t\t" + "\n\t\t".join(missing_deps))

    extra_deps = [str(dep) for dep in deps if dep not in required_deps]
    if extra_deps:
        fail("\t[Error]: Found extra deps: \n\t\t" + "\n\t\t".join(extra_deps))

_validate_maven_deps = rule(
    implementation = _validate_maven_deps_impl,
    attrs = {
        "target": attr.label(
            doc = "The target to generate a maven artifact for.",
            aspects = [collect_maven_info],
            mandatory = True,
        ),
        "deps": attr.label_list(
            doc = "The required dependencies of the target, if any.",
        ),
    },
)

def _package_android_library_impl(ctx):
    """A very, very simple Android Library (aar) packaging rule.

    This rule only support packaging simple android libraries. No resources
    support, assets, extra libs, jni, nor proguard. This rule is needed because
    there is no 'JarJar equivalent' for AARs and some of our artifacts are
    composed of sources spread across multiple android_library targets.

    See: https://developer.android.com/studio/projects/android-library.html#aar-contents
    """
    inputs = [ctx.file.manifest, ctx.file.classesJar]
    if ctx.file.lintJar:
        inputs.append(ctx.file.lintJar)

    ctx.actions.run_shell(
        inputs = inputs,
        outputs = [ctx.outputs.aar],
        command = """
            TMPDIR="$(mktemp -d)"
            cp {manifest} $TMPDIR/AndroidManifest.xml
            cp {classesJar} $TMPDIR/classes.jar
            if [[ -a {lintJar} ]]; then
                cp {lintJar} $TMPDIR/lint.jar
            fi
            touch $TMPDIR/R.txt
            zip -j {outputFile} $TMPDIR/*
            """.format(
            manifest = ctx.file.manifest.path,
            classesJar = ctx.file.classesJar.path,
            lintJar = ctx.file.lintJar.path if ctx.file.lintJar else "none",
            outputFile = ctx.outputs.aar.path,
        ),
    )

_package_android_library = rule(
    implementation = _package_android_library_impl,
    attrs = {
        "manifest": attr.label(
            doc = "The AndroidManifest.xml file.",
            allow_single_file = True,
            mandatory = True,
        ),
        "classesJar": attr.label(
            doc = "The classes.jar file.",
            allow_single_file = True,
            mandatory = True,
        ),
        "lintJar": attr.label(
            doc = "The lint.jar file.",
            allow_single_file = True,
            mandatory = False,
        ),
    },
    outputs = {
        "aar": "%{name}.aar",
    },
)
