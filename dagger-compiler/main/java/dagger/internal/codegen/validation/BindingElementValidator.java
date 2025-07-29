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

package dagger.internal.codegen.validation;

import static androidx.room.compiler.processing.XTypeKt.isArray;
import static androidx.room.compiler.processing.XTypeKt.isVoid;
import static com.google.common.base.Verify.verifyNotNull;
import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.isAssistedFactoryType;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.isAssistedInjectionType;
import static dagger.internal.codegen.binding.MapKeys.getMapKeys;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.isPrimitive;
import static dagger.internal.codegen.xprocessing.XTypes.isTypeVariable;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.FormatMethod;
import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.base.FrameworkTypes;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.binding.InjectionAnnotations;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.model.Scope;
import dagger.internal.codegen.xprocessing.XElements;
import dagger.internal.codegen.xprocessing.XTypeNames;
import dagger.internal.codegen.xprocessing.XTypes;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Qualifier;

/** A validator for elements that represent binding declarations. */
public abstract class BindingElementValidator<E extends XElement> {
  private static final ImmutableSet<XClassName> MULTIBINDING_ANNOTATIONS =
      ImmutableSet.of(XTypeNames.INTO_SET, XTypeNames.ELEMENTS_INTO_SET, XTypeNames.INTO_MAP);

  private final AllowsMultibindings allowsMultibindings;
  private final AllowsScoping allowsScoping;
  private final Map<E, ValidationReport> cache = new HashMap<>();
  private final InjectionAnnotations injectionAnnotations;

  /** Creates a validator object. */
  // TODO(bcorso): Consider reworking BindingElementValidator and all subclasses to use composition
  // rather than inheritance. The web of inheritance makes it difficult to track what implementation
  // of a method is actually being used.
  protected BindingElementValidator(
      AllowsMultibindings allowsMultibindings,
      AllowsScoping allowsScoping,
      InjectionAnnotations injectionAnnotations) {
    this.allowsMultibindings = allowsMultibindings;
    this.allowsScoping = allowsScoping;
    this.injectionAnnotations = injectionAnnotations;
  }

  /** Returns a {@link ValidationReport} for {@code element}. */
  public final ValidationReport validate(E element) {
    return reentrantComputeIfAbsent(cache, element, this::validateUncached);
  }

  private ValidationReport validateUncached(E element) {
    return elementValidator(element).validate();
  }

  /**
   * Returns an error message of the form "&lt;{@link #bindingElements()}&gt; <i>rule</i>", where
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

  /** The verb describing the {@link ElementValidator#bindingElementType()} in error messages. */
  // TODO(ronshapiro,dpb): improve the name of this method and it's documentation.
  protected abstract String bindingElementTypeVerb();

  /** The error message when a binding element has a bad type. */
  protected String badTypeMessage() {
    return bindingElements(
        "must %s a primitive, an array, a type variable, or a declared type",
        bindingElementTypeVerb());
  }

  /**
   * The error message when a the type for a binding element with {@link
   * dagger.multibindings.ElementsIntoSet @ElementsIntoSet} or {@code SET_VALUES} is a not set type.
   */
  protected String elementsIntoSetNotASetMessage() {
    return bindingElements(
        "annotated with @ElementsIntoSet must %s a Set", bindingElementTypeVerb());
  }

  /**
   * The error message when a the type for a binding element with {@link
   * dagger.multibindings.ElementsIntoSet @ElementsIntoSet} or {@code SET_VALUES} is a raw set.
   */
  protected String elementsIntoSetRawSetMessage() {
    return bindingElements(
        "annotated with @ElementsIntoSet cannot %s a raw Set", bindingElementTypeVerb());
  }

  /*** Returns an {@link ElementValidator} for validating the given {@code element}. */
  protected abstract ElementValidator elementValidator(E element);

  /** Validator for a single binding element. */
  protected abstract class ElementValidator {
    private final E element;
    protected final ValidationReport.Builder report;
    private final ImmutableSet<XAnnotation> qualifiers;

    protected ElementValidator(E element) {
      this.element = element;
      this.report = ValidationReport.about(element);
      qualifiers = injectionAnnotations.getQualifiers(element);
    }

    /** Checks the element for validity. */
    private ValidationReport validate() {
      checkType();
      checkQualifiers();
      checkMapKeys();
      checkMultibindingAnnotations();
      checkScopes();
      checkAdditionalProperties();
      return report.build();
    }

