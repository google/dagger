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
import static dagger.internal.codegen.InjectionAnnotations.getQualifiers;
import static dagger.internal.codegen.MapKeys.getMapKeys;
import static dagger.internal.codegen.Scopes.scopesOf;
import static dagger.internal.codegen.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.langmodel.DaggerElements.getAnnotationMirror;
import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.TYPEVAR;
import static javax.lang.model.type.TypeKind.VOID;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import dagger.MapKey;
import dagger.Provides;
import dagger.internal.codegen.ValidationReport.Builder;
import dagger.model.Key;
import dagger.model.Scope;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoMap;
import dagger.producers.Produces;
import java.lang.annotation.Annotation;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** A validator for elements that represent binding declarations. */
abstract class BindingElementValidator<E extends Element> {
  private final Class<? extends Annotation> bindingAnnotation;
  private final AllowsMultibindings allowsMultibindings;
  private final AllowsScoping allowsScoping;
  private final Map<E, ValidationReport<E>> cache = new HashMap<>();

  /**
   * Creates a validator object.
   *
   * @param bindingAnnotation the annotation on an element that identifies it as a binding element
   */
  protected BindingElementValidator(
      Class<? extends Annotation> bindingAnnotation,
      AllowsMultibindings allowsMultibindings,
      AllowsScoping allowsScoping) {
    this.bindingAnnotation = bindingAnnotation;
    this.allowsMultibindings = allowsMultibindings;
    this.allowsScoping = allowsScoping;
  }

  /**
   * Returns an error message of the form "<{@link #bindingElements()}> <i>rule</i>", where
   * <i>rule</i> comes from calling {@link String#format(String, Object...)} on {@code ruleFormat}
   * and the other arguments.
   */
  @FormatMethod
  protected final String bindingElements(String ruleFormat, Object... args) {
    return new Formatter().format("%s ", bindingElements()).format(ruleFormat, args).toString();
  }

  /**
   * The kind of elements that this validator validates. Should be plural. Used for error reporting.
   */
  protected abstract String bindingElements();

  /** The verb describing the {@link #bindingElementType(Builder)} in error messages. */
  // TODO(ronshapiro,dpb): improve the name of this method and it's documentation.
  protected abstract String bindingElementTypeVerb();

  /** Returns a {@link ValidationReport} for {@code element}. */
  final ValidationReport<E> validate(E element) {
    return reentrantComputeIfAbsent(cache, element, this::validateUncached);
  }

  private ValidationReport<E> validateUncached(E element) {
    ValidationReport.Builder<E> report = ValidationReport.about(element);
    checkElement(report);
    return report.build();
  }

  /** Checks the element for validity. Adds errors to {@code builder}. */
  @OverridingMethodsMustInvokeSuper
  protected void checkElement(ValidationReport.Builder<E> builder) {
    checkType(builder);
    checkQualifiers(builder);
    checkMapKeys(builder);
    checkMultibindings(builder);
    checkScopes(builder);
  }

  /**
   * The type declared by this binding element. This may differ from a binding's {@link Key#type()},
   * for example in multibindings. An {@link Optional#empty()} return value indicates that the
   * contributed type is ambiguous or missing, i.e. a {@link @BindsInstance} method with zero or
   * many parameters.
   */
  // TODO(dpb): should this be an ImmutableList<TypeMirror>, with this class checking the size?
  protected abstract Optional<TypeMirror> bindingElementType(ValidationReport.Builder<E> report);

  /**
   * Adds an error if the {@link #bindingElementType(ValidationReport.Builder) binding element type}
   * is not appropriate.
   *
   * <p>Adds an error if the type is not a primitive, array, declared type, or type variable.
   *
   * <p>If the binding is not a multibinding contribution, adds an error if the type is a framework
   * type.
   *
   * <p>If the element has {@link ElementsIntoSet @ElementsIntoSet} or {@code SET_VALUES}, adds an
   * error if the type is not a {@code Set<T>} for some {@code T}
   */
  protected void checkType(ValidationReport.Builder<E> builder) {
    switch (ContributionType.fromBindingElement(builder.getSubject())) {
      case UNIQUE:
        /* Validate that a unique binding is not attempting to bind a framework type. This
         * validation is only appropriate for unique bindings because multibindings may collect
         * framework types.  E.g. Set<Provider<Foo>> is perfectly reasonable. */
        checkFrameworkType(builder);
        // fall through

      case SET:
      case MAP:
        bindingElementType(builder).ifPresent(type -> checkKeyType(builder, type));
        break;

      case SET_VALUES:
        checkSetValuesType(builder);
    }
  }

