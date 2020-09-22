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
import static com.google.auto.common.MoreElements.asType;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.auto.common.MoreTypes.asExecutable;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.util.ElementFilter.constructorsIn;

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.BindingKind;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

/** Assisted injection utility methods. */
public final class AssistedInjectionAnnotations {
  /** Returns the factory method for the given factory {@link TypeElement}. */
  public static ExecutableElement assistedFactoryMethod(
      TypeElement factory, DaggerElements elements, DaggerTypes types) {
    return getOnlyElement(assistedFactoryMethods(factory, elements, types));
  }

  /** Returns the list of abstract factory methods for the given factory {@link TypeElement}. */
  public static ImmutableSet<ExecutableElement> assistedFactoryMethods(
      TypeElement factory, DaggerElements elements, DaggerTypes types) {
    return MoreElements.getLocalAndInheritedMethods(factory, types, elements).stream()
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
        asExecutable(types.asMemberOf(asDeclared(binding.key().type()), constructor));
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

    TypeElement factory = asType(binding.bindingElement().get());
    ExecutableElement factoryMethod = assistedFactoryMethod(factory, elements, types);
    ExecutableType factoryMethodType =
        asExecutable(types.asMemberOf(asDeclared(binding.key().type()), factoryMethod));
    TypeMirror returnType = factoryMethodType.getReturnType();
    TypeElement returnElement = asTypeElement(returnType);

    ExecutableElement constructor = getOnlyElement(assistedInjectedConstructors(returnElement));
    return assistedParameterSpecs(
        assistedParameters(constructor), factoryMethodType.getParameterTypes());
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

  private AssistedInjectionAnnotations() {}
}
