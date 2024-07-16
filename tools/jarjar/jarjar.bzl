# Copyright (C) 2024 The Dagger Authors.
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

"""Skylark rules for jarjar. See https://github.com/pantsbuild/jarjar
"""

load("@rules_java//java:defs.bzl", "java_common")

def _jarjar_library(ctx):
    ctx.actions.write(
        output = ctx.outputs._rules_file,
        content = "\n".join(ctx.attr.rules),
    )

    jar_files = depset(transitive = [jar.files for jar in ctx.attr.jars]).to_list()

    command = """
        export JAVA_HOME="{java_home}"
        export MERGE_META_INF_FILES="{merge_meta_inf_files}"
        export JARJAR="{jarjar}"
        export RULES_FILE="{rules_file}"
        export OUTFILE="{outfile}"
        "{jarjar_runner}" {jars}
    """.format(
        java_home = str(ctx.attr._jdk[java_common.JavaRuntimeInfo].java_home),
        merge_meta_inf_files = " ".join(ctx.attr.merge_meta_inf_files),
        jarjar = ctx.executable._jarjar.path,
        rules_file = ctx.outputs._rules_file.path,
        outfile = ctx.outputs.jar.path,
        jarjar_runner = ctx.executable._jarjar_runner.path,
        jars = " ".join([jar.path for jar in jar_files]),
    )

    ctx.actions.run_shell(
        command = command,
        inputs = [ctx.outputs._rules_file] + jar_files + ctx.files._jdk,
        outputs = [ctx.outputs.jar],
        tools = [ctx.executable._jarjar, ctx.executable._jarjar_runner],
    )

_jarjar_library_attrs = {
    "rules": attr.string_list(),
    "jars": attr.label_list(
        allow_files = [".jar"],
    ),
    "merge_meta_inf_files": attr.string_list(
        allow_empty = True,
        default = [],
        mandatory = False,
        doc = """A list of regular expressions that match files relative to the
        META-INF directory that will be merged into the output jar, in addition
        to files in META-INF/services. To add all files in META-INF/foo, for
        example, use "foo/.*".""",
    ),
}

_jarjar_library_attrs.update({
    "_jarjar": attr.label(
        default = Label("//tools/jarjar"),
        executable = True,
        cfg = "exec",
    ),
    "_jarjar_runner": attr.label(
        default = Label("//tools/jarjar:jarjar_runner"),
        executable = True,
        cfg = "exec",
    ),
    "_jdk": attr.label(
        default = Label("@bazel_tools//tools/jdk:current_java_runtime"),
        providers = [java_common.JavaRuntimeInfo],
    ),
})

jarjar_library = rule(
    attrs = _jarjar_library_attrs,
    outputs = {
        "jar": "%{name}.jar",
        "_rules_file": "%{name}.jarjar_rules",
    },
    implementation = _jarjar_library,
)
