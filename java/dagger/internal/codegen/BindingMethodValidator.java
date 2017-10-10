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

import static dagger.internal.codegen.DaggerElements.getAnnotationMirror;
import static dagger.internal.codegen.DaggerElements.isAnyAnnotationPresent;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_ABSTRACT;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_MULTIPLE_QUALIFIERS;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_MUST_RETURN_A_VALUE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_NOT_ABSTRACT;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_NOT_IN_MODULE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_NOT_MAP_HAS_MAP_KEY;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_PRIVATE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_RETURN_TYPE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_SET_VALUES_RAW_SET;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_SET_VALUES_RETURN_SET;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_THROWS;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_THROWS_ANY;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_THROWS_CHECKED;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_TYPE_PARAMETER;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_WITH_MULTIPLE_MAP_KEYS;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_WITH_NO_MAP_KEY;
import static dagger.internal.codegen.ErrorMessages.MULTIBINDING_ANNOTATION_CONFLICTS_WITH_BINDING_ANNOTATION_ENUM;
import static dagger.internal.codegen.ErrorMessages.MULTIPLE_MULTIBINDING_ANNOTATIONS_ON_METHOD;
import static dagger.internal.codegen.InjectionAnnotations.getQualifiers;
import static dagger.internal.codegen.MapKeys.getMapKeys;
import static dagger.internal.codegen.Util.reentrantComputeIfAbsent;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.TYPEVAR;
import static javax.lang.model.type.TypeKind.VOID;

import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import dagger.MapKey;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoMap;
import dagger.producers.Produces;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/** A validator for methods that represent binding declarations. */
abstract class BindingMethodValidator {

  private final Elements elements;
  private final Types types;
  private final Class<? extends Annotation> methodAnnotation;
  private final ImmutableSet<? extends Class<? extends Annotation>> enclosingElementAnnotations;
  private final Abstractness abstractness;
  private final ExceptionSuperclass exceptionSuperclass;
  private final Map<ExecutableElement, ValidationReport<ExecutableElement>> cache = new HashMap<>();
  private final AllowsMultibindings allowsMultibindings;

  /**
   * Creates a validator object.
   *
   * @param methodAnnotation the annotation on a method that identifies it as a binding method
   * @param enclosingElementAnnotation the method must be declared in a class or interface annotated
   *     with this annotation
   */
  protected BindingMethodValidator(
      Elements elements,
      Types types,
      Class<? extends Annotation> methodAnnotation,
      Class<? extends Annotation> enclosingElementAnnotation,
      Abstractness abstractness,
      ExceptionSuperclass exceptionSuperclass,
      AllowsMultibindings allowsMultibindings) {
    this(
        elements,
        types,
        methodAnnotation,
        ImmutableSet.of(enclosingElementAnnotation),
        abstractness,
        exceptionSuperclass,
        allowsMultibindings);
  }

  /**
   * Creates a validator object.
   *
   * @param methodAnnotation the annotation on a method that identifies it as a binding method
   * @param enclosingElementAnnotations the method must be declared in a class or interface
   *     annotated with one of these annotations
   */
  protected BindingMethodValidator(
      Elements elements,
      Types types,
      Class<? extends Annotation> methodAnnotation,
      Iterable<? extends Class<? extends Annotation>> enclosingElementAnnotations,
      Abstractness abstractness,
      ExceptionSuperclass exceptionSuperclass,
      AllowsMultibindings allowsMultibindings) {
    this.elements = elements;
    this.types = types;
    this.methodAnnotation = methodAnnotation;
    this.enclosingElementAnnotations = ImmutableSet.copyOf(enclosingElementAnnotations);
    this.abstractness = abstractness;
    this.exceptionSuperclass = exceptionSuperclass;
    this.allowsMultibindings = allowsMultibindings;
  }
  
  /** The annotation that identifies methods validated by this object. */
  Class<? extends Annotation> methodAnnotation() {
    return methodAnnotation;
  }

  /** Returns a {@link ValidationReport} for {@code method}. */
  final ValidationReport<ExecutableElement> validate(ExecutableElement method) {
    return reentrantComputeIfAbsent(cache, method, this::validateUncached);
  }

  private ValidationReport<ExecutableElement> validateUncached(ExecutableElement m) {
    ValidationReport.Builder<ExecutableElement> report = ValidationReport.about(m);
    checkMethod(report);
    return report.build();
  }

