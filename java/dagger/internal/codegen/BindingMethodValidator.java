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

import static com.google.common.base.Verify.verifyNotNull;
import static dagger.internal.codegen.DaggerElements.getAnnotationMirror;
import static dagger.internal.codegen.DaggerElements.isAnyAnnotationPresent;
import static dagger.internal.codegen.InjectionAnnotations.getQualifiers;
import static dagger.internal.codegen.MapKeys.getMapKeys;
import static dagger.internal.codegen.Scopes.scopesOf;
import static dagger.internal.codegen.Util.reentrantComputeIfAbsent;
import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.TYPEVAR;
import static javax.lang.model.type.TypeKind.VOID;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import dagger.MapKey;
import dagger.Provides;
import dagger.model.Scope;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoMap;
import dagger.producers.Produces;
import java.lang.annotation.Annotation;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** A validator for methods that represent binding declarations. */
abstract class BindingMethodValidator {

  private final DaggerElements elements;
  private final DaggerTypes types;
  private final DependencyRequestValidator dependencyRequestValidator;
  private final Class<? extends Annotation> methodAnnotation;
  private final ImmutableSet<? extends Class<? extends Annotation>> enclosingElementAnnotations;
  private final Abstractness abstractness;
  private final ExceptionSuperclass exceptionSuperclass;
  private final Map<ExecutableElement, ValidationReport<ExecutableElement>> cache = new HashMap<>();
  private final AllowsMultibindings allowsMultibindings;
  private final AllowsScoping allowsScoping;

  /**
   * Creates a validator object.
   *
   * @param methodAnnotation the annotation on a method that identifies it as a binding method
   * @param enclosingElementAnnotation the method must be declared in a class or interface annotated
   *     with this annotation
   */
  protected BindingMethodValidator(
      DaggerElements elements,
      DaggerTypes types,
      DependencyRequestValidator dependencyRequestValidator,
      Class<? extends Annotation> methodAnnotation,
      Class<? extends Annotation> enclosingElementAnnotation,
      Abstractness abstractness,
      ExceptionSuperclass exceptionSuperclass,
      AllowsMultibindings allowsMultibindings,
      AllowsScoping allowsScoping) {
    this(
        elements,
        types,
        methodAnnotation,
        ImmutableSet.of(enclosingElementAnnotation),
        dependencyRequestValidator,
        abstractness,
        exceptionSuperclass,
        allowsMultibindings,
        allowsScoping);
  }

  /**
   * Creates a validator object.
   *
   * @param methodAnnotation the annotation on a method that identifies it as a binding method
   * @param enclosingElementAnnotations the method must be declared in a class or interface
   *     annotated with one of these annotations
   */
  protected BindingMethodValidator(
      DaggerElements elements,
      DaggerTypes types,
      Class<? extends Annotation> methodAnnotation,
      Iterable<? extends Class<? extends Annotation>> enclosingElementAnnotations,
      DependencyRequestValidator dependencyRequestValidator,
      Abstractness abstractness,
      ExceptionSuperclass exceptionSuperclass,
      AllowsMultibindings allowsMultibindings,
      AllowsScoping allowsScoping) {
    this.elements = elements;
    this.types = types;
    this.methodAnnotation = methodAnnotation;
    this.enclosingElementAnnotations = ImmutableSet.copyOf(enclosingElementAnnotations);
    this.dependencyRequestValidator = dependencyRequestValidator;
    this.abstractness = abstractness;
    this.exceptionSuperclass = exceptionSuperclass;
    this.allowsMultibindings = allowsMultibindings;
    this.allowsScoping = allowsScoping;
  }

  /** The annotation that identifies binding methods validated by this object. */
  final Class<? extends Annotation> methodAnnotation() {
    return methodAnnotation;
  }

  /**
   * Returns an error message of the form "@<i>annotation</i> methods <i>rule</i>", where
   * <i>rule</i> comes from calling {@link String#format(String, Object...)} on {@code ruleFormat}
   * and the other arguments.
   */
  @FormatMethod
  protected final String bindingMethods(String ruleFormat, Object... args) {
    return new Formatter()
        .format("@%s methods ", methodAnnotation.getSimpleName())
        .format(ruleFormat, args)
        .toString();
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
    checkScopes(builder);
    checkParameters(builder);
  }

