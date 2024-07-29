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

load("@rules_java//java:defs.bzl", "java_binary")
load("//tools/jarjar:jarjar.bzl", "jarjar_library")
load("//tools/javadoc:javadoc.bzl", "javadoc_library")
load(":maven_info.bzl", "MavenInfo", "collect_maven_info")
load(":pom_file.bzl", "pom_file")

SHADED_MAVEN_DEPS = [
    "com.google.auto:auto-common",
    "org.jetbrains.kotlinx:kotlinx-metadata-jvm",
]

def dagger_pom_file(name, targets, artifact_name, artifact_id, packaging = None, **kwargs):
    pom_file(
        name = name,
        targets = targets,
        preferred_group_ids = [
            "com.google.dagger",
            "com.google",
        ],
        template_file = "//tools/maven:pom-template.xml",
        substitutions = {
            "{artifact_name}": artifact_name,
            "{artifact_id}": artifact_id,
            "{packaging}": packaging or "jar",
        },
        # NOTE: The shaded maven dependencies are excluded from every Dagger pom file.
        # Thus, if a Dagger artifact needs the dependencies it must jarjar the dependency
        # into the artifact itself using the gen_maven_artifact.shaded_deps or get it from
        # a transitive Dagger artifact as a dependency. In addition, the artifact must add
        # the shade rules in the deploy scripts, e.g. deploy-dagger.sh.
        excluded_artifacts = SHADED_MAVEN_DEPS,
        **kwargs
    )

def gen_maven_artifact(
        name,
        artifact_name,
        artifact_coordinates,
        artifact_target,
        artifact_target_libs = None,
        artifact_target_maven_deps = None,
        artifact_target_maven_deps_banned = None,
        testonly = 0,
        pom_name = "pom",
        packaging = None,
        javadoc_srcs = None,
        javadoc_root_packages = None,
        javadoc_exclude_packages = None,
        javadoc_android_api_level = None,
        shaded_deps = None,
        manifest = None,
        lint_deps = None,
        proguard_and_r8_specs = None,
        r8_specs = None,
        proguard_specs = None):
    _gen_maven_artifact(
        name,
        artifact_name,
        artifact_coordinates,
        artifact_target,
        artifact_target_libs,
        artifact_target_maven_deps,
        artifact_target_maven_deps_banned,
        testonly,
        pom_name,
        packaging,
        javadoc_srcs,
        javadoc_root_packages,
        javadoc_exclude_packages,
        javadoc_android_api_level,
        shaded_deps,
        manifest,
        lint_deps,
        proguard_and_r8_specs,
        r8_specs,
        proguard_specs
    )

