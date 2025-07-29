/*
 * Copyright (C) 2020 The Dagger Authors.
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
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XElements.asConstructor;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;

import androidx.room.compiler.codegen.XParameterSpec;
import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XConstructorType;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XHasModifiers;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.XVariableElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.xprocessing.XParameterSpecs;
import dagger.internal.codegen.xprocessing.XTypeElements;
import dagger.internal.codegen.xprocessing.XTypeNames;
import dagger.internal.codegen.xprocessing.XTypes;
import java.util.List;
import java.util.Optional;

/** Assisted injection utility methods. */
public final class AssistedInjectionAnnotations {
  /** Returns the factory method for the given factory {@link XTypeElement}. */
  public static XMethodElement assistedFactoryMethod(XTypeElement factory) {
    return getOnlyElement(assistedFactoryMethods(factory));
  }

  /** Returns the list of abstract factory methods for the given factory {@link XTypeElement}. */
  public static ImmutableSet<XMethodElement> assistedFactoryMethods(XTypeElement factory) {
    return XTypeElements.getAllNonPrivateInstanceMethods(factory).stream()
        .filter(XHasModifiers::isAbstract)
        .filter(method -> !method.isJavaDefault())
        .collect(toImmutableSet());
  }

  /** Returns {@code true} if the element uses assisted injection. */
  public static boolean isAssistedInjectionType(XTypeElement typeElement) {
    return assistedInjectedConstructors(typeElement).stream()
        .anyMatch(constructor -> constructor.hasAnnotation(XTypeNames.ASSISTED_INJECT));
  }

  /** Returns {@code true} if this binding is an assisted factory. */
  public static boolean isAssistedFactoryType(XElement element) {
    return element.hasAnnotation(XTypeNames.ASSISTED_FACTORY);
  }

  /**
   * Returns the list of assisted parameters as {@link XParameterSpec}s.
   *
   * <p>The type of each parameter will be the resolved type given by the binding key, and the name
   * of each parameter will be the name given in the {@link
   * dagger.assisted.AssistedInject}-annotated constructor.
   */
  public static ImmutableList<XParameterSpec> assistedParameterSpecs(Binding binding) {
    checkArgument(binding.kind() == BindingKind.ASSISTED_INJECTION);
    XConstructorElement constructor = asConstructor(binding.bindingElement().get());
    XConstructorType constructorType = constructor.asMemberOf(binding.key().type().xprocessing());
    return assistedParameterSpecs(constructor.getParameters(), constructorType.getParameterTypes());
  }

  private static ImmutableList<XParameterSpec> assistedParameterSpecs(
      List<? extends XExecutableParameterElement> paramElements, List<XType> paramTypes) {
    ImmutableList.Builder<XParameterSpec> assistedParameterSpecs = ImmutableList.builder();
    for (int i = 0; i < paramElements.size(); i++) {
      XExecutableParameterElement paramElement = paramElements.get(i);
      XType paramType = paramTypes.get(i);
      if (isAssistedParameter(paramElement)) {
        assistedParameterSpecs.add(
            XParameterSpecs.of(paramElement.getJvmName(), paramType.asTypeName()));
      }
    }
    return assistedParameterSpecs.build();
  }

  /**
   * Returns the list of assisted factory parameters as {@link XParameterSpec}s.
   *
   * <p>The type of each parameter will be the resolved type given by the binding key, and the name
   * of each parameter will be the name given in the {@link
   * dagger.assisted.AssistedInject}-annotated constructor.
   */
  public static ImmutableList<XParameterSpec> assistedFactoryParameterSpecs(Binding binding) {
    checkArgument(binding.kind() == BindingKind.ASSISTED_FACTORY);

    XTypeElement factory = asTypeElement(binding.bindingElement().get());
    AssistedFactoryMetadata metadata = AssistedFactoryMetadata.create(factory.getType());
    XMethodType factoryMethodType =
        metadata.factoryMethod().asMemberOf(binding.key().type().xprocessing());
    return assistedParameterSpecs(
        // Use the order of the parameters from the @AssistedFactory method but use the parameter
        // names of the @AssistedInject constructor.
        metadata.assistedFactoryAssistedParameters().stream()
            .map(metadata.assistedInjectAssistedParametersMap()::get)
            .collect(toImmutableList()),
        factoryMethodType.getParameterTypes());
  }

  /** Returns the constructors in {@code type} that are annotated with {@link AssistedInject}. */
  public static ImmutableSet<XConstructorElement> assistedInjectedConstructors(XTypeElement type) {
    return type.getConstructors().stream()
        .filter(constructor -> constructor.hasAnnotation(XTypeNames.ASSISTED_INJECT))
        .collect(toImmutableSet());
  }

  public static ImmutableList<XExecutableParameterElement> assistedParameters(Binding binding) {
    return binding.kind() == BindingKind.ASSISTED_INJECTION
        ? asConstructor(binding.bindingElement().get()).getParameters().stream()
            .filter(AssistedInjectionAnnotations::isAssistedParameter)
            .collect(toImmutableList())
        : ImmutableList.of();
  }

  /** Returns {@code true} if this binding is uses assisted injection. */
  public static boolean isAssistedParameter(XVariableElement param) {
    return param.hasAnnotation(XTypeNames.ASSISTED);
  }