    /** Check any additional properties of the element. Does nothing by default. */
    protected void checkAdditionalProperties() {}

    /**
     * The type declared by this binding element. This may differ from a binding's {@link
     * Key#type()}, for example in multibindings. An {@link Optional#empty()} return value indicates
     * that the contributed type is ambiguous or missing, i.e. a {@code @BindsInstance} method with
     * zero or many parameters.
     */
    // TODO(dpb): should this be an ImmutableList<XType>, with this class checking the size?
    protected abstract Optional<XType> bindingElementType();

    /**
     * Adds an error if the {@link #bindingElementType() binding element type} is not appropriate.
     *
     * <p>Adds an error if the type is not a primitive, array, declared type, or type variable.
     *
     * <p>If the binding is not a multibinding contribution, adds an error if the type is a
     * framework type.
     *
     * <p>If the element has {@link dagger.multibindings.ElementsIntoSet @ElementsIntoSet} or {@code
     * SET_VALUES}, adds an error if the type is not a {@code Set<T>} for some {@code T}
     */
    protected void checkType() {
      switch (ContributionType.fromBindingElement(element)) {
        case UNIQUE:
          // Basic checks on the types
          bindingElementType().ifPresent(this::checkKeyType);

          // Validate that a unique binding is not attempting to bind a framework type. This
          // validation is only appropriate for unique bindings because multibindings may collect
          // framework types.  E.g. Set<Provider<Foo>> is perfectly reasonable.
          checkFrameworkType();

          // Validate that a unique binding is not attempting to bind an unqualified assisted type.
          // This validation is only appropriate for unique bindings because multibindings may
          // collect assisted types.
          checkAssistedType();

          // Check for any specifically disallowed types
          bindingElementType().ifPresent(this::checkDisallowedType);
          break;

        case SET:
          bindingElementType().ifPresent(this::checkSetValueFrameworkType);
          break;

        case MAP:
          bindingElementType().ifPresent(this::checkMapValueFrameworkType);
          break;

        case SET_VALUES:
          checkSetValuesType();
          break;
      }
    }

    /**
     * Adds an error if {@code keyType} is not a primitive, declared type, array, or type variable.
     */
    protected void checkKeyType(XType keyType) {
      if (isVoid(keyType)) {
        report.addError(bindingElements("must %s a value (not void)", bindingElementTypeVerb()));
      } else if (!(isPrimitive(keyType)
          || isDeclared(keyType)
          || isArray(keyType)
          || isTypeVariable(keyType))) {
        report.addError(badTypeMessage());
      }
    }

    /** Adds errors for unqualified assisted types. */
    private void checkAssistedType() {
      if (qualifiers.isEmpty()
          && bindingElementType().isPresent()
          && isDeclared(bindingElementType().get())) {
        XTypeElement keyElement = bindingElementType().get().getTypeElement();
        if (isAssistedInjectionType(keyElement)) {
          report.addError(
              "Dagger does not support providing @AssistedInject types without a qualifier.",
              keyElement);
        }
        if (isAssistedFactoryType(keyElement)) {
          report.addError("Dagger does not support providing @AssistedFactory types.", keyElement);
        }
      }
    }

    /**
     * Adds an error if the type for an element with {@link
     * dagger.multibindings.ElementsIntoSet @ElementsIntoSet} or {@code SET_VALUES} is not a a
     * {@code Set<T>} for a reasonable {@code T}.
     */
    // TODO(gak): should we allow "covariant return" for set values?
    protected void checkSetValuesType() {
      bindingElementType().ifPresent(this::checkSetValuesType);
    }

    /** Adds an error if {@code type} is not a {@code Set<T>} for a reasonable {@code T}. */
    protected final void checkSetValuesType(XType type) {
      if (!SetType.isSet(type)) {
        report.addError(elementsIntoSetNotASetMessage());
      } else {
        SetType setType = SetType.from(type);
        if (setType.isRawType()) {
          report.addError(elementsIntoSetRawSetMessage());
        } else {
          checkSetValueFrameworkType(setType.elementType());
        }
      }
    }

    /**
     * Adds an error if the element has more than one {@linkplain Qualifier qualifier} annotation.
     */
    private void checkQualifiers() {
      if (qualifiers.size() > 1) {
        for (XAnnotation qualifier : qualifiers) {
          report.addError(
              bindingElements("may not use more than one @Qualifier"),
              element,
              qualifier);
        }
      }
    }