def _gen_maven_artifact(
        name,
        artifact_name,
        artifact_coordinates,
        artifact_target,
        artifact_target_libs,
        artifact_target_maven_deps,
        artifact_target_maven_deps_banned,
        testonly,
        pom_name,
        packaging,
        javadoc_srcs,
        javadoc_root_packages,
        javadoc_exclude_packages,
        javadoc_android_api_level,
        shaded_deps,
        manifest,
        lint_deps,
        proguard_and_r8_specs,
        r8_specs,
        proguard_specs):
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
      artifact_coordinates: The coordinates of the maven artifact in the
                            form: "<group_id>:<artifact_id>:<version>"
      artifact_target_libs: The set of transitive libraries of the target.
      artifact_target_maven_deps: The required maven deps of the target.
      artifact_target_maven_deps_banned: The banned maven deps of the target.
      testonly: True if the jar should be testonly.
      packaging: The packaging of the maven artifact. E.g. "aar"
      pom_name: The name of the pom file (or "pom" if absent).
      javadoc_srcs: The srcs for the javadocs.
      javadoc_root_packages: The root packages for the javadocs.
      javadoc_exclude_packages: The packages to exclude from the javadocs.
      javadoc_android_api_level: The android api level for the javadocs.
      shaded_deps: The shaded deps for the jarjar.
      manifest: The AndroidManifest.xml to bundle in when packaing an 'aar'.
      lint_deps: The lint targets to be bundled in when packaging an 'aar'.
      proguard_and_r8_specs: The proguard spec files to be bundled in when
                             packaging an 'aar', which will be applied in
                             both r8 and proguard.
      r8_specs: The proguard spec files to be used only for r8 when packaging an 'jar'.
      proguard_specs: The proguard spec files to be used only for proguard not r8 when
                           packaging an 'jar'.
    """

    _validate_maven_deps(
        name = name + "-validation",
        testonly = 1,
        target = artifact_target,
        expected_artifact = artifact_coordinates,
        expected_libs = artifact_target_libs,
        expected_maven_deps = artifact_target_maven_deps,
        banned_maven_deps = artifact_target_maven_deps_banned,
    )

    shaded_deps = shaded_deps or []
    artifact_targets = [artifact_target] + (artifact_target_libs or [])
    lint_deps = lint_deps or []

    # META-INF resources files that can be combined by appending lines.
    merge_meta_inf_files = [
        "gradle/incremental.annotation.processors",
    ]

    artifact_id = artifact_coordinates.split(":")[1]
    dagger_pom_file(
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

        if proguard_and_r8_specs:
            # Concatenate all proguard rules since an aar only contains a single proguard.txt
            native.genrule(
                name = name + "-proguard",
                srcs = proguard_and_r8_specs,
                outs = [name + "-proguard.txt"],
                cmd = "cat $(SRCS) > $@",
            )
            proguard_file = name + "-proguard.txt"
        else:
            proguard_file = None

        _package_android_library(
            name = name + "-android-lib",
            manifest = manifest,
            classesJar = name + "-classes.jar",
            lintJar = lint_jar_name,
            proguardSpec = proguard_file,
        )

        # Copy intermediate outputs to final one.
        native.genrule(
            name = name,
            srcs = [name + "-android-lib"],
            outs = [name + ".aar"],
            cmd = "cp $< $@",
        )
    else:
        # (TODO/322873492) add support for passing in general proguard rule.
        if r8_specs:
            # Concatenate all r8 rules.
            native.genrule(
                name = name + "-r8",
                srcs = r8_specs,
                outs = [name + "-r8.txt"],
                cmd = "cat $(SRCS) > $@",
            )
            r8_file = name + "-r8.txt"
        else:
            r8_file = None

        if proguard_specs:
            # Concatenate all proguard only rules.
            native.genrule(
                name = name + "-proguard-only",
                srcs = proguard_specs,
                outs = [name + "-proguard-only.txt"],
                cmd = "cat $(SRCS) > $@",
            )
            proguard_only_file = name + "-proguard-only.txt"
        else:
            proguard_only_file = None

        jarjar_library(
            name = name + "-classes",
            testonly = testonly,
            jars = artifact_targets + shaded_deps,
            merge_meta_inf_files = merge_meta_inf_files,
        )
        jar_name = name + "-classes.jar"

        # Include r8 and proguard rules to dagger jar if there is one.
        _package_r8_and_proguard_rule(
            name = name,
            artifactJar = jar_name,
            r8Spec = r8_file,
            proguardSpec = proguard_only_file,
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
        java_binary(
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
    artifact = target[MavenInfo].artifact
    if not artifact:
        fail("\t[Error]: %s is not a maven artifact" % target.label)

    if artifact != ctx.attr.expected_artifact:
        fail(
            "\t[Error]: %s expected artifact, %s, but was: %s" % (
                target.label,
                ctx.attr.expected_artifact,
                artifact,
            ),
        )

    all_transitive_deps = target[MavenInfo].all_transitive_deps.to_list()
    maven_nearest_artifacts = target[MavenInfo].maven_nearest_artifacts.to_list()
    maven_transitive_deps = target[MavenInfo].maven_transitive_deps.to_list()

    expected_libs = [dep.label for dep in getattr(ctx.attr, "expected_libs", [])]
    actual_libs = [dep for dep in all_transitive_deps if dep not in maven_transitive_deps]
    _validate_list("artifact_target_libs", actual_libs, expected_libs)

    expected_maven_deps = [dep for dep in getattr(ctx.attr, "expected_maven_deps", [])]
    actual_maven_deps = [_strip_artifact_version(artifact) for artifact in maven_nearest_artifacts]
    _validate_list(
        "artifact_target_maven_deps",
        # Exclude shaded maven deps from this list since they're not actual dependencies.
        [dep for dep in actual_maven_deps if dep not in SHADED_MAVEN_DEPS],
        expected_maven_deps,
        ctx.attr.banned_maven_deps,
    )

def _validate_list(name, actual_list, expected_list, banned_list = []):
    missing = sorted(['"{}",'.format(x) for x in actual_list if x not in expected_list])
    if missing:
        fail("\t[Error]: Found missing {}: \n\t\t".format(name) + "\n\t\t".join(missing))

    extra = sorted(['"{}",'.format(x) for x in expected_list if x not in actual_list])
    if extra:
        fail("\t[Error]: Found extra {}: \n\t\t".format(name) + "\n\t\t".join(extra))

    banned = sorted(['"{}",'.format(x) for x in actual_list if x in banned_list])
    if banned:
        fail("\t[Error]: Found banned {}: \n\t\t".format(name) + "\n\t\t".join(banned))

def _strip_artifact_version(artifact):
    # Returns "groupId:artifactId"
    return ":".join(artifact.split(":")[:2])

_validate_maven_deps = rule(
    implementation = _validate_maven_deps_impl,
    attrs = {
        "target": attr.label(
            doc = "The target to generate a maven artifact for.",
            aspects = [collect_maven_info],
            mandatory = True,
        ),
        "expected_artifact": attr.string(
            doc = "The artifact name of the target.",
            mandatory = True,
        ),
        "expected_libs": attr.label_list(
            doc = "The set of transitive libraries of the target, if any.",
        ),
        "expected_maven_deps": attr.string_list(
            doc = "The required maven dependencies of the target, if any.",
        ),
        "banned_maven_deps": attr.string_list(
            doc = "The required maven dependencies of the target, if any.",
        ),
    },
)

def _package_android_library_impl(ctx):
    """A very, very simple Android Library (aar) packaging rule.

    This rule only support packaging simple android libraries. No resources
    support, assets, extra libs, nor jni. This rule is needed because
    there is no 'JarJar equivalent' for AARs and some of our artifacts are
    composed of sources spread across multiple android_library targets.

    See: https://developer.android.com/studio/projects/android-library.html#aar-contents
    """
    inputs = [ctx.file.manifest, ctx.file.classesJar]
    if ctx.file.lintJar:
        inputs.append(ctx.file.lintJar)
    if ctx.file.proguardSpec:
        inputs.append(ctx.file.proguardSpec)

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
            if [[ -a {proguardSpec} ]]; then
                cp {proguardSpec} $TMPDIR/proguard.txt
            fi
            touch $TMPDIR/R.txt
            zip -j {outputFile} $TMPDIR/*
            """.format(
            manifest = ctx.file.manifest.path,
            classesJar = ctx.file.classesJar.path,
            lintJar = ctx.file.lintJar.path if ctx.file.lintJar else "none",
            proguardSpec = ctx.file.proguardSpec.path if ctx.file.proguardSpec else "none",
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
        "proguardSpec": attr.label(
            doc = "The proguard.txt file.",
            allow_single_file = True,
            mandatory = False,
        ),
    },
    outputs = {
        "aar": "%{name}.aar",
    },
)

