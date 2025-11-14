/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.internal.codegen.base;

import static androidx.room3.compiler.processing.XElementKt.isMethod;
import static androidx.room3.compiler.processing.XElementKt.isTypeElement;
import static androidx.room3.compiler.processing.XElementKt.isVariableElement;
import static androidx.room3.compiler.processing.XTypeKt.isArray;
import static androidx.room3.compiler.processing.compat.XConverters.toJavac;
import static androidx.room3.compiler.processing.compat.XConverters.toXProcessing;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.xprocessing.XAnnotationValues.getKindName;
import static dagger.internal.codegen.xprocessing.XElements.asEnumEntry;
import static dagger.internal.codegen.xprocessing.XElements.asExecutable;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.asTypeParameter;
import static dagger.internal.codegen.xprocessing.XElements.asVariable;
import static dagger.internal.codegen.xprocessing.XElements.getKindName;
import static dagger.internal.codegen.xprocessing.XElements.isEnumEntry;
import static dagger.internal.codegen.xprocessing.XElements.isExecutable;
import static dagger.internal.codegen.xprocessing.XElements.isTypeParameter;
import static dagger.internal.codegen.xprocessing.XExecutableTypes.asMethodType;
import static dagger.internal.codegen.xprocessing.XExecutableTypes.getKindName;
import static dagger.internal.codegen.xprocessing.XExecutableTypes.isMethodType;
import static dagger.internal.codegen.xprocessing.XTypes.asArray;
import static dagger.internal.codegen.xprocessing.XTypes.asTypeVariable;
import static dagger.internal.codegen.xprocessing.XTypes.getKindName;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.isTypeOf;
import static dagger.internal.codegen.xprocessing.XTypes.isTypeVariable;
import static dagger.internal.codegen.xprocessing.XTypes.isWildcard;

import androidx.room3.compiler.codegen.XClassName;
import androidx.room3.compiler.codegen.XTypeName;
import androidx.room3.compiler.processing.XAnnotation;
import androidx.room3.compiler.processing.XAnnotationValue;
import androidx.room3.compiler.processing.XElement;
import androidx.room3.compiler.processing.XExecutableElement;
import androidx.room3.compiler.processing.XExecutableType;
import androidx.room3.compiler.processing.XProcessingEnv;
import androidx.room3.compiler.processing.XProcessingEnv.Backend;
import androidx.room3.compiler.processing.XType;
import androidx.room3.compiler.processing.XTypeElement;
import androidx.room3.compiler.processing.compat.XConverters;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dagger.Reusable;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.xprocessing.XAnnotationValues;
import dagger.internal.codegen.xprocessing.XAnnotations;
import dagger.internal.codegen.xprocessing.XElements;
import dagger.internal.codegen.xprocessing.XExecutableTypes;
import dagger.internal.codegen.xprocessing.XTypeNames;
import dagger.internal.codegen.xprocessing.XTypes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;

/**
 * A fork of {@link com.google.auto.common.SuperficialValidation}.
 *
 * <p>This fork makes a couple changes from the original:
 *
 * <ul>
 *   <li>Throws {@link ValidationException} rather than returning {@code false} for invalid types.
 *   <li>Fixes a bug that incorrectly validates error types in annotations (b/213880825)
 *   <li>Exposes extra methods needed to validate various parts of an element rather than just the
 *       entire element.
 * </ul>
 */
@Reusable
public final class DaggerSuperficialValidation {
  /**
   * Returns the type element with the given class name or throws {@link ValidationException} if it
   * is not accessible in the current compilation.
   */
  public static XTypeElement requireTypeElement(
      XProcessingEnv processingEnv, XClassName className) {
    return requireTypeElement(processingEnv, className.getCanonicalName());
  }

  /**
   * Returns the type element with the given class name or throws {@link ValidationException} if it
   * is not accessible in the current compilation.
   */
  public static XTypeElement requireTypeElement(XProcessingEnv processingEnv, String className) {
    XTypeElement type = processingEnv.findTypeElement(className);
    if (type == null) {
      throw new ValidationException.KnownErrorType(className);
    }
    return type;
  }

