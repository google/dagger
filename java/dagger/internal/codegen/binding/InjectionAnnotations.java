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

import static com.google.auto.common.MoreElements.asType;
import static com.google.auto.common.MoreElements.asVariable;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.base.MoreAnnotationValues.getStringValue;
import static dagger.internal.codegen.binding.SourceFiles.memberInjectedFieldSignatureForVariable;
import static dagger.internal.codegen.binding.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.langmodel.DaggerElements.getAnnotationMirror;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.util.ElementFilter.constructorsIn;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.SuperficialValidation;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.extension.DaggerCollectors;
import dagger.internal.codegen.extension.DaggerStreams;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.kotlin.KotlinMetadataUtil;
import dagger.internal.codegen.langmodel.DaggerElements;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;

/** Utilities relating to annotations defined in the {@code javax.inject} package. */
public final class InjectionAnnotations {

  private static final Equivalence<AnnotationMirror> EQUIVALENCE = AnnotationMirrors.equivalence();

  private final DaggerElements elements;
  private final KotlinMetadataUtil kotlinMetadataUtil;

  @Inject
  InjectionAnnotations(DaggerElements elements, KotlinMetadataUtil kotlinMetadataUtil) {
    this.elements = elements;
    this.kotlinMetadataUtil = kotlinMetadataUtil;
  }

  public Optional<AnnotationMirror> getQualifier(Element e) {
    if (!SuperficialValidation.validateElement(e)) {
      throw new TypeNotPresentException(e.toString(), null);
    }
    checkNotNull(e);
    ImmutableCollection<? extends AnnotationMirror> qualifierAnnotations = getQualifiers(e);
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

  public ImmutableCollection<? extends AnnotationMirror> getQualifiers(Element element) {
    ImmutableSet<? extends AnnotationMirror> qualifiers =
        AnnotationMirrors.getAnnotatedAnnotations(element, Qualifier.class);
    if (element.getKind() == ElementKind.FIELD
        // static injected fields are not supported, no need to get qualifier from kotlin metadata
        && !element.getModifiers().contains(STATIC)
        && isAnnotationPresent(element, Inject.class)
        && kotlinMetadataUtil.hasMetadata(element)) {
      return Stream.concat(
              qualifiers.stream(), getQualifiersForKotlinProperty(asVariable(element)).stream())
          .map(EQUIVALENCE::wrap) // Wrap in equivalence to deduplicate
          .distinct()
          .map(Wrapper::get)
          .collect(DaggerStreams.toImmutableList());
    } else {
      return qualifiers.asList();
    }
  }

  /** Returns the constructors in {@code type} that are annotated with {@link Inject}. */
  public static ImmutableSet<ExecutableElement> injectedConstructors(TypeElement type) {
    return FluentIterable.from(constructorsIn(type.getEnclosedElements()))
        .filter(constructor -> isAnnotationPresent(constructor, Inject.class))
        .toSet();
  }

  /**
   * Gets the qualifiers annotation of a Kotlin Property. Finding these annotations involve finding
   * the synthetic method for annotations as described by the Kotlin metadata or finding the
   * corresponding MembersInjector method for the field, which also contains the qualifier
   * annotation.
   */
  private ImmutableCollection<? extends AnnotationMirror> getQualifiersForKotlinProperty(
      VariableElement fieldElement) {
    // TODO(bcorso): Consider moving this to KotlinMetadataUtil
    if (kotlinMetadataUtil.isMissingSyntheticPropertyForAnnotations(fieldElement)) {
      // If we detect that the synthetic method for annotations is missing, possibly due to the
      // element being from a compiled class, then find the MembersInjector that was generated
      // for the enclosing class and extract the qualifier information from it.
      TypeElement membersInjector =
          elements.getTypeElement(
              membersInjectorNameForType(asType(fieldElement.getEnclosingElement())));
      if (membersInjector != null) {
        String memberInjectedFieldSignature = memberInjectedFieldSignatureForVariable(fieldElement);
        // TODO(danysantiago): We have to iterate over all the injection methods for every qualifier
        //  look up. Making this N^2 when looking through all the injected fields. :(
        return ElementFilter.methodsIn(membersInjector.getEnclosedElements()).stream()
            .filter(
                method ->
                    getAnnotationMirror(method, TypeNames.INJECTED_FIELD_SIGNATURE)
                        .map(annotation -> getStringValue(annotation, "value"))
                        .map(memberInjectedFieldSignature::equals)
                        // If a method is not an @InjectedFieldSignature method then filter it out
                        .orElse(false))
            .collect(DaggerCollectors.toOptional())
            .map(this::getQualifiers)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        String.format(
                            "No matching InjectedFieldSignature for %1$s. This likely means that "
                                + "%1$s was compiled with an older, incompatible version of "
                                + "Dagger. Please update all Dagger dependencies to the same "
                                + "version.",
                            memberInjectedFieldSignature)));
      } else {
        throw new IllegalStateException(
            "No MembersInjector found for " + fieldElement.getEnclosingElement());
      }
    } else {
      return kotlinMetadataUtil.getSyntheticPropertyAnnotations(fieldElement, Qualifier.class);
    }
  }
}
