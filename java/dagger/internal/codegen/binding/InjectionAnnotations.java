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
import static androidx.room.compiler.processing.XElementKt.isMethod;
import static androidx.room.compiler.processing.XElementKt.isMethodParameter;
import static androidx.room.compiler.processing.XElementKt.isTypeElement;
import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static androidx.room.compiler.processing.compat.XConverters.toXProcessing;
import static com.google.auto.common.MoreElements.asType;
import static com.google.auto.common.MoreElements.asVariable;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.base.MoreAnnotationValues.getStringValue;
import static dagger.internal.codegen.binding.SourceFiles.factoryNameForElement;
import static dagger.internal.codegen.binding.SourceFiles.memberInjectedFieldSignatureForVariable;
import static dagger.internal.codegen.binding.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.extension.DaggerCollectors.onlyElement;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.langmodel.DaggerElements.getAnnotationMirror;
import static dagger.internal.codegen.langmodel.DaggerElements.isAnyAnnotationPresent;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XElements.asMethodParameter;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.closestEnclosingTypeElement;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.util.ElementFilter.constructorsIn;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.common.AnnotationMirrors;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.base.DaggerSuperficialValidation;
import dagger.internal.codegen.compileroption.CompilerOptions;
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
  private final DaggerSuperficialValidation superficialValidation;
  private final CompilerOptions compilerOptions;

  @Inject
  InjectionAnnotations(
      XProcessingEnv processingEnv,
      DaggerElements elements,
      KotlinMetadataUtil kotlinMetadataUtil,
      DaggerSuperficialValidation superficialValidation,
      CompilerOptions compilerOptions) {
    this.processingEnv = processingEnv;
    this.elements = elements;
    this.kotlinMetadataUtil = kotlinMetadataUtil;
    this.superficialValidation = superficialValidation;
    this.compilerOptions = compilerOptions;
  }

  /**
   * Returns the scope on the given element if it exists.
   *
   * <p>The {@code ScopeMetadata} is used to avoid superficial validation on unnecessary
   * annotations. If the {@code ScopeMetadata} does not exist, then all annotations must be
   * superficially validated before we can determine if they are scopes or not.
   *
   * @throws IllegalArgumentException if the given element has more than one scope.
   */
  public Optional<Scope> getScope(XElement element) {
    return getScopes(element).stream().collect(toOptional());
  }

  /**
   * Returns the scopes on the given element, or an empty set if none exist.
   *
   * <p>Note: Use {@link #getScope(XElement)} if the usage of the scope on the given element has
   * already been validated and known to be unique. This method should typically only be used in the
   * process of such validation.
   *
   * <p>The {@code ScopeMetadata} is used to avoid superficial validation on unnecessary
   * annotations. If the {@code ScopeMetadata} does not exist, then all annotations must be
   * superficially validated before we can determine if they are scopes or not.
   */
  public ImmutableSet<Scope> getScopes(XElement element) {
    superficialValidation.validateTypeOf(element);
    ImmutableSet<Scope> scopes =
        getScopesFromScopeMetadata(element)
            .orElseGet(
                () -> {
                  // Validate the annotation types before we check for @Scope, otherwise the @Scope
                  // annotation may appear to be missing (b/213880825).
                  superficialValidation.validateAnnotationTypesOf(element);
                  return element.getAllAnnotations().stream()
                      .filter(InjectionAnnotations::hasScopeAnnotation)
                      .map(DaggerAnnotation::from)
                      .map(Scope::scope)
                      .collect(toImmutableSet());
                });

    // Fully validate each scope to ensure its values are also valid.
    scopes.stream()
        .map(scope -> scope.scopeAnnotation().xprocessing())
        .forEach(scope -> superficialValidation.validateAnnotationOf(element, scope));
    return scopes;
  }

  private Optional<ImmutableSet<Scope>> getScopesFromScopeMetadata(XElement element) {
    Optional<XAnnotation> scopeMetadata = getScopeMetadata(element);
    if (!scopeMetadata.isPresent()) {
      return Optional.empty();
    }
    String scopeName = scopeMetadata.get().getAsString("value");
    if (scopeName.isEmpty()) {
      return Optional.of(ImmutableSet.of());
    }
    XAnnotation scopeAnnotation =
        element.getAllAnnotations().stream()
            .filter(
                annotation ->
                    scopeName.contentEquals(
                        annotation.getType().getTypeElement().getQualifiedName()))
            .collect(onlyElement());
    // Do superficial validation before we convert to a Scope, otherwise the @Scope annotation may
    // appear to be missing from the annotation if it's no longer on the classpath.
    superficialValidation.validateAnnotationTypeOf(element, scopeAnnotation);

    // If strictSuperficialValidation is disabled, then we fall back to the old behavior where
    // we may potentially miss a scope rather than report an exception.
    if (compilerOptions.strictSuperficialValidation()) {
      return Optional.of(ImmutableSet.of(Scope.scope(DaggerAnnotation.from(scopeAnnotation))));
    } else {
      return Scope.isScope(DaggerAnnotation.from(scopeAnnotation))
          ? Optional.of(ImmutableSet.of(Scope.scope(DaggerAnnotation.from(scopeAnnotation))))
          : Optional.empty();
    }
  }

  private Optional<XAnnotation> getScopeMetadata(XElement element) {
    return getGeneratedNameForScopeMetadata(element)
        .flatMap(factoryName -> Optional.ofNullable(processingEnv.findTypeElement(factoryName)))
        .flatMap(factory -> Optional.ofNullable(factory.getAnnotation(TypeNames.SCOPE_METADATA)));
  }

  private Optional<ClassName> getGeneratedNameForScopeMetadata(XElement element) {
    // Currently, we only support ScopeMetadata for inject-constructor types and provides methods.
    if (isTypeElement(element)) {
      return asTypeElement(element).getConstructors().stream()
          .filter(InjectionAnnotations::hasInjectOrAssistedInjectAnnotation)
          .findFirst()
          .map(SourceFiles::factoryNameForElement);
    } else if (isMethod(element) && element.hasAnnotation(TypeNames.PROVIDES)) {
      return Optional.of(factoryNameForElement(asMethod(element)));
    }
    return Optional.empty();
  }

  /*
   * Returns the qualifier on the given element if it exists.
   *
   * <p>The {@code QualifierMetadata} is used to avoid superficial validation on unnecessary
   * annotations. If the {@code QualifierMetadata} does not exist, then all annotations must be
   * superficially validated before we can determine if they are qualifiers or not.
   *
   * @throws IllegalArgumentException if the given element has more than one qualifier.
   */
  public Optional<XAnnotation> getQualifier(XElement element) {
    return getQualifier(toJavac(element)).map(qualifier -> toXProcessing(qualifier, processingEnv));
  }

  /*
   * Returns the qualifier on the given element if it exists.
   *
   * <p>The {@code QualifierMetadata} is used to avoid superficial validation on unnecessary
   * annotations. If the {@code QualifierMetadata} does not exist, then all annotations must be
   * superficially validated before we can determine if they are qualifiers or not.
   *
   * @throws IllegalArgumentException if the given element has more than one qualifier.
   */
  public Optional<AnnotationMirror> getQualifier(Element e) {
    checkNotNull(e);
    ImmutableList<? extends AnnotationMirror> qualifierAnnotations = getQualifiers(e);
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

  /*
   * Returns the qualifiers on the given element, or an empty set if none exist.
   *
   * <p>The {@code QualifierMetadata} is used to avoid superficial validation on unnecessary
   * annotations. If the {@code QualifierMetadata} does not exist, then all annotations must be
   * superficially validated before we can determine if they are qualifiers or not.
   */
  public ImmutableSet<XAnnotation> getQualifiers(XElement element) {
    return getQualifiers(toJavac(element)).stream()
        .map(qualifier -> toXProcessing(qualifier, processingEnv))
        .collect(toImmutableSet());
  }

  /*
   * Returns the qualifiers on the given element, or an empty set if none exist.
   *
   * <p>The {@code QualifierMetadata} is used to avoid superficial validation on unnecessary
   * annotations. If the {@code QualifierMetadata} does not exist, then all annotations must be
   * superficially validated before we can determine if they are qualifiers or not.
   */
  public ImmutableList<? extends AnnotationMirror> getQualifiers(Element element) {
    superficialValidation.validateTypeOf(toXProcessing(element, processingEnv));
    ImmutableSet<? extends AnnotationMirror> qualifiers =
        getQualifiersFromQualifierMetadata(element)
            .orElseGet(
                () -> {
                  // Validate the annotation types before we check for @Qualifier, otherwise the
                  // @Qualifier annotation may appear to be missing (b/213880825).
                  superficialValidation.validateAnnotationTypesOf(element);
                  return element.getAnnotationMirrors().stream()
                      .filter(InjectionAnnotations::hasQualifierAnnotation)
                      .collect(toImmutableSet());
                });

    if (element.getKind() == ElementKind.FIELD
        // static injected fields are not supported, no need to get qualifier from kotlin metadata
        && !element.getModifiers().contains(STATIC)
        && hasInjectAnnotation(element)
        && kotlinMetadataUtil.hasMetadata(element)) {
      qualifiers =
          Stream.concat(
                  qualifiers.stream(), getQualifiersForKotlinProperty(asVariable(element)).stream())
              .map(EQUIVALENCE::wrap) // Wrap in equivalence to deduplicate
              .distinct()
              .map(Wrapper::get)
              .collect(DaggerStreams.toImmutableSet());
    }

    // Fully validate each qualifier to ensure its values are also valid.
    qualifiers.forEach(qualifier -> superficialValidation.validateAnnotationOf(element, qualifier));

    return qualifiers.asList();
  }

  private Optional<ImmutableSet<? extends AnnotationMirror>> getQualifiersFromQualifierMetadata(
      Element javaElement) {
    XElement element = toXProcessing(javaElement, processingEnv);
    Optional<XAnnotation> qualifierMetadata = getQualifierMetadata(element);
    if (!qualifierMetadata.isPresent()) {
      return Optional.empty();
    }
    ImmutableSet<String> qualifierNames =
        ImmutableSet.copyOf(qualifierMetadata.get().getAsStringList("value"));
    if (qualifierNames.isEmpty()) {
      return Optional.of(ImmutableSet.of());
    }
    ImmutableSet<XAnnotation> qualifierAnnotations =
        element.getAllAnnotations().stream()
            .filter(
                annotation ->
                    qualifierNames.contains(
                        annotation.getType().getTypeElement().getQualifiedName()))
            .collect(toImmutableSet());
    if (qualifierAnnotations.isEmpty()) {
      return Optional.of(ImmutableSet.of());
    }
    // We should be guaranteed that there's exactly one qualifier since the existance of
    // @QualifierMetadata means that this element has already been processed and multiple
    // qualifiers would have been caught already.
    XAnnotation qualifierAnnotation = getOnlyElement(qualifierAnnotations);

    // Ensure the annotation type is superficially valid before we check for @Qualifier, otherwise
    // the @Qualifier marker may appear to be missing from the annotation (b/213880825).
    superficialValidation.validateAnnotationTypeOf(element, qualifierAnnotation);
    if (compilerOptions.strictSuperficialValidation()) {
      return Optional.of(ImmutableSet.of(toJavac(qualifierAnnotation)));
    } else {
      // If strictSuperficialValidation is disabled, then we fall back to the old behavior where
      // we may potentially miss a qualifier rather than report an exception.
      return hasQualifierAnnotation(toJavac(qualifierAnnotation))
          ? Optional.of(ImmutableSet.of(toJavac(qualifierAnnotation)))
          : Optional.empty();
    }
  }

  /**
   * Returns {@code QualifierMetadata} annotation.
   *
   * <p>Currently, {@code QualifierMetadata} is only associated with inject constructor parameters,
   * inject fields, inject method parameters, provide methods, and provide method parameters.
   */
  private Optional<XAnnotation> getQualifierMetadata(XElement element) {
    return getGeneratedNameForQualifierMetadata(element)
        .flatMap(name -> Optional.ofNullable(processingEnv.findTypeElement(name)))
        .flatMap(type -> Optional.ofNullable(type.getAnnotation(TypeNames.QUALIFIER_METADATA)));
  }

  private Optional<ClassName> getGeneratedNameForQualifierMetadata(XElement element) {
    // Currently we only support @QualifierMetadata for @Inject fields, @Inject method parameters,
    // @Inject constructor parameters, @Provides methods, and @Provides method parameters.
    if (isField(element) && hasInjectAnnotation(element)) {
      return Optional.of(membersInjectorNameForType(closestEnclosingTypeElement(element)));
    } else if (isMethod(element) && element.hasAnnotation(TypeNames.PROVIDES)) {
      return Optional.of(factoryNameForElement(asMethod(element)));
    } else if (isMethodParameter(element)) {
      XExecutableElement executableElement = asMethodParameter(element).getEnclosingMethodElement();
      if (isConstructor(executableElement)
          && hasInjectOrAssistedInjectAnnotation(executableElement)) {
        return Optional.of(factoryNameForElement(executableElement));
      }
      if (isMethod(executableElement) && hasInjectAnnotation(executableElement)) {
        return Optional.of(membersInjectorNameForType(closestEnclosingTypeElement(element)));
      }
      if (isMethod(executableElement) && executableElement.hasAnnotation(TypeNames.PROVIDES)) {
        return Optional.of(factoryNameForElement(executableElement));
      }
    }
    return Optional.empty();
  }

  /** Returns the constructors in {@code type} that are annotated with {@link Inject}. */
  public static ImmutableSet<XConstructorElement> injectedConstructors(XTypeElement type) {
    return type.getConstructors().stream()
        .filter(InjectionAnnotations::hasInjectAnnotation)
        .collect(toImmutableSet());
  }

  /** Returns the constructors in {@code type} that are annotated with {@link Inject}. */
  public static ImmutableSet<ExecutableElement> injectedConstructors(TypeElement type) {
    return FluentIterable.from(constructorsIn(type.getEnclosedElements()))
        .filter(InjectionAnnotations::hasInjectAnnotation)
        .toSet();
  }

  private static boolean hasQualifierAnnotation(AnnotationMirror annotation) {
    return isAnyAnnotationPresent(
        annotation.getAnnotationType().asElement(), TypeNames.QUALIFIER, TypeNames.QUALIFIER_JAVAX);
  }

  private static boolean hasScopeAnnotation(XAnnotation annotation) {
    return annotation
        .getType()
        .getTypeElement()
        .hasAnyAnnotation(TypeNames.SCOPE, TypeNames.SCOPE_JAVAX);
  }

  /** Returns true if the given element is annotated with {@link Inject}. */
  public static boolean hasInjectAnnotation(XElement element) {
    return element.hasAnyAnnotation(TypeNames.INJECT, TypeNames.INJECT_JAVAX);
  }

  /** Returns true if the given element is annotated with {@link Inject}. */
  public static boolean hasInjectAnnotation(Element element) {
    return isAnyAnnotationPresent(element, TypeNames.INJECT, TypeNames.INJECT_JAVAX);
  }

  /** Returns true if the given element is annotated with {@link Inject}. */
  public static boolean hasInjectOrAssistedInjectAnnotation(XElement element) {
    return element.hasAnyAnnotation(
        TypeNames.INJECT, TypeNames.INJECT_JAVAX, TypeNames.ASSISTED_INJECT);
  }

  /** Returns true if the given element is annotated with {@link Inject}. */
  public static boolean hasInjectOrAssistedInjectAnnotation(Element element) {
    return isAnyAnnotationPresent(
        element, TypeNames.INJECT, TypeNames.INJECT_JAVAX, TypeNames.ASSISTED_INJECT);
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
      return ImmutableSet.<AnnotationMirror>builder()
          .addAll(
              kotlinMetadataUtil.getSyntheticPropertyAnnotations(fieldElement, TypeNames.QUALIFIER))
          .addAll(
              kotlinMetadataUtil.getSyntheticPropertyAnnotations(
                  fieldElement, TypeNames.QUALIFIER_JAVAX))
          .build();
    }
  }
}