  private final boolean isStrictValidationEnabled;
  private final XProcessingEnv processingEnv;

  @Inject
  DaggerSuperficialValidation(XProcessingEnv processingEnv, CompilerOptions compilerOptions) {
    this.processingEnv = processingEnv;
    this.isStrictValidationEnabled = compilerOptions.strictSuperficialValidation();
  }

  /**
   * Validates the {@link XElement#getType()} type of the given element.
   *
   * <p>Validating the type also validates any types it references, such as any type arguments or
   * type bounds. For an {@link XExecutableType}, the parameter and return types must be fully
   * defined, as must types declared in a {@code throws} clause or in the bounds of any type
   * parameters.
   */
  public void validateTypeOf(XElement element) {
    try {
      // In XProcessing, there is no generic way to get an element "asType" so we break this down
      // differently for different element kinds.
      if (isTypeElement(element)) {
        validateType(Ascii.toLowerCase(getKindName(element)), asTypeElement(element).getType());
      } else if (isVariableElement(element)) {
        validateType(
            Ascii.toLowerCase(getKindName(element)) + " type", asVariable(element).getType());
      } else if (isExecutable(element)) {
        validateExecutableType(asExecutable(element).getExecutableType());
      } else if (isEnumEntry(element)) {
        validateType(
            Ascii.toLowerCase(getKindName(element)),
            asEnumEntry(element).getEnumTypeElement().getType());
      }
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(element);
    }
  }

  /**
   * Validates the {@link XElement#getSuperType()} type of the given element.
   *
   * <p>Validating the type also validates any types it references, such as any type arguments or
   * type bounds.
   */
  public void validateSuperTypeOf(XTypeElement element) {
    try {
      validateType("superclass", element.getSuperType());
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(element);
    }
  }

  /**
   * Validates the {@link XExecutableElement#getThrownTypes()} types of the given element.
   *
   * <p>Validating the type also validates any types it references, such as any type arguments or
   * type bounds.
   */
  public void validateThrownTypesOf(XExecutableElement element) {
    try {
      validateTypes("thrown type", element.getThrownTypes());
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(element);
    }
  }

  /**
   * Validates the annotation types of the given element.
   *
   * <p>Note: this method does not validate annotation values. This method is useful if you care
   * about the annotation's annotations (e.g. to check for {@code Scope} or {@code Qualifier}). In
   * such cases, we just need to validate the annotation's type.
   */
  public void validateAnnotationTypesOf(XElement element) {
    element
        .getAllAnnotations()
        .forEach(annotation -> validateAnnotationTypeOf(element, annotation));
  }

  /**
   * Validates the type of the given annotation.
   *
   * <p>The annotation is assumed to be annotating the given element, but this is not checked. The
   * element is only in the error message if a {@link ValidatationException} is thrown.
   *
   * <p>Note: this method does not validate annotation values. This method is useful if you care
   * about the annotation's annotations (e.g. to check for {@code Scope} or {@code Qualifier}). In
   * such cases, we just need to validate the annotation's type.
   */
  // TODO(bcorso): See CL/427767370 for suggestions to make this API clearer.
  public void validateAnnotationTypeOf(XElement element, XAnnotation annotation) {
    try {
      validateType("annotation type", annotation.getType());
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(annotation).append(element);
    }
  }

  /** Validate the annotations of the given element. */
  public void validateAnnotationsOf(XElement element) {
    try {
      validateAnnotations(element.getAllAnnotations());
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(element);
    }
  }

  public void validateAnnotationOf(XElement element, XAnnotation annotation) {
    try {
      validateAnnotation(annotation);
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(element);
    }
  }

