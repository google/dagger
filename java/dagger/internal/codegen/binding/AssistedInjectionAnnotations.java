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

import static com.google.auto.common.MoreElements.asExecutable;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.auto.common.MoreTypes.asExecutable;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.base.MoreAnnotationValues.getStringValue;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.langmodel.DaggerElements.getAnnotationMirror;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.util.ElementFilter.constructorsIn;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.spi.model.BindingKind;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

/** Assisted injection utility methods. */
public final class AssistedInjectionAnnotations {
  /** Returns the factory method for the given factory {@link TypeElement}. */
  public static ExecutableElement assistedFactoryMethod(
      TypeElement factory, DaggerElements elements) {
    return getOnlyElement(assistedFactoryMethods(factory, elements));
  }

  /** Returns the list of abstract factory methods for the given factory {@link TypeElement}. */
  public static ImmutableSet<ExecutableElement> assistedFactoryMethods(
      TypeElement factory, DaggerElements elements) {
    return elements.getLocalAndInheritedMethods(factory).stream()
        .filter(method -> method.getModifiers().contains(ABSTRACT))
        .filter(method -> !method.isDefault())
        .collect(toImmutableSet());
  }

  /** Returns {@code true} if the element uses assisted injection. */
  public static boolean isAssistedInjectionType(TypeElement typeElement) {
    ImmutableSet<ExecutableElement> injectConstructors = assistedInjectedConstructors(typeElement);
    return !injectConstructors.isEmpty()
        && isAnnotationPresent(getOnlyElement(injectConstructors), AssistedInject.class);
  }

  /** Returns {@code true} if this binding is an assisted factory. */
  public static boolean isAssistedFactoryType(Element element) {
    return isAnnotationPresent(element, AssistedFactory.class);
  }

  /**
   * Returns the list of assisted parameters as {@link ParameterSpec}s.
   *
   * <p>The type of each parameter will be the resolved type given by the binding key, and the name
   * of each parameter will be the name given in the {@link
   * dagger.assisted.AssistedInject}-annotated constructor.
   */
  public static ImmutableList<ParameterSpec> assistedParameterSpecs(
      Binding binding, DaggerTypes types) {
    checkArgument(binding.kind() == BindingKind.ASSISTED_INJECTION);
    ExecutableElement constructor = asExecutable(binding.bindingElement().get());
    ExecutableType constructorType =
        asExecutable(types.asMemberOf(asDeclared(binding.key().type().java()), constructor));
    return assistedParameterSpecs(constructor.getParameters(), constructorType.getParameterTypes());
  }

  private static ImmutableList<ParameterSpec> assistedParameterSpecs(
      List<? extends VariableElement> paramElements, List<? extends TypeMirror> paramTypes) {
    ImmutableList.Builder<ParameterSpec> assistedParameterSpecs = ImmutableList.builder();
    for (int i = 0; i < paramElements.size(); i++) {
      VariableElement paramElement = paramElements.get(i);
      TypeMirror paramType = paramTypes.get(i);
      if (isAssistedParameter(paramElement)) {
        assistedParameterSpecs.add(
            ParameterSpec.builder(TypeName.get(paramType), paramElement.getSimpleName().toString())
                .build());
      }
    }
    return assistedParameterSpecs.build();
  }

  /**
   * Returns the list of assisted factory parameters as {@link ParameterSpec}s.
   *
   * <p>The type of each parameter will be the resolved type given by the binding key, and the name
   * of each parameter will be the name given in the {@link
   * dagger.assisted.AssistedInject}-annotated constructor.
   */
  public static ImmutableList<ParameterSpec> assistedFactoryParameterSpecs(
      Binding binding, DaggerElements elements, DaggerTypes types) {
    checkArgument(binding.kind() == BindingKind.ASSISTED_FACTORY);

    AssistedFactoryMetadata metadata =
        AssistedFactoryMetadata.create(binding.bindingElement().get().asType(), elements, types);
    ExecutableType factoryMethodType =
        asExecutable(
            types.asMemberOf(asDeclared(binding.key().type().java()), metadata.factoryMethod()));
    return assistedParameterSpecs(
        // Use the order of the parameters from the @AssistedFactory method but use the parameter
        // names of the @AssistedInject constructor.
        metadata.assistedFactoryAssistedParameters().stream()
            .map(metadata.assistedInjectAssistedParametersMap()::get)
            .collect(toImmutableList()),
        factoryMethodType.getParameterTypes());
  }

  /** Returns the constructors in {@code type} that are annotated with {@link AssistedInject}. */
  public static ImmutableSet<ExecutableElement> assistedInjectedConstructors(TypeElement type) {
    return constructorsIn(type.getEnclosedElements()).stream()
        .filter(constructor -> isAnnotationPresent(constructor, AssistedInject.class))
        .collect(toImmutableSet());
  }

  public static ImmutableList<VariableElement> assistedParameters(Binding binding) {
    return binding.kind() == BindingKind.ASSISTED_INJECTION
        ? assistedParameters(asExecutable(binding.bindingElement().get()))
        : ImmutableList.of();
  }

  private static ImmutableList<VariableElement> assistedParameters(ExecutableElement constructor) {
    return constructor.getParameters().stream()
        .filter(AssistedInjectionAnnotations::isAssistedParameter)
        .collect(toImmutableList());
  }

  /** Returns {@code true} if this binding is uses assisted injection. */
  public static boolean isAssistedParameter(VariableElement param) {
    return isAnnotationPresent(MoreElements.asVariable(param), Assisted.class);
  }

