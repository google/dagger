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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.METHOD;

import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Objects;
import com.google.common.base.Optional;

import dagger.Provides;

import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

/**
 * Represents a unique combination of {@linkplain TypeMirror type} and
 * {@linkplain Qualifier qualifier} to which binding can occur.
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class Key {
  abstract Optional<AnnotationMirror> qualifier();

  /**
   * As documented in {@link TypeMirror}, equals and hashCode aren't implemented to represent
   * logical equality, so we use {@link Mirrors#equivalence()} for this object.
   */
  abstract Equivalence.Wrapper<TypeMirror> wrappedType();

  TypeMirror type() {
    return wrappedType().get();
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(Key.class)
        .omitNullValues()
        .add("qualifier", qualifier().orNull())
        .add("type", type())
        .toString();
  }

  // TODO(gak): normalize boxed types

  static Key create(TypeMirror type) {
    return new AutoValue_Key(Optional.<AnnotationMirror>absent(), Mirrors.equivalence().wrap(type));
  }

  static Key create(Optional<AnnotationMirror> qualifier, TypeMirror type) {
    return new AutoValue_Key(qualifier, Mirrors.equivalence().wrap(type));
  }

  // TODO(gak): decide whether to address set bindings here or someplace else
  static Key forProvidesMethod(ExecutableElement e) {
    checkNotNull(e);
    checkArgument(e.getKind().equals(METHOD));
    checkArgument(e.getAnnotation(Provides.class) != null);
    return new AutoValue_Key(getQualifier(e), Mirrors.equivalence().wrap(e.getReturnType()));
  }

  static Key forComponentMethod(ExecutableElement e) {
    checkNotNull(e);
    checkArgument(e.getKind().equals(METHOD));
    checkArgument(e.getParameters().isEmpty());
    return new AutoValue_Key(getQualifier(e), Mirrors.equivalence().wrap(e.getReturnType()));
  }

  static Key forInjectConstructor(ExecutableElement e) {
    checkNotNull(e);
    checkArgument(e.getKind().equals(CONSTRUCTOR));
    checkArgument(!getQualifier(e).isPresent());
    // Must use the enclosing element.  The return type is void for constructors(?!)
    TypeMirror type = e.getEnclosingElement().asType();
    return new AutoValue_Key(Optional.<AnnotationMirror>absent(), Mirrors.equivalence().wrap(type));
  }
}
