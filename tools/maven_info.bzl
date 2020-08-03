# Copyright (C) 2019 The Dagger Authors.
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
"""Skylark rules to collect Maven artifacts information.
"""

load("@bazel_skylib//lib:unittest.bzl", "asserts", "unittest")

# TODO(b/142057516): Unfork this file once we've settled on a more general API.
MavenInfo = provider(
    fields = {
        "artifact": """
        The Maven coordinate for the artifact that is exported by this target, if one exists.
        """,
        "has_srcs": """
        True if this library contains srcs..
        """,
        "all_transitive_deps": """
        All transitive deps of the target with srcs.
        """,
        "maven_nearest_artifacts": """
        The nearest maven deps of the target.
        """,
        "maven_transitive_deps": """
        All transitive deps that are included in some maven dependency.
        """,
    },
)

_EMPTY_MAVEN_INFO = MavenInfo(
    artifact = None,
    has_srcs = False,
    maven_nearest_artifacts = depset(),
    maven_transitive_deps = depset(),
    all_transitive_deps = depset(),
)

_MAVEN_COORDINATES_PREFIX = "maven_coordinates="

def _collect_maven_info_impl(target, ctx):
    tags = getattr(ctx.rule.attr, "tags", [])
    srcs = getattr(ctx.rule.attr, "srcs", [])
    deps = getattr(ctx.rule.attr, "deps", [])
    exports = getattr(ctx.rule.attr, "exports", [])

    artifact = None
    for tag in tags:
        if tag in ("maven:compile_only", "maven:shaded"):
            return [_EMPTY_MAVEN_INFO]
        if tag.startswith(_MAVEN_COORDINATES_PREFIX):
            artifact = tag[len(_MAVEN_COORDINATES_PREFIX):]

    all_deps = [dep.label for dep in (deps + exports) if dep[MavenInfo].has_srcs]
    all_transitive_deps = [dep[MavenInfo].all_transitive_deps for dep in (deps + exports)]

    maven_artifacts = []
    maven_nearest_artifacts = []
    maven_deps = []
    maven_transitive_deps = []
    for dep in (deps + exports):
        # If the dep is itself a maven artifact, add it and all of its transitive deps.
        # Otherwise, just propagate its transitive maven deps.
        if dep[MavenInfo].artifact or dep[MavenInfo] == _EMPTY_MAVEN_INFO:
            if (dep[MavenInfo].artifact):
                maven_artifacts.append(dep[MavenInfo].artifact)
            maven_deps.append(dep.label)
            maven_transitive_deps.append(dep[MavenInfo].all_transitive_deps)
        else:
            maven_nearest_artifacts.append(dep[MavenInfo].maven_nearest_artifacts)
            maven_transitive_deps.append(dep[MavenInfo].maven_transitive_deps)

    return [MavenInfo(
        artifact = artifact,
        has_srcs = len(srcs) > 0,
        maven_nearest_artifacts = depset(maven_artifacts, transitive = maven_nearest_artifacts),
        maven_transitive_deps = depset(maven_deps, transitive = maven_transitive_deps),
        all_transitive_deps = depset(all_deps, transitive = all_transitive_deps),
    )]

collect_maven_info = aspect(
    attr_aspects = [
        "deps",
        "exports",
    ],
    doc = """
    Collects the Maven information for targets, their dependencies, and their transitive exports.
    """,
    implementation = _collect_maven_info_impl,
)

def _fake_java_library(name, deps = None, exports = None, is_artifact = True):
    src_file = ["%s.java" % name]
    native.genrule(
        name = "%s_source_file" % name,
        outs = src_file,
        cmd = "echo 'package pkg; class %s {}' > $@" % name,
    )
    native.java_library(
        name = name,
        srcs = src_file,
        tags = ["maven_coordinates=%s:_:_" % name] if is_artifact else [],
        deps = deps or [],
        exports = exports or [],
    )