  /**
   * Validate the type hierarchy for the given type (with the given type description) within the
   * given element.
   *
   * <p>Validation includes all superclasses, interfaces, and type parameters of those types.
   */
  public void validateTypeHierarchyOf(String typeDescription, XElement element, XType type) {
    try {
      validateTypeHierarchy(typeDescription, type, new HashSet<>());
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(element);
    }
  }

  private void validateTypeHierarchy(String desc, XType type, Set<XTypeName> visited) {
    if (!visited.add(type.asTypeName())) {
      return;
    }
    validateType(desc, type);
    try {
      if (isArray(type)) {
        validateTypeHierarchy("array component type", asArray(type).getComponentType(), visited);
      } else if (isDeclared(type)) {
        type.getTypeArguments()
            .forEach(typeArg -> validateTypeHierarchy("type argument", typeArg, visited));
        type.getSuperTypes()
            .forEach(supertype -> validateTypeHierarchy("supertype", supertype, visited));
      } else if (isWildcard(type) && type.extendsBound() != null) {
        validateTypeHierarchy("extends bound type", type.extendsBound(), visited);
      } else if (isTypeVariable(type)) {
        asTypeVariable(type)
            .getUpperBounds()
            .forEach(bound -> validateTypeHierarchy("type variable bound type", bound, visited));
      }
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(desc, type);
    }
  }

  /**
   * Returns true if all of the given elements return true from {@link #validateElement(XElement)}.
   */
  private void validateElements(Collection<? extends XElement> elements) {
    elements.forEach(this::validateElement);
  }

  /**
   * Returns true if all types referenced by the given element are defined. The exact meaning of
   * this depends on the kind of element. For packages, it means that all annotations on the package
   * are fully defined. For other element kinds, it means that types referenced by the element,
   * anything it contains, and any of its annotations element are all defined.
   */
  public void validateElement(XElement element) {
    checkNotNull(element);

    // Validate the annotations first since these are common to all element kinds. We don't
    // need to wrap these in try-catch because the *Of() methods are already wrapped.
    validateAnnotationsOf(element);

    // Validate enclosed elements based on the given element's kind.
    try {
      if (isTypeElement(element)) {
        XTypeElement typeElement = asTypeElement(element);
        validateElements(typeElement.getTypeParameters());
        validateTypes("interface", typeElement.getSuperInterfaces());
        if (typeElement.getSuperType() != null) {
          validateType("superclass", typeElement.getSuperType());
        }
        // TODO (b/286313067) move the logic to ComponentValidator once the validation logic is
        // split into individual validators to satisfy different needs.
        // Dagger doesn't use components' static method, therefore, they shouldn't be validated to
        // be able to stop component generation.
        if (typeElement.hasAnnotation(XTypeNames.COMPONENT)) {
          validateElements(
              typeElement.getEnclosedElements().stream()
                  .filter(member -> !XElements.isStatic(member))
                  .collect(toImmutableList()));
        } else {
          validateElements(typeElement.getEnclosedElements());
        }
      } else if (isExecutable(element)) {
        if (isMethod(element)) {
          validateType("return type", asMethod(element).getReturnType());
        }
        XExecutableElement executableElement = asExecutable(element);
        validateTypes("thrown type", executableElement.getThrownTypes());
        validateElements(executableElement.getTypeParameters());
        validateElements(executableElement.getParameters());
      } else if (isTypeParameter(element)) {
        validateTypes("bound type", asTypeParameter(element).getBounds());
      }
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(element);
    }

    // Validate the type last. This allows errors on more specific elements to be caught above.
    // E.g. errors on parameters will be attributed to the parameter elements rather than the method
    // type, which generally leads to nicer error messages. We don't need to wrap these in try-catch
    // because the *Of() methods are already wrapped.
    validateTypeOf(element);
  }

  private void validateTypes(String desc, Collection<? extends XType> types) {
    types.forEach(type -> validateType(desc, type));
  }