  /** Checks the method for validity. Adds errors to {@code builder}. */
  @OverridingMethodsMustInvokeSuper
  protected void checkMethod(ValidationReport.Builder<ExecutableElement> builder) {
    checkEnclosingElement(builder);
    checkTypeParameters(builder);
    checkNotPrivate(builder);
    checkAbstractness(builder);
    checkReturnType(builder);
    checkThrows(builder);
    checkQualifiers(builder);
    checkMapKeys(builder);
    checkMultibindings(builder);
  }

  /**
   * Adds an error if the method is not declared in a class or interface annotated with one of the
   * {@link #enclosingElementAnnotations}.
   */
  private void checkEnclosingElement(ValidationReport.Builder<ExecutableElement> builder) {
    if (!isAnyAnnotationPresent(
        builder.getSubject().getEnclosingElement(), enclosingElementAnnotations)) {
      builder.addError(
          formatErrorMessage(
              BINDING_METHOD_NOT_IN_MODULE,
              FluentIterable.from(enclosingElementAnnotations)
                  .transform(Class::getSimpleName)
                  .join(Joiner.on(" or @"))));
    }
  }

  /** Adds an error if the method is generic. */
  private void checkTypeParameters(ValidationReport.Builder<ExecutableElement> builder) {
    if (!builder.getSubject().getTypeParameters().isEmpty()) {
      builder.addError(formatErrorMessage(BINDING_METHOD_TYPE_PARAMETER));
    }
  }

  /** Adds an error if the method is private. */
  private void checkNotPrivate(ValidationReport.Builder<ExecutableElement> builder) {
    if (builder.getSubject().getModifiers().contains(PRIVATE)) {
      builder.addError(formatErrorMessage(BINDING_METHOD_PRIVATE));
    }
  }

  /** Adds an error if the method is abstract but must not be, or is not and must be. */
  private void checkAbstractness(ValidationReport.Builder<ExecutableElement> builder) {
    boolean isAbstract = builder.getSubject().getModifiers().contains(ABSTRACT);
    switch (abstractness) {
      case MUST_BE_ABSTRACT:
        if (!isAbstract) {
          builder.addError(formatErrorMessage(BINDING_METHOD_NOT_ABSTRACT));
        }
        break;

      case MUST_BE_CONCRETE:
        if (isAbstract) {
          builder.addError(formatErrorMessage(BINDING_METHOD_ABSTRACT));
        }
        break;

      default:
        throw new AssertionError();
    }
  }

  /**
   * Adds an error if the return type is not appropriate for the method.
   *
   * <p>Adds an error if the method doesn't return a primitive, array, declared type, or type
   * variable.
   *
   * <p>If the method is not a multibinding contribution, adds an error if it returns a framework
   * type.
   *
   * <p>If the method is a {@link ElementsIntoSet @ElementsIntoSet} or {@code SET_VALUES}
   * contribution, adds an error if the method doesn't return a {@code Set<T>} for some {@code T}
   */
  protected void checkReturnType(ValidationReport.Builder<ExecutableElement> builder) {
    switch (ContributionType.fromBindingMethod(builder.getSubject())) {
      case UNIQUE:
        /* Validate that a unique binding is not attempting to bind a framework type. This
         * validation is only appropriate for unique bindings because multibindings may collect
         * framework types.  E.g. Set<Provider<Foo>> is perfectly reasonable. */
        checkFrameworkType(builder);
        // fall through

      case SET:
      case MAP:
        checkKeyType(builder, builder.getSubject().getReturnType());
        break;

      case SET_VALUES:
        checkSetValuesType(builder);
        break;

      default:
        throw new AssertionError();
    }
  }

  /**
   * Adds an error if {@code keyType} is not a primitive, declared type, array, or type variable.
   */
  protected void checkKeyType(
      ValidationReport.Builder<ExecutableElement> builder, TypeMirror keyType) {
    TypeKind kind = keyType.getKind();
    if (kind.equals(VOID)) {
      builder.addError(formatErrorMessage(BINDING_METHOD_MUST_RETURN_A_VALUE));
    } else if (!(kind.isPrimitive()
        || kind.equals(DECLARED)
        || kind.equals(ARRAY)
        || kind.equals(TYPEVAR))) {
      builder.addError(badReturnTypeMessage());
    }
  }