  /** Metadata about an {@link dagger.assisted.AssistedFactory} annotated type. */
  @AutoValue
  public abstract static class AssistedFactoryMetadata {
    public static AssistedFactoryMetadata create(XType factoryType) {
      XTypeElement factoryElement = factoryType.getTypeElement();
      XMethodElement factoryMethod = assistedFactoryMethod(factoryElement);
      XMethodType factoryMethodType = factoryMethod.asMemberOf(factoryType);
      XType assistedInjectType = factoryMethodType.getReturnType();
      XTypeElement assistedInjectElement = assistedInjectType.getTypeElement();
      return new AutoValue_AssistedInjectionAnnotations_AssistedFactoryMetadata(
          factoryElement,
          factoryType,
          factoryMethod,
          factoryMethodType,
          assistedInjectElement,
          assistedInjectType,
          AssistedInjectionAnnotations.assistedInjectAssistedParameters(assistedInjectType),
          AssistedInjectionAnnotations.assistedFactoryAssistedParameters(
              factoryMethod, factoryMethodType));
    }

    public abstract XTypeElement factory();

    public abstract XType factoryType();

    public abstract XMethodElement factoryMethod();

    public abstract XMethodType factoryMethodType();

    public abstract XTypeElement assistedInjectElement();

    public abstract XType assistedInjectType();

    public abstract ImmutableList<AssistedParameter> assistedInjectAssistedParameters();

    public abstract ImmutableList<AssistedParameter> assistedFactoryAssistedParameters();

    @Memoized
    public ImmutableMap<AssistedParameter, XExecutableParameterElement>
        assistedInjectAssistedParametersMap() {
      ImmutableMap.Builder<AssistedParameter, XExecutableParameterElement> builder =
          ImmutableMap.builder();
      for (AssistedParameter assistedParameter : assistedInjectAssistedParameters()) {
        builder.put(assistedParameter, assistedParameter.element());
      }
      return builder.build();
    }

    @Memoized
    public ImmutableMap<AssistedParameter, XExecutableParameterElement>
        assistedFactoryAssistedParametersMap() {
      ImmutableMap.Builder<AssistedParameter, XExecutableParameterElement> builder =
          ImmutableMap.builder();
      for (AssistedParameter assistedParameter : assistedFactoryAssistedParameters()) {
        builder.put(assistedParameter, assistedParameter.element());
      }
      return builder.build();
    }
  }

  /**
   * Metadata about an {@link Assisted} annotated parameter.
   *
   * <p>This parameter can represent an {@link Assisted} annotated parameter from an {@link
   * AssistedInject} constructor or an {@link AssistedFactory} method.
   */
  @AutoValue
  public abstract static class AssistedParameter {
    public static AssistedParameter create(
        XExecutableParameterElement parameter, XType parameterType) {
      AssistedParameter assistedParameter =
          new AutoValue_AssistedInjectionAnnotations_AssistedParameter(
              Optional.ofNullable(parameter.getAnnotation(XTypeNames.ASSISTED))
                  .map(assisted -> assisted.getAsString("value"))
                  .orElse(""),
              XTypes.equivalence().wrap(parameterType));
      assistedParameter.parameterElement = parameter;
      return assistedParameter;
    }

    private XExecutableParameterElement parameterElement;

    /** Returns the string qualifier from the {@link Assisted#value()}. */
    public abstract String qualifier();

    /** Returns the equivalence wrapper for the type annotated with {@link Assisted}. */
    abstract Equivalence.Wrapper<XType> wrappedType();

    /** Returns the type annotated with {@link Assisted}. */
    public final XType type() {
      return wrappedType().get();
    }

    public final XExecutableParameterElement element() {
      return parameterElement;
    }

    @Override
    public final String toString() {
      return qualifier().isEmpty()
          ? String.format("@Assisted %s", XTypes.toStableString(type()))
          : String.format("@Assisted(\"%s\") %s", qualifier(), XTypes.toStableString(type()));
    }
  }

  public static ImmutableList<AssistedParameter> assistedInjectAssistedParameters(
      XType assistedInjectType) {
    // We keep track of the constructor both as an ExecutableElement to access @Assisted
    // parameters and as an ExecutableType to access the resolved parameter types.
    XConstructorElement assistedInjectConstructor =
        getOnlyElement(assistedInjectedConstructors(assistedInjectType.getTypeElement()));
    XConstructorType assistedInjectConstructorType =
        assistedInjectConstructor.asMemberOf(assistedInjectType.makeNonNullable());

    ImmutableList.Builder<AssistedParameter> builder = ImmutableList.builder();
    for (int i = 0; i < assistedInjectConstructor.getParameters().size(); i++) {
      XExecutableParameterElement parameter = assistedInjectConstructor.getParameters().get(i);
      XType parameterType = assistedInjectConstructorType.getParameterTypes().get(i);
      if (parameter.hasAnnotation(XTypeNames.ASSISTED)) {
        builder.add(AssistedParameter.create(parameter, parameterType));
      }
    }
    return builder.build();
  }

  private static ImmutableList<AssistedParameter> assistedFactoryAssistedParameters(
      XMethodElement factoryMethod, XMethodType factoryMethodType) {
    ImmutableList.Builder<AssistedParameter> builder = ImmutableList.builder();
    for (int i = 0; i < factoryMethod.getParameters().size(); i++) {
      XExecutableParameterElement parameter = factoryMethod.getParameters().get(i);
      XType parameterType = factoryMethodType.getParameterTypes().get(i);
      builder.add(AssistedParameter.create(parameter, parameterType));
    }
    return builder.build();
  }

  private AssistedInjectionAnnotations() {}
}