  /**
   * Returns true if the given type is fully defined. This means that the type itself is defined, as
   * are any types it references, such as any type arguments or type bounds.
   */
  private void validateType(String desc, XType type) {
    checkNotNull(type);
    try {
      if (isArray(type)) {
        validateType("array component type", asArray(type).getComponentType());
      } else if (isDeclared(type)) {
        if (isStrictValidationEnabled) {
          // There's a bug in TypeVisitor which will visit the visitDeclared() method rather than
          // visitError() even when it's an ERROR kind. Thus, we check the kind directly here and
          // fail validation if it's an ERROR kind (see b/213880825).
          if (isErrorKind(type)) {
            throw new ValidationException.KnownErrorType(type);
          }
        }
        type.getTypeArguments().forEach(typeArg -> validateType("type argument", typeArg));
      } else if (isWildcard(type)) {
        if (type.extendsBound() != null) {
          validateType("extends bound type", type.extendsBound());
        }
      } else if (isErrorKind(type)) {
        throw new ValidationException.KnownErrorType(type);
      }
    } catch (RuntimeException e) {
      throw ValidationException.from(e).append(desc, type);
    }
  }

  // TODO(bcorso): Consider moving this over to XProcessing. There's some complication due to
  // b/248552462 and the fact that XProcessing also uses the error.NonExistentClass type for invalid
  // types in KSP, which we may want to keep as error kinds in KSP.
  private boolean isErrorKind(XType type) {
    // https://youtrack.jetbrains.com/issue/KT-34193/Kapt-CorrectErrorTypes-doesnt-work-for-generics
    // XProcessing treats 'error.NonExistentClass' as an error type. However, due to the bug in KAPT
    // (linked above), 'error.NonExistentClass' can still be referenced in the stub classes even
    // when 'correctErrorTypes=true' is enabled. Thus, we can't treat 'error.NonExistentClass' as an
    // actual error type, as that would completely prevent processing of stubs that exhibit this
    // bug. This behavior also matches how things work in Javac, as 'error.NonExistentClass' is
    // treated as a TypeKind.DECLARED rather than a TypeKind.ERROR since the type is a real class
    // that exists on the classpath.
    return type.isError()
        && !(processingEnv.getBackend() == Backend.JAVAC
            && type.getTypeName().toString().contentEquals("error.NonExistentClass"));
  }

  /**
   * Returns true if the given type is fully defined. This means that the parameter and return types
   * must be fully defined, as must types declared in a {@code throws} clause or in the bounds of
   * any type parameters.
   */
  private void validateExecutableType(XExecutableType type) {
    try {
      validateTypes("parameter type", type.getParameterTypes());
      validateTypes("thrown type", type.getThrownTypes());
      validateTypes("type variable", getTypeVariables(type));
      if (isMethodType(type)) {
        validateType("return type", asMethodType(type).getReturnType());
      }
    } catch (RuntimeException e) {
      throw ValidationException.from(e).append(type);
    }
  }

  private ImmutableList<XType> getTypeVariables(XExecutableType executableType) {
    switch (processingEnv.getBackend()) {
      case JAVAC:
        return toJavac(executableType).getTypeVariables().stream()
            .map(typeVariable -> toXProcessing(typeVariable, processingEnv))
            .collect(toImmutableList());
      case KSP:
        // TODO(b/247851395): Add a way to get type variables as XTypes from XExecutableType --
        // currently, we can only get TypeVariableNames from XMethodType. For now, just skip
        // validating type variables of methods in KSP.
        return ImmutableList.of();
    }
    throw new AssertionError("Unexpected backend: " + processingEnv.getBackend());
  }

  private void validateAnnotations(Collection<XAnnotation> annotations) {
    annotations.forEach(this::validateAnnotation);
  }

