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

package dagger.internal.codegen.binding;

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.extension.DaggerStreams.presentValues;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static java.util.stream.Collectors.joining;

import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.model.BindingGraph.SubcomponentCreatorBindingEdge;
import dagger.internal.codegen.model.DaggerTypeElement;

/** An implementation of {@link SubcomponentCreatorBindingEdge}. */
public final class SubcomponentCreatorBindingEdgeImpl implements SubcomponentCreatorBindingEdge {
  private final ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations;

  SubcomponentCreatorBindingEdgeImpl(
      ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations) {
    this.subcomponentDeclarations = subcomponentDeclarations;
  }

  @Override
  public ImmutableSet<DaggerTypeElement> declaringModules() {
    return subcomponentDeclarations.stream()
        .map(SubcomponentDeclaration::contributingModule)
        .flatMap(presentValues())
        .map(DaggerTypeElement::from)
        .collect(toImmutableSet());
  }

  @Override
  public String toString() {
    return "subcomponent declared by "
        + (subcomponentDeclarations.size() == 1
            ? getOnlyElement(declaringModules()).xprocessing().getQualifiedName()
            : declaringModules().stream()
                .map(DaggerTypeElement::xprocessing)
                .map(XTypeElement::getQualifiedName)
                .collect(joining(", ", "{", "}")));
  }
}
