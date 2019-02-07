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

"""Macro for creating a jar from a set of flat files"""

def simple_jar(name, srcs):
    """Creates a jar out of a set of flat files"""

    # TODO(dpb): consider creating a Fileset() under the hood to support srcs from different
    # directories, or continually update the same zip file for each source file
    # TODO(ronshapiro): extract a .sh file to make this easier to understand
    native.genrule(
        name = name,
        srcs = srcs,
        outs = ["%s.jar" % name],
        cmd = 'package_name="{package_name}"'.format(package_name = native.package_name()) +
              """
        dirname=""
        for src in $(SRCS); do
          src_dirname="$$(echo "$${src}" | grep -o -P "(.*/)?$${package_name}" | head -n1)"
          if [[ -z "$${dirname}" ]]; then
            dirname="$${src_dirname}"
          elif [[ "$${dirname}" != "$${src_dirname}" ]]; then
            echo "Sources must all be in the same directory: $(SRCS)"
            exit 1
          fi
        done

        if [[ -z "$${dirname}" ]]; then
          echo "No sources provided"
          exit 1
        fi

        OUT="$$(pwd)/$@"
        cd "$${dirname}"
        zip "$$OUT" -r * &> /dev/null
        """,
    )