  private void validateAnnotation(XAnnotation annotation) {
    try {
      validateType("annotation type", annotation.getType());
      try {
        // Note: We separate this into its own try-catch since there's a bug where we could get an
        // error when getting the annotation values due to b/264089557. This way we will at least
        // report the name of the annotation in the error message.
        validateAnnotationValues(getDefaultValues(annotation));
        validateAnnotationValues(annotation.getAnnotationValues());
      } catch (RuntimeException exception) {
        throw ValidationException.from(exception).append(annotation);
      }
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception)
          .append(
              "annotation type: "
                  + (annotation.getType().isError()
                      ? annotation.getName() // SUPPRESS_GET_NAME_CHECK
                      : annotation.getClassName().canonicalName()));
    }
  }

  private ImmutableList<XAnnotationValue> getDefaultValues(XAnnotation annotation) {
    switch (processingEnv.getBackend()) {
      case JAVAC:
        return annotation.getTypeElement().getDeclaredMethods().stream()
            .map(XConverters::toJavac)
            .filter(method -> method.getDefaultValue() != null)
            .map(method -> toXProcessing(method.getDefaultValue(), method, processingEnv))
            .collect(toImmutableList());
      case KSP:
        // TODO(b/231170716): Add a generic way to retrieve default values from XAnnotation
        // For now, just ignore them in KSP when doing validation.
        return ImmutableList.of();
    }
    throw new AssertionError("Unexpected backend: " + processingEnv.getBackend());
  }

  private void validateAnnotationValues(Collection<XAnnotationValue> values) {
    values.forEach(this::validateAnnotationValue);
  }

  private void validateAnnotationValue(XAnnotationValue value) {
    try {
      XType expectedType = value.getValueType();

      // TODO(b/249834057): In KSP error types in annotation values are just null, so check this
      // first and throw KnownErrorType of "<error>" to match Javac for now.
      if (processingEnv.getBackend() == Backend.KSP && value.getValue() == null) {
        throw new ValidationException.KnownErrorType("<error>");
      }

      if (value.hasListValue()) {
        validateAnnotationValues(value.asAnnotationValueList());
      } else if (value.hasAnnotationValue()) {
        validateIsEquivalentType(value.asAnnotation().getType(), expectedType);
        validateAnnotation(value.asAnnotation());
      } else if (value.hasEnumValue()) {
        validateIsEquivalentType(value.asEnum().getEnumTypeElement().getType(), expectedType);
        validateElement(value.asEnum());
      } else if (value.hasTypeValue()) {
        validateType("annotation value type", value.asType());
      } else {
        // Validates all other types, e.g. primitives and String values.
        validateIsTypeOf(expectedType, value.getValue().getClass());
      }
    } catch (RuntimeException e) {
      throw ValidationException.from(e).append(value);
    }
  }

  private void validateIsTypeOf(XType expectedType, Class<?> clazz) {
    // TODO(b/248633751): We get the XClassName via an XTypeElement rather than XClassName.get()
    // because the latter does not handle interop types correctly.
    XClassName actualClassName =
        processingEnv.requireTypeElement(clazz.getCanonicalName()).asClassName();
    if (!isTypeOf(expectedType.boxed(), actualClassName)) {
      throw new ValidationException.UnknownErrorType()
          .append(
              String.format(
                  "Expected type %s, but got %s",
                  expectedType.boxed().asTypeName(),
                  actualClassName));
    }
  }

  private void validateIsEquivalentType(XType type, XType expectedType) {
    if (!XTypes.equivalence().equivalent(type, expectedType)) {
      throw new ValidationException.KnownErrorType(type);
    }
  }

  /**
   * A runtime exception that can be used during superficial validation to collect information about
   * unexpected exceptions during validation.
   */
  public abstract static class ValidationException extends RuntimeException {
    /** A {@link ValidationException} that originated from an unexpected exception. */
    public static final class UnexpectedException extends ValidationException {
      private UnexpectedException(Throwable throwable) {
        super(throwable);
      }
    }

    /** A {@link ValidationException} that originated from a known error type. */
    public static final class KnownErrorType extends ValidationException {
      private final String errorTypeName;

      private KnownErrorType(XType errorType) {
        this.errorTypeName = XTypes.toStableString(errorType);
      }

      private KnownErrorType(String errorTypeName) {
        this.errorTypeName = errorTypeName;
      }

      public String getErrorTypeName() {
        return errorTypeName;
      }
    }

    /** A {@link ValidationException} that originated from an unknown error type. */
    public static final class UnknownErrorType extends ValidationException {}

    private static ValidationException from(Throwable throwable) {
      if (throwable instanceof ValidationException) {
        // We only ever create one instance of the ValidationException.
        return (ValidationException) throwable;
      } else if (throwable instanceof TypeNotPresentException) {
        // XProcessing can throw TypeNotPresentException, so grab the error type from there if so.
        return new KnownErrorType(((TypeNotPresentException) throwable).typeName());
      }
      return new UnexpectedException(throwable);
    }

    private Optional<XElement> lastReportedElement = Optional.empty();
    private final List<String> messages = new ArrayList<>();

    private ValidationException() {
      super("");
    }

    private ValidationException(Throwable throwable) {
      super("", throwable);
    }

    /**
     * Appends a message for the given element and returns this instance of {@link
     * ValidationException}
     */
    private ValidationException append(XElement element) {
      lastReportedElement = Optional.of(element);
      return append(getMessageForElement(element));
    }

    /**
     * Appends a message for the given type and returns this instance of {@link ValidationException}
     */
    private ValidationException append(String desc, XType type) {
      return append(
          String.format(
              "type (%s %s): %s",
              getKindName(type),
              desc,
              XTypes.toStableString(type)));
    }

    /**
     * Appends a message for the given executable type and returns this instance of {@link
     * ValidationException}
     */
    private ValidationException append(XExecutableType type) {
      return append(
          String.format(
              "type (EXECUTABLE %s): %s",
              Ascii.toLowerCase(getKindName(type)),
              XExecutableTypes.toStableString(type)));
    }
    /**
     * Appends a message for the given annotation and returns this instance of {@link
     * ValidationException}
     */
    private ValidationException append(XAnnotation annotation) {
      // Note: Calling #toString() directly on the annotation throws NPE (b/216180336).
      return append(String.format("annotation: %s", XAnnotations.toStableString(annotation)));
    }

    /** Appends the given message and returns this instance of {@link ValidationException} */
    @CanIgnoreReturnValue
    protected ValidationException append(String message) {
      messages.add(message);
      return this;
    }

    /**
     * Appends a message for the given annotation value and returns this instance of {@link
     * ValidationException}
     */
    private ValidationException append(XAnnotationValue value) {
      return append(
          String.format(
              "annotation value (%s): %s=%s",
              getKindName(value),
              value.getName(),  // SUPPRESS_GET_NAME_CHECK
              XAnnotationValues.toStableString(value)));
    }

    @Override
    public String getMessage() {
      return String.format("\n  Validation trace:\n    => %s", getTrace());
    }

    public String getTrace() {
      return String.join("\n    => ", getMessageInternal().reverse());
    }

    private ImmutableList<String> getMessageInternal() {
      if (!lastReportedElement.isPresent()) {
        return ImmutableList.copyOf(messages);
      }
      // Append any enclosing element information if needed.
      List<String> newMessages = new ArrayList<>(messages);
      XElement element = lastReportedElement.get();
      while (shouldAppendEnclosingElement(element)) {
        element = element.getEnclosingElement();
        newMessages.add(getMessageForElement(element));
      }
      return ImmutableList.copyOf(newMessages);
    }

    private static boolean shouldAppendEnclosingElement(XElement element) {
      return element.getEnclosingElement() != null
          // We don't report enclosing elements for types because the type name should contain any
          // enclosing type and package information we need.
          && !isTypeElement(element)
          && (isExecutable(element.getEnclosingElement())
              || isTypeElement(element.getEnclosingElement()));
    }

    private String getMessageForElement(XElement element) {
      return String.format(
          "element (%s): %s",
          Ascii.toUpperCase(getKindName(element)),
          XElements.toStableString(element));
    }
  }
}