def _maven_info_test_impl(ctx):
    env = unittest.begin(ctx)
    asserts.equals(
        env,
        expected = ctx.attr.artifact if ctx.attr.artifact else None,
        actual = ctx.attr.target[MavenInfo].artifact,
        msg = "MavenInfo.artifact",
    )
    asserts.equals(
        env,
        expected = sorted([ctx.label.relative(dep) for dep in ctx.attr.maven_transitive_deps]),
        actual = sorted(ctx.attr.target[MavenInfo].maven_transitive_deps.to_list()),
        msg = "MavenInfo.maven_transitive_deps",
    )
    asserts.equals(
        env,
        expected = sorted([ctx.label.relative(dep) for dep in ctx.attr.all_transitive_deps]),
        actual = sorted(ctx.attr.target[MavenInfo].all_transitive_deps.to_list()),
        msg = "MavenInfo.all_transitive_deps",
    )
    return unittest.end(env)

_maven_info_test = unittest.make(
    _maven_info_test_impl,
    attrs = {
        "target": attr.label(aspects = [collect_maven_info]),
        "artifact": attr.string(),
        "maven_transitive_deps": attr.string_list(),
        "all_transitive_deps": attr.string_list(),
    },
)

def maven_info_tests():
    """Tests for `pom_file` and `MavenInfo`.
    """
    _fake_java_library(name = "A")
    _fake_java_library(
        name = "DepOnA",
        deps = [":A"],
    )

    _maven_info_test(
        name = "a_test",
        target = ":A",
        artifact = "A:_:_",
        maven_transitive_deps = [],
        all_transitive_deps = [],
    )

    _maven_info_test(
        name = "dependencies_test",
        target = ":DepOnA",
        artifact = "DepOnA:_:_",
        maven_transitive_deps = [":A"],
        all_transitive_deps = [":A"],
    )

    _fake_java_library(
        name = "ExportsA",
        exports = [":A"],
    )

    _maven_info_test(
        name = "exports_test",
        target = ":ExportsA",
        artifact = "ExportsA:_:_",
        maven_transitive_deps = [":A"],
        all_transitive_deps = [":A"],
    )

    _fake_java_library(
        name = "TransitiveExports",
        exports = [":ExportsA"],
    )

    _maven_info_test(
        name = "transitive_exports_test",
        target = ":TransitiveExports",
        artifact = "TransitiveExports:_:_",
        maven_transitive_deps = [":ExportsA", ":A"],
        all_transitive_deps = [":ExportsA", ":A"],
    )

    _fake_java_library(
        name = "TransitiveDeps",
        deps = [":ExportsA"],
    )

    _maven_info_test(
        name = "transitive_deps_test",
        target = ":TransitiveDeps",
        artifact = "TransitiveDeps:_:_",
        maven_transitive_deps = [":ExportsA", ":A"],
        all_transitive_deps = [":ExportsA", ":A"],
    )

    _fake_java_library(name = "Node1", is_artifact = False)
    _maven_info_test(
        name = "test_node1",
        target = ":Node1",
        maven_transitive_deps = [],
        all_transitive_deps = [],
    )

    _fake_java_library(name = "Node2_Artifact", deps = [":Node1"])
    _maven_info_test(
        name = "test_node2",
        target = ":Node2_Artifact",
        artifact = "Node2_Artifact:_:_",
        maven_transitive_deps = [],
        all_transitive_deps = [":Node1"],
    )

    _fake_java_library(name = "Node3", deps = [":Node2_Artifact"], is_artifact = False)
    _maven_info_test(
        name = "test_node3",
        target = ":Node3",
        maven_transitive_deps = [":Node1", ":Node2_Artifact"],
        all_transitive_deps = [":Node1", ":Node2_Artifact"],
    )

    _fake_java_library(name = "Node4", deps = [":Node3"], is_artifact = False)
    _maven_info_test(
        name = "test_node4",
        target = ":Node4",
        maven_transitive_deps = [":Node1", ":Node2_Artifact"],
        all_transitive_deps = [":Node1", ":Node2_Artifact", ":Node3"],
    )
