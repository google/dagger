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

class WorkspaceVisitor(ast.NodeVisitor):
  def __init__(self):
    self.artifacts = {}

  def visit_Call(self, rule):
    if rule.func.id is not 'maven_jar': return
    name = None
    artifact = None
    for keyword in rule.keywords:
      if keyword.arg == 'name':
        name = keyword.value.s
      if keyword.arg == 'artifact':
        artifact = keyword.value.s
    self.artifacts['@%s//jar:jar' % name] = artifact

def maven_artifacts():
  visitor = WorkspaceVisitor()
  with open('WORKSPACE', 'r') as workspace:
    visitor.visit(ast.parse(workspace.read()))

  return visitor.artifacts