  /** The error message when a non-{@code void} binding method returns a bad type. */
  protected String badReturnTypeMessage() {
    return formatErrorMessage(BINDING_METHOD_RETURN_TYPE);
  }

  /**
   * Adds an error if an {@link ElementsIntoSet @ElementsIntoSet} or {@code SET_VALUES} method
   * doesn't return a {@code Set<T>} for a reasonable {@code T}.
   */
  // TODO(gak): should we allow "covariant return" for set values?
  protected void checkSetValuesType(ValidationReport.Builder<ExecutableElement> builder) {
    checkSetValuesType(builder, builder.getSubject().getReturnType());
  }

  /** Adds an error if {@code type} is not a {@code Set<T>} for a reasonable {@code T}. */
  protected final void checkSetValuesType(
      ValidationReport.Builder<ExecutableElement> builder, TypeMirror type) {
    if (!SetType.isSet(type)) {
      builder.addError(badSetValuesTypeMessage());
    } else {
      SetType setType = SetType.from(type);
      if (setType.isRawType()) {
        builder.addError(formatErrorMessage(BINDING_METHOD_SET_VALUES_RAW_SET));
      } else {
        checkKeyType(builder, setType.elementType());
      }
    }
  }

  /**
   * Adds an error if the method declares throws anything but an {@link Error} or an appropriate
   * subtype of {@link Exception}.
   */
  private void checkThrows(ValidationReport.Builder<ExecutableElement> builder) {
    exceptionSuperclass.checkThrows(this, builder);
  }

  /** Adds an error if the method has more than one {@linkplain Qualifier qualifier} annotation. */
  protected void checkQualifiers(ValidationReport.Builder<ExecutableElement> builder) {
    ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(builder.getSubject());
    if (qualifiers.size() > 1) {
      for (AnnotationMirror qualifier : qualifiers) {
        builder.addError(BINDING_METHOD_MULTIPLE_QUALIFIERS, builder.getSubject(), qualifier);
      }
    }
  }

  /**
   * Adds an error if an {@link IntoMap @IntoMap} or {@code MAP} method doesn't have exactly one
   * {@link MapKey @MapKey} annotation, or if a method that is neither {@link IntoMap @IntoMap} nor
   * {@code MAP} has any.
   */
  protected void checkMapKeys(ValidationReport.Builder<ExecutableElement> builder) {
    if (!allowsMultibindings.allowsMultibindings()) {
      return;
    }
    ImmutableSet<? extends AnnotationMirror> mapKeys = getMapKeys(builder.getSubject());
    if (ContributionType.fromBindingMethod(builder.getSubject()).equals(ContributionType.MAP)) {
      switch (mapKeys.size()) {
        case 0:
          builder.addError(formatErrorMessage(BINDING_METHOD_WITH_NO_MAP_KEY));
          break;
        case 1:
          break;
        default:
          builder.addError(formatErrorMessage(BINDING_METHOD_WITH_MULTIPLE_MAP_KEYS));
          break;
      }
    } else if (!mapKeys.isEmpty()) {
      builder.addError(formatErrorMessage(BINDING_METHOD_NOT_MAP_HAS_MAP_KEY));
    }
  }

  /**
   * Adds errors if the method has more than one {@linkplain MultibindingAnnotations multibinding
   * annotation} or if it has a multibinding annotation and its {@link Provides} or {@link Produces}
   * annotation has a {@code type} parameter.
   */
  protected void checkMultibindings(ValidationReport.Builder<ExecutableElement> builder) {
    if (!allowsMultibindings.allowsMultibindings()) {
      return;
    }
    ImmutableSet<AnnotationMirror> multibindingAnnotations =
        MultibindingAnnotations.forMethod(builder.getSubject());
    if (multibindingAnnotations.size() > 1) {
      for (AnnotationMirror annotation : multibindingAnnotations) {
        builder.addError(
            formatErrorMessage(MULTIPLE_MULTIBINDING_ANNOTATIONS_ON_METHOD),
            builder.getSubject(),
            annotation);
      }
    }

    AnnotationMirror bindingAnnotationMirror =
        getAnnotationMirror(builder.getSubject(), methodAnnotation).get();
    boolean usesProvidesType = false;
    for (ExecutableElement member : bindingAnnotationMirror.getElementValues().keySet()) {
      usesProvidesType |= member.getSimpleName().contentEquals("type");
    }
    if (usesProvidesType && !multibindingAnnotations.isEmpty()) {
      builder.addError(
          formatErrorMessage(MULTIBINDING_ANNOTATION_CONFLICTS_WITH_BINDING_ANNOTATION_ENUM),
          builder.getSubject());
    }
  }

