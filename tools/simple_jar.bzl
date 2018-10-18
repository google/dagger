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
    # directories
    native.genrule(
        name = name,
        srcs = srcs,
        outs = ["%s.jar" % name],
        cmd = """
        OUT="$$(pwd)/$@"
        cd {package_name}
        zip "$$OUT" -r * &> /dev/null
        """.format(package_name = native.package_name()),
    )
