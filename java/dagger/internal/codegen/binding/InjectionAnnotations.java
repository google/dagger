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

import static androidx.room.compiler.processing.XElementKt.isConstructor;
import static androidx.room.compiler.processing.XElementKt.isField;
import static androidx.room.compiler.processing.XElementKt.isMethodParameter;
import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static androidx.room.compiler.processing.compat.XConverters.toXProcessing;
import static com.google.auto.common.MoreElements.asType;
import static com.google.auto.common.MoreElements.asVariable;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.base.MoreAnnotationValues.getStringValue;
import static dagger.internal.codegen.binding.SourceFiles.factoryNameForElement;
import static dagger.internal.codegen.binding.SourceFiles.memberInjectedFieldSignatureForVariable;
import static dagger.internal.codegen.binding.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.langmodel.DaggerElements.getAnnotationMirror;
import static dagger.internal.codegen.langmodel.DaggerElements.isAnnotationPresent;
import static dagger.internal.codegen.xprocessing.XElements.asMethodParameter;
import static dagger.internal.codegen.xprocessing.XElements.closestEnclosingTypeElement;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.util.ElementFilter.constructorsIn;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.compat.XConverters;
import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.SuperficialValidation;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.base.Scopes;
import dagger.internal.codegen.extension.DaggerCollectors;
import dagger.internal.codegen.extension.DaggerStreams;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.kotlin.KotlinMetadataUtil;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.spi.model.DaggerAnnotation;
import dagger.spi.model.Scope;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
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

  private final XProcessingEnv processingEnv;
  private final DaggerElements elements;
  private final KotlinMetadataUtil kotlinMetadataUtil;

  @Inject
  InjectionAnnotations(
      XProcessingEnv processingEnv,
      DaggerElements elements,
      KotlinMetadataUtil kotlinMetadataUtil) {
    this.processingEnv = processingEnv;
    this.elements = elements;
    this.kotlinMetadataUtil = kotlinMetadataUtil;
  }

  /**
   * Returns the scope on the inject constructor's type if it exists.
   *
   * @throws IllegalArgumentException if the given constructor is not an inject constructor or there
   *   are more than one scope annotations present.
   */
  public Optional<Scope> getScope(XConstructorElement injectConstructor) {
    return getScopes(injectConstructor).stream().collect(toOptional());
  }

  /**
   * Returns the scopes on the inject constructor's type, or an empty set if none exist.
   *
   * @throws IllegalArgumentException if the given constructor is not an inject constructor.
   */
  public ImmutableSet<Scope> getScopes(XConstructorElement injectConstructor) {
    checkArgument(injectConstructor.hasAnyAnnotation(TypeNames.INJECT, TypeNames.ASSISTED_INJECT));
    XTypeElement factory = processingEnv.findTypeElement(factoryNameForElement(injectConstructor));
    if (factory != null && factory.hasAnnotation(TypeNames.SCOPE_METADATA)) {
      String scopeName = factory.getAnnotation(TypeNames.SCOPE_METADATA).getAsString("value");
      if (scopeName.isEmpty()) {
        return ImmutableSet.of();
      }
      ImmutableSet<XAnnotation> scopeAnnotations =
          injectConstructor.getEnclosingElement().getAllAnnotations().stream()
              .filter(
                  annotation ->
                      scopeName.contentEquals(
                          annotation.getType().getTypeElement().getQualifiedName()))
              .collect(toImmutableSet());
      checkState(
          !scopeAnnotations.isEmpty(),
          "Expected scope, %s, on inject type, %s.",
          scopeName,
          injectConstructor.getEnclosingElement());
      checkState(
          scopeAnnotations.size() == 1,
          "Expected a single scope, %s, on inject type, %s, but found multiple: %s",
          scopeName,
          injectConstructor.getEnclosingElement(),
          scopeAnnotations);
      XAnnotation scopeAnnotation = getOnlyElement(scopeAnnotations);
      // Do superficial validation before we convert to a Scope, otherwise the @Scope annotation may
      // appear to be missing from the annotation if it's no longer on the classpath.
      DaggerSuperficialValidation.strictValidateAnnotationOf(
          injectConstructor.getEnclosingElement(), scopeAnnotation);
      return ImmutableSet.of(Scope.scope(DaggerAnnotation.from(scopeAnnotation)));
    }

    // Fall back to validating all annotations if the ScopeMetadata isn't available.
    DaggerSuperficialValidation
        .strictValidateAnnotationsOf(injectConstructor.getEnclosingElement());
    return Scopes.scopesOf(injectConstructor.getEnclosingElement());
  }

  public Optional<XAnnotation> getQualifier(XElement element) {
    return getQualifier(toJavac(element)).map(qualifier -> toXProcessing(qualifier, processingEnv));
  }

  public Optional<AnnotationMirror> getQualifier(Element e) {
    // TODO(b/213880825): Eventually everything will use the new DaggerSuperficialValidation, but
    // for now we only support elements within inject types. Other elements keep the old validation.
    if (isFromInjectType(e)) {
      DaggerSuperficialValidation.validateTypeOf(toXProcessing(e, processingEnv));
    } else {
      if (!SuperficialValidation.validateElement(e)) {
        throw new TypeNotPresentException(e.toString(), null);
      }
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

  private boolean isFromInjectType(Element element) {
    switch (element.getKind()) {
      case FIELD:
        return isAnnotationPresent(element, TypeNames.INJECT);
      case PARAMETER:
        // Handles both @Inject constructors and @Inject methods.
        return isAnnotationPresent(element.getEnclosingElement(), TypeNames.INJECT)
            || isAnnotationPresent(element.getEnclosingElement(), TypeNames.ASSISTED_INJECT);
      default:
        return false;
    }
  }

  public ImmutableSet<XAnnotation> getQualifiers(XElement element) {
    return getQualifiers(toJavac(element)).stream()
        .map(qualifier -> toXProcessing(qualifier, processingEnv))
        .collect(toImmutableSet());
  }

  public ImmutableCollection<? extends AnnotationMirror> getQualifiers(Element element) {
    ImmutableSet<? extends AnnotationMirror> qualifiers =
        isFromInjectType(element)
            ? getQualifiersForInjectType(toXProcessing(element, processingEnv)).stream()
                .map(XConverters::toJavac)
                .collect(toImmutableSet())
            : DaggerElements.getAnnotatedAnnotations(element, TypeNames.QUALIFIER);
    if (element.getKind() == ElementKind.FIELD
        // static injected fields are not supported, no need to get qualifier from kotlin metadata
        && !element.getModifiers().contains(STATIC)
        && isAnnotationPresent(element, TypeNames.INJECT)
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

  private ImmutableSet<XAnnotation> getQualifiersForInjectType(XElement element) {
    ClassName generatedName = generatedClassNameForInjectType(element);
    XTypeElement generatedType = processingEnv.findTypeElement(generatedName);
    if (generatedType != null && generatedType.hasAnnotation(TypeNames.QUALIFIER_METADATA)) {
      ImmutableSet<String> qualifierNames =
          ImmutableSet.copyOf(
              generatedType.getAnnotation(TypeNames.QUALIFIER_METADATA).getAsStringList("value"));
      if (qualifierNames.isEmpty()) {
        return ImmutableSet.of();
      }
      ImmutableSet<XAnnotation> qualifierAnnotations =
          element.getAllAnnotations().stream()
              .filter(
                  annotation ->
                      qualifierNames.contains(
                          annotation.getType().getTypeElement().getQualifiedName()))
              .collect(toImmutableSet());
      if (qualifierAnnotations.isEmpty()) {
        return ImmutableSet.of();
      }
      // We should be guaranteed that there's exactly one qualifier since the existance of
      // @QualifierMetadata means that this element has already been processed and multiple
      // qualifiers would have been caught already.
      XAnnotation qualifierAnnotation = getOnlyElement(qualifierAnnotations);
      // Ensure the annotation type is superficially valid before we check for @Qualifier, otherwise
      // the @Qualifier marker may appear to be missing from the annotation (b/213880825).
      DaggerSuperficialValidation.strictValidateAnnotationOf(element, qualifierAnnotation);
      // TODO(b/213880825): The @Qualifier annotation may appear to be missing from the annotation
      // even though we know it's a qualifier because the type is no longer on the classpath. Once
      // we fix issue #3136, the superficial validation above will fail in this case, but until then
      // keep the same behavior and return an empty set.
      return qualifierAnnotation.getType().getTypeElement().hasAnnotation(TypeNames.QUALIFIER)
          ? ImmutableSet.of(qualifierAnnotation)
          : ImmutableSet.of();
    }

    // Fall back to validating all annotations if the ScopeMetadata isn't available.
    DaggerSuperficialValidation.strictValidateAnnotationsOf(element);
    return ImmutableSet.copyOf(element.getAnnotationsAnnotatedWith(TypeNames.QUALIFIER));
  }

  private ClassName generatedClassNameForInjectType(XElement element) {
    checkArgument(isFromInjectType(toJavac(element)));
    if (isField(element)) {
      return membersInjectorNameForType(closestEnclosingTypeElement(element));
    } else if (isMethodParameter(element)) {
      XExecutableElement executableElement = asMethodParameter(element).getEnclosingMethodElement();
      return isConstructor(executableElement)
          ? factoryNameForElement(executableElement)
          : membersInjectorNameForType(closestEnclosingTypeElement(element));
    }
    throw new AssertionError("Found unexpected element: " + element);
  }

  /** Returns the constructors in {@code type} that are annotated with {@link Inject}. */
  public static ImmutableSet<XConstructorElement> injectedConstructors(XTypeElement type) {
    return type.getConstructors().stream()
        .filter(constructor -> constructor.hasAnnotation(TypeNames.INJECT))
        .collect(toImmutableSet());
  }

  /** Returns the constructors in {@code type} that are annotated with {@link Inject}. */
  public static ImmutableSet<ExecutableElement> injectedConstructors(TypeElement type) {
    return FluentIterable.from(constructorsIn(type.getEnclosedElements()))
        .filter(constructor -> isAnnotationPresent(constructor, TypeNames.INJECT))
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
      return kotlinMetadataUtil.getSyntheticPropertyAnnotations(fieldElement, TypeNames.QUALIFIER);
    }
  }
}