  /** Adds an error if the method returns a {@linkplain FrameworkTypes framework type}. */
  protected void checkFrameworkType(ValidationReport.Builder<ExecutableElement> builder) {
    if (FrameworkTypes.isFrameworkType(builder.getSubject().getReturnType())) {
      builder.addError(formatErrorMessage(BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES));
    }
  }

  /**
   * Formats an error message whose first {@code %s} parameter should be replaced with the simple
   * name of the method annotation.
   */
  protected String formatErrorMessage(String format, Object... otherParameters) {
    return otherParameters.length == 0
        ? String.format(format, methodAnnotation.getSimpleName())
        : String.format(
            format, Lists.asList(methodAnnotation.getSimpleName(), otherParameters).toArray());
  }

  /**
   * The error message when an {@link ElementsIntoSet @ElementsIntoSet} or {@code SET_VALUES} method
   * returns a bad type.
   */
  protected String badSetValuesTypeMessage() {
    return formatErrorMessage(BINDING_METHOD_SET_VALUES_RETURN_SET);
  }

  /** An abstract/concrete restriction on methods. */
  protected enum Abstractness {
    MUST_BE_ABSTRACT,
    MUST_BE_CONCRETE
  }

  /**
   * The exception class that all {@code throws}-declared throwables must extend, other than
   * {@link Error}.
   */
  protected enum ExceptionSuperclass {
    /** Methods may not declare any throwable types. */
    NO_EXCEPTIONS {
      @Override
      protected void checkThrows(
          BindingMethodValidator validator, ValidationReport.Builder<ExecutableElement> builder) {
        if (!builder.getSubject().getThrownTypes().isEmpty()) {
          builder.addError(validator.formatErrorMessage(BINDING_METHOD_THROWS_ANY));
          return;
        }
      }
    },

    /** Methods may throw checked or unchecked exceptions or errors. */
    EXCEPTION(Exception.class, BINDING_METHOD_THROWS),

    /** Methods may throw unchecked exceptions or errors. */
    RUNTIME_EXCEPTION(RuntimeException.class, BINDING_METHOD_THROWS_CHECKED),
    ;

    private final Class<? extends Exception> superclass;
    private final String errorMessage;

    private ExceptionSuperclass() {
      this(null, null);
    }

    private ExceptionSuperclass(Class<? extends Exception> superclass, String errorMessage) {
      this.superclass = superclass;
      this.errorMessage = errorMessage;
    }

    /**
     * Adds an error if the method declares throws anything but an {@link Error} or an appropriate
     * subtype of {@link Exception}.
     *
     * <p>This method is overridden in {@link #NO_EXCEPTIONS}.
     */
    protected void checkThrows(
        BindingMethodValidator validator, ValidationReport.Builder<ExecutableElement> builder) {
      TypeMirror exceptionSupertype =
          validator.elements.getTypeElement(superclass.getCanonicalName()).asType();
      TypeMirror errorType =
          validator.elements.getTypeElement(Error.class.getCanonicalName()).asType();
      for (TypeMirror thrownType : builder.getSubject().getThrownTypes()) {
        if (!validator.types.isSubtype(thrownType, exceptionSupertype)
            && !validator.types.isSubtype(thrownType, errorType)) {
          builder.addError(validator.formatErrorMessage(errorMessage));
          break;
        }
      }
    }
  }

  /** Whether to check multibinding annotations. */
  protected enum AllowsMultibindings {
    /**
     * This method disallows multibinding annotations, so don't bother checking for their validity.
     * {@link MultibindingAnnotationsProcessingStep} will add errors if the method has any
     * multibinding annotations.
     */
    NO_MULTIBINDINGS,

    /** This method allows multibinding annotations, so validate them. */
    ALLOWS_MULTIBINDINGS,
    ;

    private boolean allowsMultibindings() {
      return this == ALLOWS_MULTIBINDINGS;
    }
  }
}
