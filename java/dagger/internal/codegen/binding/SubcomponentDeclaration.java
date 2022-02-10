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

package dagger.internal.codegen.binding;

import static dagger.internal.codegen.binding.ConfigurationAnnotations.getSubcomponentCreator;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.base.DaggerSuperficialValidation;
import dagger.internal.codegen.base.ModuleAnnotation;
import dagger.spi.model.Key;
import java.util.Optional;
import javax.inject.Inject;

/**
 * A declaration for a subcomponent that is included in a module via {@link
 * dagger.Module#subcomponents()}.
 */
@AutoValue
public abstract class SubcomponentDeclaration extends BindingDeclaration {
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
  abstract XTypeElement subcomponentType();

  /** The module annotation. */
  public abstract ModuleAnnotation moduleAnnotation();

  @Memoized
  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object obj);

  /** A {@link SubcomponentDeclaration} factory. */
  public static class Factory {
    private final KeyFactory keyFactory;
    private final DaggerSuperficialValidation superficialValidation;

    @Inject
    Factory(KeyFactory keyFactory, DaggerSuperficialValidation superficialValidation) {
      this.keyFactory = keyFactory;
      this.superficialValidation = superficialValidation;
    }

    ImmutableSet<SubcomponentDeclaration> forModule(XTypeElement module) {
      ModuleAnnotation moduleAnnotation =
          ModuleAnnotation.moduleAnnotation(module, superficialValidation).get();
      XElement subcomponentAttribute =
          moduleAnnotation.annotation().getType().getTypeElement().getDeclaredMethods().stream()
              .filter(method -> getSimpleName(method).contentEquals("subcomponents"))
              .collect(toOptional())
              .get();

      ImmutableSet.Builder<SubcomponentDeclaration> declarations = ImmutableSet.builder();
      for (XTypeElement subcomponent : moduleAnnotation.subcomponents()) {
        declarations.add(
            new AutoValue_SubcomponentDeclaration(
                Optional.of(subcomponentAttribute),
                Optional.of(module),
                keyFactory.forSubcomponentCreator(
                    getSubcomponentCreator(subcomponent).get().getType()),
                subcomponent,
                moduleAnnotation));
      }
      return declarations.build();
    }
  }
}
