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

package dagger.internal.codegen.binding;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.binding.SourceFiles.simpleVariableName;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XElements.hasAnyAnnotation;
import static dagger.internal.codegen.xprocessing.XTypeElements.isNested;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.xprocessing.XTypeElements;
import java.util.Optional;

/** A type that a component needs an instance of. */
@AutoValue
public abstract class ComponentRequirement {
  /** The kind of the {@link ComponentRequirement}. */
  public enum Kind {
    /** A type listed in the component's {@code dependencies} attribute. */
    DEPENDENCY,

    /** A type listed in the component or subcomponent's {@code modules} attribute. */
    MODULE,

    /**
     * An object that is passed to a builder's {@link dagger.BindsInstance @BindsInstance} method.
     */
    BOUND_INSTANCE,
    ;

    public boolean isBoundInstance() {
      return equals(BOUND_INSTANCE);
    }

    public boolean isModule() {
      return equals(MODULE);
    }
  }

  private XType type;

  /** The kind of requirement. */
  public abstract Kind kind();

  /** Returns true if this is a {@link Kind#BOUND_INSTANCE} requirement. */
  // TODO(ronshapiro): consider removing this and inlining the usages
  final boolean isBoundInstance() {
    return kind().isBoundInstance();
  }

  /** The type of the instance the component must have. */
  abstract TypeName typeName();

  /** The type of the instance the component must have. */
  public XType type() {
    return type;
  }

  /** The element associated with the type of this requirement. */
  public XTypeElement typeElement() {
    return type.getTypeElement();
  }

  /** The action a component builder should take if it {@code null} is passed. */
  public enum NullPolicy {
    /** Make a new instance. */
    NEW,
    /** Throw an exception. */
    THROW,
    /** Allow use of null values. */
    ALLOW,
  }

  /**
   * An override for the requirement's null policy. If set, this is used as the null policy instead
   * of the default behavior in {@link #nullPolicy}.
   *
   * <p>Some implementations' null policy can be determined upon construction (e.g., for binding
   * instances), but others' require Elements which must wait until {@link #nullPolicy} is called.
   */
  abstract Optional<NullPolicy> overrideNullPolicy();

  /** The requirement's null policy. */
  public NullPolicy nullPolicy() {
    if (overrideNullPolicy().isPresent()) {
      return overrideNullPolicy().get();
    }
    switch (kind()) {
      case MODULE:
        return componentCanMakeNewInstances(typeElement())
            ? NullPolicy.NEW
            : requiresAPassedInstance() ? NullPolicy.THROW : NullPolicy.ALLOW;
      case DEPENDENCY:
      case BOUND_INSTANCE:
        return NullPolicy.THROW;
    }
    throw new AssertionError();
  }

  /**
   * Returns true if the passed {@link ComponentRequirement} requires a passed instance in order to
   * be used within a component.
   */
  public boolean requiresAPassedInstance() {
    if (!kind().isModule()) {
      // Bound instances and dependencies always require the user to provide an instance.
      return true;
    }
    return requiresModuleInstance() && !componentCanMakeNewInstances(typeElement());
  }

  /**
   * Returns {@code true} if an instance is needed for this (module) requirement.
   *
   * <p>An instance is only needed if there is a binding method on the module that is neither {@code
   * abstract} nor {@code static}; if all bindings are one of those, then there should be no
   * possible dependency on instance state in the module's bindings.
   *
   * <p>Alternatively, if the module is a Kotlin Object then the binding methods are considered
   * {@code static}, requiring no module instance.
   */
  public boolean requiresModuleInstance() {
    if (typeElement().isKotlinObject() || typeElement().isCompanionObject()) {
      return false;
    }
    return XTypeElements.getAllNonPrivateInstanceMethods(typeElement()).stream()
        .filter(this::isBindingMethod)
        .anyMatch(method -> !method.isAbstract() && !method.isStatic());
  }

  private boolean isBindingMethod(XMethodElement method) {
    // TODO(cgdecker): At the very least, we should have utility methods to consolidate this stuff
    // in one place; listing individual annotations all over the place is brittle.
    return hasAnyAnnotation(
        method,
        TypeNames.PROVIDES,
        TypeNames.PRODUCES,
        // TODO(ronshapiro): it would be cool to have internal meta-annotations that could describe
        // these, like @AbstractBindingMethod
        TypeNames.BINDS,
        TypeNames.MULTIBINDS,
        TypeNames.BINDS_OPTIONAL_OF);
  }

  /** The key for this requirement, if one is available. */
  public abstract Optional<Key> key();

  /** Returns the name for this requirement that could be used as a variable. */
  public abstract String variableName();

  /** Returns a parameter spec for this requirement. */
  public ParameterSpec toParameterSpec() {
    return ParameterSpec.builder(type().getTypeName(), variableName()).build();
  }

  public static ComponentRequirement forDependency(XType type) {
    checkArgument(isDeclared(checkNotNull(type)));
    return create(Kind.DEPENDENCY, type);
  }

  public static ComponentRequirement forModule(XType type) {
    checkArgument(isDeclared(checkNotNull(type)));
    return create(Kind.MODULE, type);
  }

  public static ComponentRequirement forBoundInstance(ContributionBinding binding) {
    checkArgument(binding.kind().equals(BindingKind.BOUND_INSTANCE));
    return forBoundInstance(binding.key(), binding.isNullable(), binding.bindingElement().get());
  }

  static ComponentRequirement forBoundInstance(
      Key key, boolean nullable, XElement elementForVariableName) {
    return create(
        Kind.BOUND_INSTANCE,
        key.type().xprocessing(),
        nullable ? Optional.of(NullPolicy.ALLOW) : Optional.empty(),
        Optional.of(key),
        getSimpleName(elementForVariableName));
  }

  private static ComponentRequirement create(Kind kind, XType type) {
    return create(
        kind,
        type,
        Optional.empty(),
        Optional.empty(),
        simpleVariableName(type.getTypeElement().getClassName()));
  }

  private static ComponentRequirement create(
      Kind kind,
      XType type,
      Optional<NullPolicy> overrideNullPolicy,
      Optional<Key> key,
      String variableName) {
    ComponentRequirement requirement =
        new AutoValue_ComponentRequirement(
            kind, type.getTypeName(), overrideNullPolicy, key, variableName);
    requirement.type = type;
    return requirement;
  }

  /**
   * Returns true if and only if a component can instantiate new instances (typically of a module)
   * rather than requiring that they be passed.
   */
  // TODO(bcorso): Should this method throw if its called knowing that an instance is not needed?
  public static boolean componentCanMakeNewInstances(XTypeElement typeElement) {
    // TODO(gak): still need checks for visibility
    return typeElement.isClass()
        && !typeElement.isAbstract()
        && !requiresEnclosingInstance(typeElement)
        && hasVisibleDefaultConstructor(typeElement);
  }

  private static boolean requiresEnclosingInstance(XTypeElement typeElement) {
    return isNested(typeElement) && !typeElement.isStatic();
  }

  private static boolean hasVisibleDefaultConstructor(XTypeElement typeElement) {
    return typeElement.getConstructors().stream()
        .anyMatch(constructor -> !constructor.isPrivate() && constructor.getParameters().isEmpty());
  }
}