  /**
   * Adds an error if the method is not declared in a class or interface annotated with one of the
   * {@link #enclosingElementAnnotations}.
   */
  private void checkEnclosingElement(ValidationReport.Builder<ExecutableElement> builder) {
    if (!isAnyAnnotationPresent(
        builder.getSubject().getEnclosingElement(), enclosingElementAnnotations)) {
      builder.addError(
          bindingMethods(
              "can only be present within a @%s",
              enclosingElementAnnotations
                  .stream()
                  .map(Class::getSimpleName)
                  .collect(joining(" or @"))));
    }
  }

  /** Adds an error if the method is generic. */
  private void checkTypeParameters(ValidationReport.Builder<ExecutableElement> builder) {
    if (!builder.getSubject().getTypeParameters().isEmpty()) {
      builder.addError(bindingMethods("may not have type parameters"));
    }
  }

  /** Adds an error if the method is private. */
  private void checkNotPrivate(ValidationReport.Builder<ExecutableElement> builder) {
    if (builder.getSubject().getModifiers().contains(PRIVATE)) {
      builder.addError(bindingMethods("cannot be private"));
    }
  }

  /** Adds an error if the method is abstract but must not be, or is not and must be. */
  private void checkAbstractness(ValidationReport.Builder<ExecutableElement> builder) {
    boolean isAbstract = builder.getSubject().getModifiers().contains(ABSTRACT);
    switch (abstractness) {
      case MUST_BE_ABSTRACT:
        if (!isAbstract) {
          builder.addError(bindingMethods("must be abstract"));
        }
        break;

      case MUST_BE_CONCRETE:
        if (isAbstract) {
          builder.addError(bindingMethods("cannot be abstract"));
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
      builder.addError(bindingMethods("must return a value (not void)"));
    } else if (!(kind.isPrimitive()
        || kind.equals(DECLARED)
        || kind.equals(ARRAY)
        || kind.equals(TYPEVAR))) {
      builder.addError(badReturnTypeMessage());
    }
  }

  /** The error message when a non-{@code void} binding method returns a bad type. */
  protected String badReturnTypeMessage() {
    return bindingMethods("must return a primitive, an array, a type variable, or a declared type");
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
        builder.addError(bindingMethods("annotated with @ElementsIntoSet cannot return a raw Set"));
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
        builder.addError(
            bindingMethods("may not use more than one @Qualifier"),
            builder.getSubject(),
            qualifier);
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
          builder.addError(bindingMethods("of type map must declare a map key"));
          break;
        case 1:
          break;
        default:
          builder.addError(bindingMethods("may not have more than one map key"));
          break;
      }
    } else if (!mapKeys.isEmpty()) {
      builder.addError(bindingMethods("of non map type cannot declare a map key"));
    }
  }

  /**
   * Adds errors if the method doesn't allow {@linkplain MultibindingAnnotations multibinding
   * annotations} and has any, or if it does allow them but has more than one, or if it has a
   * multibinding annotation and its {@link Provides} or {@link Produces} annotation has a {@code
   * type} parameter.
   */
  protected void checkMultibindings(ValidationReport.Builder<ExecutableElement> builder) {
    ImmutableSet<AnnotationMirror> multibindingAnnotations =
        MultibindingAnnotations.forMethod(builder.getSubject());

    switch (allowsMultibindings) {
      case NO_MULTIBINDINGS:
        for (AnnotationMirror annotation : multibindingAnnotations) {
          builder.addError(
              bindingMethods("cannot have multibinding annotations"),
              builder.getSubject(),
              annotation);
        }
        break;

      case ALLOWS_MULTIBINDINGS:
        if (multibindingAnnotations.size() > 1) {
          for (AnnotationMirror annotation : multibindingAnnotations) {
            builder.addError(
                bindingMethods("cannot have more than one multibinding annotation"),
                builder.getSubject(),
                annotation);
          }
        }
        break;
    }

    AnnotationMirror bindingAnnotationMirror =
        getAnnotationMirror(builder.getSubject(), methodAnnotation).get();
    boolean usesProvidesType = false;
    for (ExecutableElement member : bindingAnnotationMirror.getElementValues().keySet()) {
      usesProvidesType |= member.getSimpleName().contentEquals("type");
    }
    if (usesProvidesType && !multibindingAnnotations.isEmpty()) {
      builder.addError(
          "@Provides.type cannot be used with multibinding annotations", builder.getSubject());
    }
  }

  /** Adds an error if the method has more than one {@linkplain Scope scope} annotation. */
  private void checkScopes(ValidationReport.Builder<ExecutableElement> builder) {
    ImmutableSet<Scope> scopes = scopesOf(builder.getSubject());
    String error = null;
    switch (allowsScoping) {
      case ALLOWS_SCOPING:
        if (scopes.size() <= 1) {
          return;
        }
        error = bindingMethods("cannot use more than one @Scope");
        break;
      case NO_SCOPING:
        error = bindingMethods("cannot be scoped");
        break;
    }
    verifyNotNull(error);
    for (Scope scope : scopes) {
      builder.addError(error, builder.getSubject(), scope.scopeAnnotation());
    }
  }

  /** Adds errors for the method parameters. */
  protected void checkParameters(ValidationReport.Builder<ExecutableElement> builder) {
    for (VariableElement parameter : builder.getSubject().getParameters()) {
      checkParameter(builder, parameter);
    }
  }

  /**
   * Adds errors for a method parameter. This implementation reports an error if the parameter has
   * more than one qualifier.
   */
  protected void checkParameter(
      ValidationReport.Builder<ExecutableElement> builder, VariableElement parameter) {
    dependencyRequestValidator.validateDependencyRequest(builder, parameter, parameter.asType());
  }

  /** Adds an error if the method returns a {@linkplain FrameworkTypes framework type}. */
  protected void checkFrameworkType(ValidationReport.Builder<ExecutableElement> builder) {
    if (FrameworkTypes.isFrameworkType(builder.getSubject().getReturnType())) {
      builder.addError(bindingMethods("must not return framework types"));
    }
  }

  /**
   * The error message when an {@link ElementsIntoSet @ElementsIntoSet} or {@code SET_VALUES} method
   * returns a bad type.
   */
  protected String badSetValuesTypeMessage() {
    return bindingMethods("annotated with @ElementsIntoSet must return a Set");
  }

  /** An abstract/concrete restriction on methods. */
  protected enum Abstractness {
    MUST_BE_ABSTRACT,
    MUST_BE_CONCRETE
  }

  /**
   * The exception class that all {@code throws}-declared throwables must extend, other than {@link
   * Error}.
   */
  protected enum ExceptionSuperclass {
    /** Methods may not declare any throwable types. */
    NO_EXCEPTIONS {
      @Override
      protected String errorMessage(BindingMethodValidator validator) {
        return validator.bindingMethods("may not throw");
      }

      @Override
      protected void checkThrows(
          BindingMethodValidator validator, ValidationReport.Builder<ExecutableElement> builder) {
        if (!builder.getSubject().getThrownTypes().isEmpty()) {
          builder.addError(validator.bindingMethods("may not throw"));
          return;
        }
      }
    },

    /** Methods may throw checked or unchecked exceptions or errors. */
    EXCEPTION(Exception.class) {
      @Override
      protected String errorMessage(BindingMethodValidator validator) {
        return validator.bindingMethods(
            "may only throw unchecked exceptions or exceptions subclassing Exception");
      }
    },

    /** Methods may throw unchecked exceptions or errors. */
    RUNTIME_EXCEPTION(RuntimeException.class) {
      @Override
      protected String errorMessage(BindingMethodValidator validator) {
        return validator.bindingMethods("may only throw unchecked exceptions");
      }
    },
    ;

    private final Class<? extends Exception> superclass;

    ExceptionSuperclass() {
      this(null);
    }

    ExceptionSuperclass(Class<? extends Exception> superclass) {
      this.superclass = superclass;
    }

    /**
     * Adds an error if the method declares throws anything but an {@link Error} or an appropriate
     * subtype of {@link Exception}.
     *
     * <p>This method is overridden in {@link #NO_EXCEPTIONS}.
     */
    protected void checkThrows(
        BindingMethodValidator validator, ValidationReport.Builder<ExecutableElement> builder) {
      TypeMirror exceptionSupertype = validator.elements.getTypeElement(superclass).asType();
      TypeMirror errorType = validator.elements.getTypeElement(Error.class).asType();
      for (TypeMirror thrownType : builder.getSubject().getThrownTypes()) {
        if (!validator.types.isSubtype(thrownType, exceptionSupertype)
            && !validator.types.isSubtype(thrownType, errorType)) {
          builder.addError(errorMessage(validator));
          break;
        }
      }
    }

    protected abstract String errorMessage(BindingMethodValidator validator);
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

  /** How to check scoping annotations. */
  protected enum AllowsScoping {
    /** This method disallows scope annotations, so check that none are present. */
    NO_SCOPING,

    /** This method allows scoping, so validate that there's at most one. */
    ALLOWS_SCOPING,
    ;
  }
}
