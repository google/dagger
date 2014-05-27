/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.auto.common.MoreElements;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import dagger.Provides;
import java.util.Iterator;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.immutableEnumSet;
import static dagger.Provides.Type.SET;
import static dagger.Provides.Type.SET_VALUES;
import static dagger.internal.codegen.InjectionAnnotations.getScopeAnnotation;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.FIELD;
import static javax.lang.model.element.ElementKind.METHOD;

/**
 * A value object representing the mechanism by which a {@link Key} can be provided. New instances
 * should be created using an instance of the {@link Factory}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@AutoValue
abstract class ProvisionBinding extends Binding {
  enum Kind {
    /** Represents an {@link Inject} binding. */
    INJECTION,
    /** Represents a binding configured by {@link Provides}. */
    PROVISION,
  }

  /**
   * The type of binding ({@link Inject} or {@link Provides}). For the particular type of provision,
   * use {@link #provisionType}.
   */
  abstract Kind bindingKind();

  /** Returns provision type that was used to bind the key. */
  abstract Provides.Type provisionType();

  /** The {@link Key} that is provided by this binding. */
  abstract Key providedKey();

  /** The scope in which the binding declares the {@link #providedKey()}. */
  abstract Optional<AnnotationMirror> scope();

  /** Returns {@code true} if this provision binding requires members to be injected implicitly. */
  abstract boolean requiresMemberInjection();

  private static ImmutableSet<Provides.Type> SET_BINDING_TYPES = immutableEnumSet(SET, SET_VALUES);

  /**
   * Returns {@code true} if the given bindings are all contributors to a set binding.
   *
   * @throws IllegalArgumentException if some of the bindings are set bindings and some are not.
   */
  static boolean isSetBindingCollection(Iterable<ProvisionBinding> bindings) {
    checkNotNull(bindings);
    Iterator<ProvisionBinding> iterator = bindings.iterator();
    checkArgument(iterator.hasNext(), "no bindings");
    boolean setBinding = SET_BINDING_TYPES.contains(iterator.next().provisionType());
    while (iterator.hasNext()) {
      checkArgument(setBinding,
          "more than one binding present, but found a non-set binding");
      checkArgument(SET_BINDING_TYPES.contains(iterator.next().provisionType()),
          "more than one binding present, but found a non-set binding");
    }
    return setBinding;
  }

  static final class Factory {
    private final Key.Factory keyFactory;
    private final DependencyRequest.Factory dependencyRequestFactory;

    Factory(Key.Factory keyFactory, DependencyRequest.Factory dependencyRequestFactory) {
      this.keyFactory = keyFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
    }

    ProvisionBinding forNoArgsConstructor(ExecutableElement constructorElement) {
      checkNotNull(constructorElement);
      checkArgument(constructorElement.getKind().equals(CONSTRUCTOR));
      Key key = keyFactory.forInjectConstructor(constructorElement);
      checkArgument(!key.qualifier().isPresent());
      TypeElement typeElement = (TypeElement) constructorElement.getEnclosingElement();
      boolean requiresMemberInjection = requiresMemberInjection(MoreElements.asType(typeElement));
      return new AutoValue_ProvisionBinding(
          constructorElement,
          dependencyRequestFactory.forRequiredVariables(constructorElement.getParameters()),
          Kind.INJECTION,
          Provides.Type.UNIQUE,
          key,
          getScopeAnnotation(constructorElement.getEnclosingElement()),
          requiresMemberInjection);
    }

    ProvisionBinding forInjectConstructor(ExecutableElement constructorElement) {
      checkNotNull(constructorElement);
      checkArgument(constructorElement.getAnnotation(Inject.class) != null);
      return forNoArgsConstructor(constructorElement);
    }

    private static final ImmutableSet<ElementKind> MEMBER_KINDS =
        Sets.immutableEnumSet(METHOD, FIELD);

    private static boolean requiresMemberInjection(TypeElement type) {
      for (Element enclosedElement : type.getEnclosedElements()) {
        if (MEMBER_KINDS.contains(enclosedElement.getKind())
            && (enclosedElement.getAnnotation(Inject.class) != null)) {
          return true;
        }
      }
      return false;
    }

    ProvisionBinding forProvidesMethod(ExecutableElement providesMethod) {
      checkNotNull(providesMethod);
      checkArgument(providesMethod.getKind().equals(METHOD));
      Provides providesAnnotation = providesMethod.getAnnotation(Provides.class);
      checkArgument(providesAnnotation != null);
      return new AutoValue_ProvisionBinding(
          providesMethod,
          dependencyRequestFactory.forRequiredVariables(providesMethod.getParameters()),
          Kind.PROVISION,
          providesAnnotation.type(),
          keyFactory.forProvidesMethod(providesMethod),
          getScopeAnnotation(providesMethod),
          false);
    }
  }
}