    /**
     * Adds an error if an {@link dagger.multibindings.IntoMap @IntoMap} element doesn't have
     * exactly one {@link dagger.MapKey @MapKey} annotation, or if an element that is {@link
     * dagger.multibindings.IntoMap @IntoMap} has any.
     */
    private void checkMapKeys() {
      if (!allowsMultibindings.allowsMultibindings()) {
        return;
      }
      ImmutableSet<XAnnotation> mapKeys = getMapKeys(element);
      if (ContributionType.fromBindingElement(element).equals(ContributionType.MAP)) {
        switch (mapKeys.size()) {
          case 0:
            report.addError(bindingElements("of type map must declare a map key"));
            break;
          case 1:
            break;
          default:
            report.addError(bindingElements("may not have more than one map key"));
            break;
        }
      } else if (!mapKeys.isEmpty()) {
        report.addError(bindingElements("of non map type cannot declare a map key"));
      }
    }

    /**
     * Adds errors if:
     *
     * <ul>
     *   <li>the element doesn't allow {@linkplain MultibindingAnnotations multibinding annotations}
     *       and has any
     *   <li>the element does allow them but has more than one
     *   <li>the element has a multibinding annotation and its {@link dagger.Provides} or {@link
     *       dagger.producers.Produces} annotation has a {@code type} parameter.
     * </ul>
     */
    private void checkMultibindingAnnotations() {
      ImmutableSet<XAnnotation> multibindingAnnotations =
          XElements.getAllAnnotations(element, MULTIBINDING_ANNOTATIONS);

      switch (allowsMultibindings) {
        case NO_MULTIBINDINGS:
          for (XAnnotation annotation : multibindingAnnotations) {
            report.addError(
                bindingElements("cannot have multibinding annotations"),
                element,
                annotation);
          }
          break;

        case ALLOWS_MULTIBINDINGS:
          if (multibindingAnnotations.size() > 1) {
            for (XAnnotation annotation : multibindingAnnotations) {
              report.addError(
                  bindingElements("cannot have more than one multibinding annotation"),
                  element,
                  annotation);
            }
          }
          break;
      }
    }

    /**
     * Adds an error if the element has a scope but doesn't allow scoping, or if it has more than
     * one {@linkplain Scope scope} annotation.
     */
    private void checkScopes() {
      ImmutableSet<Scope> scopes = injectionAnnotations.getScopes(element);
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
        report.addError(error, element, scope.scopeAnnotation().xprocessing());
      }
    }

    /**
     * Adds an error if the {@link #bindingElementType() type} is a {@linkplain FrameworkTypes
     * framework type}.
     */
    private void checkFrameworkType() {
      if (bindingElementType().filter(FrameworkTypes::isFrameworkType).isPresent()) {
        report.addError(bindingElements("must not %s framework types: %s",
            bindingElementTypeVerb(), XTypes.toStableString(bindingElementType().get())));
      }
    }

    private void checkSetValueFrameworkType(XType bindingType) {
      checkKeyType(bindingType);
      if (FrameworkTypes.isSetValueFrameworkType(bindingType)) {
        report.addError(bindingElements(
            "with @IntoSet/@ElementsIntoSet must not %s framework types: %s",
            bindingElementTypeVerb(), XTypes.toStableString(bindingType)));
      }
      checkDisallowedType(bindingType);
    }

    private void checkMapValueFrameworkType(XType bindingType) {
      checkKeyType(bindingType);
      if (FrameworkTypes.isMapValueFrameworkType(bindingType)) {
        report.addError(
            bindingElements("with @IntoMap must not %s framework types: %s",
                bindingElementTypeVerb(), XTypes.toStableString(bindingType)));
      }
      checkDisallowedType(bindingType);
    }

    private void checkDisallowedType(XType bindingType) {
      // TODO(erichang): Consider if we want to go inside complex types to ban
      // dagger.internal.Provider as well? E.g. List<dagger.internal.Provider<Foo>>
      if (FrameworkTypes.isDisallowedType(bindingType)) {
        report.addError(bindingElements("must not %s disallowed types: %s",
            bindingElementTypeVerb(), XTypes.toStableString(bindingElementType().get())));
      }
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
