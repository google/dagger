/*
 * Copyright (C) 2018 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen;

import dagger.model.BindingGraph.DependencyEdge;
import dagger.model.DependencyRequest;

/** An implementation of {@link DependencyEdge}. */
final class DependencyEdgeImpl implements DependencyEdge {

  private final DependencyRequest dependencyRequest;
  private final boolean entryPoint;

  DependencyEdgeImpl(DependencyRequest dependencyRequest, boolean entryPoint) {
    this.dependencyRequest = dependencyRequest;
    this.entryPoint = entryPoint;
  }

  @Override
  public DependencyRequest dependencyRequest() {
    return dependencyRequest;
  }

  @Override
  public boolean isEntryPoint() {
    return entryPoint;
  }

  @Override
  public String toString() {
    String string =
        dependencyRequest
            .requestElement()
            .map(ElementFormatter::elementToString)
            .orElseGet(
                () ->
                    "synthetic request for "
                        + dependencyRequest.kind().format(dependencyRequest.key()));
    return entryPoint ? string + " (entry point)" : string;
  }
}