def _package_r8_and_proguard_rule_impl(ctx):
    inputs = [ctx.file.artifactJar]
    if ctx.file.r8Spec:
        inputs.append(ctx.file.r8Spec)
    if ctx.file.proguardSpec:
        inputs.append(ctx.file.proguardSpec)
    ctx.actions.run_shell(
        inputs = inputs,
        outputs = [ctx.outputs.jar],
        command = """
            TMPDIR="$(mktemp -d)"
            cp {artifactJar} $TMPDIR/artifact.jar
            if [[ -a {r8Spec} ]]; then
                mkdir -p META-INF/com.android.tools/r8
                cp {r8Spec} META-INF/com.android.tools/r8/r8.pro
                jar uf $TMPDIR/artifact.jar META-INF/
            fi
            if [[ -a {proguardSpec} ]]; then
                mkdir -p META-INF/com.android.tools/proguard
                cp {proguardSpec} META-INF/com.android.tools/proguard/proguard.pro
                jar uf $TMPDIR/artifact.jar META-INF/
            fi
            cp $TMPDIR/artifact.jar {outputFile}
            """.format(
            artifactJar = ctx.file.artifactJar.path,
            r8Spec = ctx.file.r8Spec.path if ctx.file.r8Spec else "none",
            proguardSpec = ctx.file.proguardSpec.path if ctx.file.proguardSpec else "none",
            outputFile = ctx.outputs.jar.path,
        ),
    )

_package_r8_and_proguard_rule = rule(
    implementation = _package_r8_and_proguard_rule_impl,
    attrs = {
        "artifactJar": attr.label(
            doc = "The library artifact jar to be updated.",
            allow_single_file = True,
            mandatory = True,
        ),
        "r8Spec": attr.label(
            doc = "The r8.txt file to be merged with the artifact jar",
            allow_single_file = True,
            mandatory = False,
        ),
        "proguardSpec": attr.label(
            doc = "The proguard-only.txt file to be merged with the artifact jar",
            allow_single_file = True,
            mandatory = False,
        ),
    },
    outputs = {
        "jar": "%{name}.jar",
    },
)
