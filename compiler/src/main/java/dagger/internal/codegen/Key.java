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

import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import dagger.MembersInjector;
import dagger.Provides;
import java.util.Set;
import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;
import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.type.TypeKind.DECLARED;

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
   * logical equality, so we use {@link MoreTypes#equivalence()} for this object.
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

  static Key create(TypeMirror type) {
    return new AutoValue_Key(Optional.<AnnotationMirror>absent(),
        MoreTypes.equivalence().wrap(type));
  }

  static Key create(Optional<AnnotationMirror> qualifier, TypeMirror type) {
    return new AutoValue_Key(qualifier, MoreTypes.equivalence().wrap(type));
  }

  static final class Factory {
    private final Types types;
    private final Elements elements;

    Factory(Types types, Elements elements) {
      this.types = checkNotNull(types);
      this.elements = checkNotNull(elements);
    }

    private TypeMirror normalize(TypeMirror type) {
      TypeKind kind = type.getKind();
      return kind.isPrimitive() ? types.getPrimitiveType(kind) : type;
    }

    private TypeElement getSetElement() {
      return elements.getTypeElement(Set.class.getCanonicalName());
    }

    Key forProvidesMethod(ExecutableElement e) {
      checkNotNull(e);
      checkArgument(e.getKind().equals(METHOD));
      Provides providesAnnotation = e.getAnnotation(Provides.class);
      checkArgument(providesAnnotation != null);
      TypeMirror returnType = normalize(e.getReturnType());
      Optional<AnnotationMirror> qualifier = getQualifier(e);
      switch (providesAnnotation.type()) {
        case UNIQUE:
          return new AutoValue_Key(qualifier, MoreTypes.equivalence().wrap(returnType));
        case SET:
          TypeMirror setType = types.getDeclaredType(getSetElement(), returnType);
          return new AutoValue_Key(qualifier, MoreTypes.equivalence().wrap(setType));
        case SET_VALUES:
          // TODO(gak): do we want to allow people to use "covariant return" here?
          checkArgument(returnType.getKind().equals(DECLARED));
          checkArgument(((DeclaredType) returnType).asElement().equals(getSetElement()));
          return new AutoValue_Key(qualifier, MoreTypes.equivalence().wrap(returnType));
        default:
          throw new AssertionError();
      }
    }

    Key forInjectConstructor(ExecutableElement e) {
      checkNotNull(e);
      checkArgument(e.getKind().equals(CONSTRUCTOR));
      checkArgument(!getQualifier(e).isPresent());
      // Must use the enclosing element.  The return type is void for constructors(?!)
      TypeMirror type = e.getEnclosingElement().asType();
      return new AutoValue_Key(Optional.<AnnotationMirror>absent(),
          MoreTypes.equivalence().wrap(type));
    }

    Key forMembersInjector(TypeElement e) {
      checkNotNull(e);
      checkArgument(e.getKind().equals(CLASS));
      checkArgument(!getQualifier(e).isPresent());
      // Must use the enclosing element.  The return type is void for constructors(?!)
      TypeMirror type = types.getDeclaredType(
          elements.getTypeElement(MembersInjector.class.getCanonicalName()), e.asType());
      return new AutoValue_Key(Optional.<AnnotationMirror>absent(),
          MoreTypes.equivalence().wrap(type));
    }

  }
}
