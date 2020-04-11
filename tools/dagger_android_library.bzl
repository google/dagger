# Copyright (C) 2020 The Dagger Authors.
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

"""Macro for producing aars with Dagger's lint.jar in them.

This works by basically invoking the native android_library rule under the hood, but then modifying
its aar to embed the compiled lint.jar into it. To preserve build outputs integrity, the sole output
of the genrule is just a lintpatch text file with an md5 hash of the final aar file.
"""

def dagger_android_library(name, **kwargs):
    """An android_library with dagger's lint.jar.

    Args:
      name: The name of the target.
    """
    native_result = native.android_library(name = name, **kwargs)

    created_aar = ":" + name + ".aar"
    native.genrule(
        name = name + "-lintpatch",
        srcs = [":" + name + ".aar"],
        outs = [name + "-lintpatch.txt"],
        cmd = """
            # Rewrite permissions of new file to allow modifying it
            chmod +w $(location {created_aar})

            # Symlink the lint jar so the next zip command uses the desired name
            ln -s $(location //java/dagger/lint:dagger_lint) lint.jar

            # Push the lint jar into the aar
            zip -r -qq $(location {created_aar}) lint.jar

            # Restore previous permissions
            chmod -w $(location {created_aar})

            # Write md5 of the final aar into a the output
            md5sum $(location {created_aar}) | cut -c 1-32 >> $@
        """.format(created_aar = created_aar),
        tools = ["//java/dagger/lint:dagger_lint"],
        visibility = kwargs.get("visibility", None),
        tags = kwargs.get("tags", None),
    )
