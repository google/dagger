/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static com.google.auto.common.AnnotationMirrors.getAnnotationElementAndValue;
import static dagger.internal.codegen.ConfigurationAnnotations.getModuleAnnotation;
import static dagger.internal.codegen.ConfigurationAnnotations.getModuleSubcomponents;
import static dagger.internal.codegen.ConfigurationAnnotations.getSubcomponentCreator;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import dagger.model.Key;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * A declaration for a subcomponent that is included in a module via {@link
 * dagger.Module#subcomponents()}.
 */
@AutoValue
abstract class SubcomponentDeclaration extends BindingDeclaration {
  /**
   * Key for the {@link dagger.Subcomponent.Builder} or {@link
   * dagger.producers.ProductionSubcomponent.Builder} of {@link #subcomponentType()}.
   */
  @Override
  public abstract Key key();

  /**
   * The type element that defines the {@link dagger.Subcomponent} or {@link
   * dagger.producers.ProductionSubcomponent} for this declaration.
   */
  abstract TypeElement subcomponentType();

  abstract AnnotationMirror moduleAnnotation();

  static class Factory {
    private final KeyFactory keyFactory;

    @Inject
    Factory(KeyFactory keyFactory) {
      this.keyFactory = keyFactory;
    }

    ImmutableSet<SubcomponentDeclaration> forModule(TypeElement module) {
      ImmutableSet.Builder<SubcomponentDeclaration> declarations = ImmutableSet.builder();
      AnnotationMirror moduleAnnotation = getModuleAnnotation(module).get();
      Element subcomponentAttribute =
          getAnnotationElementAndValue(moduleAnnotation, "subcomponents").getKey();
      for (TypeElement subcomponent :
          MoreTypes.asTypeElements(getModuleSubcomponents(moduleAnnotation))) {
        declarations.add(
            new AutoValue_SubcomponentDeclaration(
                Optional.of(subcomponentAttribute),
                Optional.of(module),
                keyFactory.forSubcomponentCreator(
                    getSubcomponentCreator(subcomponent).get().asType()),
                subcomponent,
                moduleAnnotation));
      }
      return declarations.build();
    }
  }
}
