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
"""

def dagger_android_library(name, **kwargs):
    """An android_library with dagger's lint.jar.

    Args:
      name: The name of the target.
    """
    native.android_library(name = name + "-intermediate", **kwargs)

    existing_aar = ":" + name + "-intermediate.aar"
    native.genrule(
        name = name,
        srcs = [":" + name + "-intermediate.aar", "//java/dagger/lint:dagger_lint"],
        outs = [name + ".aar"],
        cmd = """
            # Copy the android_library output aar
            cp $(location {existing_aar}) {aar_name}
            # Rewrite permissions of new file to allow modifying it
            chmod +w {aar_name}
            # Symlink the lint jar so the next zip command uses the desired name
            ln -s $(location //java/dagger/lint:dagger_lint) lint.jar
            # Push the lint jar into the aar
            zip -r -qq {aar_name} lint.jar
            # Restore previous permissions
            chmod 100555 {aar_name}
            # Copy the aar to the outs param now
            cp {aar_name} $@
        """.format(existing_aar = existing_aar, aar_name = name + ".aar"),
        visibility = kwargs.get("visibility", None),
        tags = kwargs.get("tags", None),
    )
