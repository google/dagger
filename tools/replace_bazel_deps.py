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

import re
import sys

PATTERN = re.compile("<generated_bzl_deps ?/>")
ONLY_SPACE = re.compile(" +$")

def readfile(filename):
  with open(filename, 'r') as file:
    return file.read()

template_file_contents = readfile(sys.argv[1])
deps_replacement = readfile(sys.argv[2])

match = re.search(PATTERN, template_file_contents)

if match:
  replacement_start = match.start()
  last_newline = template_file_contents.rfind('\n', 0, replacement_start) + 1
  if (last_newline > 0 and
      ONLY_SPACE.match(template_file_contents, last_newline, replacement_start)):
    padding = ' ' * (replacement_start - last_newline)
    new_deps_lines = []
    for dep_line in deps_replacement.split('\n'):
      if len(new_deps_lines) is 0: # don't prefix the first line, it's already prefixed
        new_deps_lines.append(dep_line)
      else:
        new_deps_lines.append(padding + dep_line)
    deps_replacement = '\n'.join(new_deps_lines)
else:
  print "Could not find regex '%s' in template file" % PATTERN.pattern
  exit

with open(sys.argv[3], 'w') as output:
  output.write(PATTERN.sub(deps_replacement, template_file_contents, count=1))
