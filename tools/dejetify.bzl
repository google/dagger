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

"""Macro for producing dejetified artifacts.
"""

# See: https://developer.android.com/studio/command-line/jetifier
JETIFIER_STANDALONE = "https://dl.google.com/dl/android/studio/jetifier-zips/1.0.0-beta08/jetifier-standalone.zip"

def dejetified_library(name, input, output):
    _dejetify_library(name, input, output)

def _dejetify_library(name, input, output):
    """Generates a dejetified library artifact.

    A dejetified artifact is one that has been transformed to migrate its
    AndroidX APIs to the support equivalents.

    Args:
      name: The name of the target.
      input: The android_library input, e.g. ":myLibrary.aar".
      output: The name of the output artifact, e.g. "dejetified-myLibrary.aar".
    """
    native.genrule(
        name = name,
        srcs = [input],
        outs = [output],
        cmd = """
            TEMP="$$(mktemp -d)"
            curl {tool_link} --output $$TEMP/jetifier-standalone.zip
            unzip $$TEMP/jetifier-standalone.zip -d $$TEMP/
            $$TEMP/jetifier-standalone/bin/jetifier-standalone -r \
              -l info -i $< -o $@
        """.format(tool_link = JETIFIER_STANDALONE),
        local = 1,
    )