  /**
   * Adds an error if {@code keyType} is not a primitive, declared type, array, or type variable.
   */
  protected void checkKeyType(ValidationReport.Builder<E> builder, TypeMirror keyType) {
    TypeKind kind = keyType.getKind();
    if (kind.equals(VOID)) {
      builder.addError(bindingElements("must %s a value (not void)", bindingElementTypeVerb()));
    } else if (!(kind.isPrimitive()
        || kind.equals(DECLARED)
        || kind.equals(ARRAY)
        || kind.equals(TYPEVAR))) {
      builder.addError(badTypeMessage());
    }
  }

  /** The error message when a binding element has a bad type. */
  protected String badTypeMessage() {
    return bindingElements(
        "must %s a primitive, an array, a type variable, or a declared type",
        bindingElementTypeVerb());
  }

  /**
   * Adds an error if the type for an element with {@link ElementsIntoSet @ElementsIntoSet} or
   * {@code SET_VALUES} is not a a {@code Set<T>} for a reasonable {@code T}.
   */
  // TODO(gak): should we allow "covariant return" for set values?
  protected void checkSetValuesType(ValidationReport.Builder<E> builder) {
    bindingElementType(builder).ifPresent(keyType -> checkSetValuesType(builder, keyType));
  }

  /** Adds an error if {@code type} is not a {@code Set<T>} for a reasonable {@code T}. */
  protected final void checkSetValuesType(ValidationReport.Builder<E> builder, TypeMirror type) {
    if (!SetType.isSet(type)) {
      builder.addError(elementsIntoSetNotASetMessage());
    } else {
      SetType setType = SetType.from(type);
      if (setType.isRawType()) {
        builder.addError(elementsIntoSetRawSetMessage());
      } else {
        checkKeyType(builder, setType.elementType());
      }
    }
  }

  /**
   * The error message when a the type for a binding element with {@link
   * ElementsIntoSet @ElementsIntoSet} or {@code SET_VALUES} is a not set type.
   */
  protected String elementsIntoSetNotASetMessage() {
    return bindingElements(
        "annotated with @ElementsIntoSet must %s a Set", bindingElementTypeVerb());
  }

  /**
   * The error message when a the type for a binding element with {@link
   * ElementsIntoSet @ElementsIntoSet} or {@code SET_VALUES} is a raw set.
   */
  protected String elementsIntoSetRawSetMessage() {
    return bindingElements(
        "annotated with @ElementsIntoSet cannot %s a raw Set", bindingElementTypeVerb());
  }

  /** Adds an error if the element has more than one {@linkplain Qualifier qualifier} annotation. */
  private void checkQualifiers(ValidationReport.Builder<E> builder) {
    ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(builder.getSubject());
    if (qualifiers.size() > 1) {
      for (AnnotationMirror qualifier : qualifiers) {
        builder.addError(
            bindingElements("may not use more than one @Qualifier"),
            builder.getSubject(),
            qualifier);
      }
    }
  }

  /**
   * Adds an error if an {@link IntoMap @IntoMap} element doesn't have exactly one {@link
   * MapKey @MapKey} annotation, or if an element that is {@link IntoMap @IntoMap} has any.
   */
  private void checkMapKeys(ValidationReport.Builder<E> builder) {
    if (!allowsMultibindings.allowsMultibindings()) {
      return;
    }
    ImmutableSet<? extends AnnotationMirror> mapKeys = getMapKeys(builder.getSubject());
    if (ContributionType.fromBindingElement(builder.getSubject()).equals(ContributionType.MAP)) {
      switch (mapKeys.size()) {
        case 0:
          builder.addError(bindingElements("of type map must declare a map key"));
          break;
        case 1:
          break;
        default:
          builder.addError(bindingElements("may not have more than one map key"));
          break;
      }
    } else if (!mapKeys.isEmpty()) {
      builder.addError(bindingElements("of non map type cannot declare a map key"));
    }
  }

