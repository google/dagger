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
        "maven_transitive_deps": """
        All transitive deps that are included in some maven dependency.
        """,
    },
)

_EMPTY_MAVEN_INFO = MavenInfo(
    artifact = None,
    has_srcs = False,
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

    maven_deps = []
    maven_transitive_deps = []
    for dep in (deps + exports):
        # If the dep is itself a maven artifact, add it and all of its transitive deps.
        # Otherwise, just propagate its transitive maven deps.
        if dep[MavenInfo].artifact or dep[MavenInfo] == _EMPTY_MAVEN_INFO:
            maven_deps.append(dep.label)
            maven_transitive_deps.append(dep[MavenInfo].all_transitive_deps)
        else:
            maven_transitive_deps.append(dep[MavenInfo].maven_transitive_deps)

    return [MavenInfo(
        artifact = artifact,
        has_srcs = len(srcs) > 0,
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

