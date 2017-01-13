# Copyright (C) 2017 The Dagger Authors.
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

import ast
import unittest

class WorkspaceVisitor(ast.NodeVisitor):
  def __init__(self):
    self.missing_sha1 = []

  def visit_Call(self, rule):
    if rule.func.id == 'maven_jar':
      name = None
      for parameter in rule.keywords:
        if parameter.arg == 'sha1':
          return
        if parameter.arg == 'name':
          name = parameter.value.s
      self.missing_sha1.append(name)

class MavenSha1Test(unittest.TestCase):
  def test_each_maven_jar_rule_has_sha1(self):
    with open('WORKSPACE', 'r') as workspace:
      visitor = WorkspaceVisitor()
      visitor.visit(ast.parse(workspace.read()))
      if len(visitor.missing_sha1) > 0:
        missing = ', '.join(visitor.missing_sha1)
        self.fail('%s did not specify a sha1' % missing)

if __name__ == '__main__':
  unittest.main()