  /**
   * Adds errors if:
   *
   * <ul>
   *   <li>the element doesn't allow {@linkplain MultibindingAnnotations multibinding annotations}
   *       and has any
   *   <li>the element does allow them but has more than one
   *   <li>the element has a multibinding annotation and its {@link Provides} or {@link Produces}
   *       annotation has a {@code type} parameter.
   * </ul>
   */
  private void checkMultibindings(ValidationReport.Builder<E> builder) {
    ImmutableSet<AnnotationMirror> multibindingAnnotations =
        MultibindingAnnotations.forElement(builder.getSubject());

    switch (allowsMultibindings) {
      case NO_MULTIBINDINGS:
        for (AnnotationMirror annotation : multibindingAnnotations) {
          builder.addError(
              bindingElements("cannot have multibinding annotations"),
              builder.getSubject(),
              annotation);
        }
        break;

      case ALLOWS_MULTIBINDINGS:
        if (multibindingAnnotations.size() > 1) {
          for (AnnotationMirror annotation : multibindingAnnotations) {
            builder.addError(
                bindingElements("cannot have more than one multibinding annotation"),
                builder.getSubject(),
                annotation);
          }
        }
        break;
    }

    // TODO(ronshapiro): move this into ProvidesMethodValidator
    if (bindingAnnotation.equals(Provides.class)) {
      AnnotationMirror bindingAnnotationMirror =
          getAnnotationMirror(builder.getSubject(), bindingAnnotation).get();
      boolean usesProvidesType = false;
      for (ExecutableElement member : bindingAnnotationMirror.getElementValues().keySet()) {
        usesProvidesType |= member.getSimpleName().contentEquals("type");
      }
      if (usesProvidesType && !multibindingAnnotations.isEmpty()) {
        builder.addError(
            "@Provides.type cannot be used with multibinding annotations", builder.getSubject());
      }
    }
  }

  /**
   * Adds an error if the element has a scope but doesn't allow scoping, or if it has more than one
   * {@linkplain Scope scope} annotation.
   */
  private void checkScopes(ValidationReport.Builder<E> builder) {
    ImmutableSet<Scope> scopes = scopesOf(builder.getSubject());
    String error = null;
    switch (allowsScoping) {
      case ALLOWS_SCOPING:
        if (scopes.size() <= 1) {
          return;
        }
        error = bindingElements("cannot use more than one @Scope");
        break;
      case NO_SCOPING:
        error = bindingElements("cannot be scoped");
        break;
    }
    verifyNotNull(error);
    for (Scope scope : scopes) {
      builder.addError(error, builder.getSubject(), scope.scopeAnnotation());
    }
  }

  /**
   * Adds an error if the {@link #bindingElementType(Builder) type} is a {@linkplain FrameworkTypes
   * framework type}.
   */
  private void checkFrameworkType(ValidationReport.Builder<E> builder) {
    if (bindingElementType(builder).filter(FrameworkTypes::isFrameworkType).isPresent()) {
      builder.addError(bindingElements("must not %s framework types", bindingElementTypeVerb()));
    }
  }

  /** Whether to check multibinding annotations. */
  enum AllowsMultibindings {
    /**
     * This element disallows multibinding annotations, so don't bother checking for their validity.
     * {@link MultibindingAnnotationsProcessingStep} will add errors if the element has any
     * multibinding annotations.
     */
    NO_MULTIBINDINGS,

    /** This element allows multibinding annotations, so validate them. */
    ALLOWS_MULTIBINDINGS,
    ;

    private boolean allowsMultibindings() {
      return this == ALLOWS_MULTIBINDINGS;
    }
  }

  /** How to check scoping annotations. */
  enum AllowsScoping {
    /** This element disallows scoping, so check that no scope annotations are present. */
    NO_SCOPING,

    /** This element allows scoping, so validate that there's at most one scope annotation. */
    ALLOWS_SCOPING,
    ;
  }
}
