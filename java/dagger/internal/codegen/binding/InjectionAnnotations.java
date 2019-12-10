/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkNotNull;
import static javax.lang.model.util.ElementFilter.constructorsIn;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreElements;
import com.google.auto.common.SuperficialValidation;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import dagger.internal.codegen.kotlin.KotlinMetadataUtil;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/** Utilities relating to annotations defined in the {@code javax.inject} package. */
public final class InjectionAnnotations {
  private final KotlinMetadataUtil kotlinMetadataUtil;

  @Inject
  InjectionAnnotations(KotlinMetadataUtil kotlinMetadataUtil) {
    this.kotlinMetadataUtil = kotlinMetadataUtil;
  }

  public Optional<AnnotationMirror> getQualifier(Element e) {
    if (!SuperficialValidation.validateElement(e)) {
      throw new TypeNotPresentException(e.toString(), null);
    }
    checkNotNull(e);
    ImmutableSet<? extends AnnotationMirror> qualifierAnnotations = getQualifiers(e);
    switch (qualifierAnnotations.size()) {
      case 0:
        return Optional.empty();
      case 1:
        return Optional.<AnnotationMirror>of(qualifierAnnotations.iterator().next());
      default:
        throw new IllegalArgumentException(
            e + " was annotated with more than one @Qualifier annotation");
    }
  }

  public ImmutableSet<? extends AnnotationMirror> getQualifiers(Element element) {
    ImmutableSet<? extends AnnotationMirror> qualifiers =
        AnnotationMirrors.getAnnotatedAnnotations(element, Qualifier.class);
    if (kotlinMetadataUtil.hasMetadata(element) && element.getKind() == ElementKind.FIELD) {
      return Sets.union(
              qualifiers,
              kotlinMetadataUtil.getSyntheticPropertyAnnotations(
                  MoreElements.asVariable(element), Qualifier.class))
          .immutableCopy();
    } else {
      return qualifiers;
    }
  }

  /** Returns the constructors in {@code type} that are annotated with {@link Inject}. */
  public static ImmutableSet<ExecutableElement> injectedConstructors(TypeElement type) {
    return FluentIterable.from(constructorsIn(type.getEnclosedElements()))
        .filter(constructor -> isAnnotationPresent(constructor, Inject.class))
        .toSet();
  }
}