  /** Metadata about an {@link dagger.assisted.AssistedFactory} annotated type. */
  @AutoValue
  public abstract static class AssistedFactoryMetadata {
    public static AssistedFactoryMetadata create(
        TypeMirror factory, DaggerElements elements, DaggerTypes types) {
      DeclaredType factoryType = asDeclared(factory);
      TypeElement factoryElement = asTypeElement(factoryType);
      ExecutableElement factoryMethod = assistedFactoryMethod(factoryElement, elements);
      ExecutableType factoryMethodType = asExecutable(types.asMemberOf(factoryType, factoryMethod));
      DeclaredType assistedInjectType = asDeclared(factoryMethodType.getReturnType());
      return new AutoValue_AssistedInjectionAnnotations_AssistedFactoryMetadata(
          factoryElement,
          factoryType,
          factoryMethod,
          factoryMethodType,
          asTypeElement(assistedInjectType),
          assistedInjectType,
          AssistedInjectionAnnotations.assistedInjectAssistedParameters(assistedInjectType, types),
          AssistedInjectionAnnotations.assistedFactoryAssistedParameters(
              factoryMethod, factoryMethodType));
    }

    public abstract TypeElement factory();

    public abstract DeclaredType factoryType();

    public abstract ExecutableElement factoryMethod();

    public abstract ExecutableType factoryMethodType();

    public abstract TypeElement assistedInjectElement();

    public abstract DeclaredType assistedInjectType();

    public abstract ImmutableList<AssistedParameter> assistedInjectAssistedParameters();

    public abstract ImmutableList<AssistedParameter> assistedFactoryAssistedParameters();

    @Memoized
    public ImmutableMap<AssistedParameter, VariableElement> assistedInjectAssistedParametersMap() {
      ImmutableMap.Builder<AssistedParameter, VariableElement> builder = ImmutableMap.builder();
      for (AssistedParameter assistedParameter : assistedInjectAssistedParameters()) {
        builder.put(assistedParameter, assistedParameter.variableElement);
      }
      return builder.build();
    }

    @Memoized
    public ImmutableMap<AssistedParameter, VariableElement> assistedFactoryAssistedParametersMap() {
      ImmutableMap.Builder<AssistedParameter, VariableElement> builder = ImmutableMap.builder();
      for (AssistedParameter assistedParameter : assistedFactoryAssistedParameters()) {
        builder.put(assistedParameter, assistedParameter.variableElement);
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
    public static AssistedParameter create(VariableElement parameter, TypeMirror parameterType) {
      AssistedParameter assistedParameter =
          new AutoValue_AssistedInjectionAnnotations_AssistedParameter(
              getAnnotationMirror(parameter, TypeNames.ASSISTED)
                  .map(assisted -> getStringValue(assisted, "value"))
                  .orElse(""),
              MoreTypes.equivalence().wrap(parameterType));
      assistedParameter.variableElement = parameter;
      return assistedParameter;
    }

    private VariableElement variableElement;

    /** Returns the string qualifier from the {@link Assisted#value()}. */
    public abstract String qualifier();

    /** Returns the wrapper for the type annotated with {@link Assisted}. */
    public abstract Equivalence.Wrapper<TypeMirror> wrappedType();

    /** Returns the type annotated with {@link Assisted}. */
    public final TypeMirror type() {
      return wrappedType().get();
    }

    public final VariableElement variableElement() {
      return variableElement;
    }

    @Override
    public final String toString() {
      return qualifier().isEmpty()
          ? String.format("@Assisted %s", type())
          : String.format("@Assisted(\"%s\") %s", qualifier(), type());
    }
  }

  public static ImmutableList<AssistedParameter> assistedInjectAssistedParameters(
      DeclaredType assistedInjectType, DaggerTypes types) {
    // We keep track of the constructor both as an ExecutableElement to access @Assisted
    // parameters and as an ExecutableType to access the resolved parameter types.
    ExecutableElement assistedInjectConstructor =
        getOnlyElement(assistedInjectedConstructors(asTypeElement(assistedInjectType)));
    ExecutableType assistedInjectConstructorType =
        asExecutable(types.asMemberOf(assistedInjectType, assistedInjectConstructor));

    ImmutableList.Builder<AssistedParameter> builder = ImmutableList.builder();
    for (int i = 0; i < assistedInjectConstructor.getParameters().size(); i++) {
      VariableElement parameter = assistedInjectConstructor.getParameters().get(i);
      TypeMirror parameterType = assistedInjectConstructorType.getParameterTypes().get(i);
      if (isAnnotationPresent(parameter, Assisted.class)) {
        builder.add(AssistedParameter.create(parameter, parameterType));
      }
    }
    return builder.build();
  }

  public static ImmutableList<AssistedParameter> assistedFactoryAssistedParameters(
      ExecutableElement factoryMethod, ExecutableType factoryMethodType) {
    ImmutableList.Builder<AssistedParameter> builder = ImmutableList.builder();
    for (int i = 0; i < factoryMethod.getParameters().size(); i++) {
      VariableElement parameter = factoryMethod.getParameters().get(i);
      TypeMirror parameterType = factoryMethodType.getParameterTypes().get(i);
      builder.add(AssistedParameter.create(parameter, parameterType));
    }
    return builder.build();
  }

  private AssistedInjectionAnnotations() {}
}
