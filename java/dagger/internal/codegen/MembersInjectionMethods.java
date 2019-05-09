/*
 * Copyright (C) 2017 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.ComponentImplementation.MethodSpecKind.MEMBERS_INJECTION_METHOD;
import static dagger.internal.codegen.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.InjectionMethods.InjectionSiteMethod;
import dagger.internal.codegen.MembersInjectionBinding.InjectionSite;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.Key;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/** Manages the member injection methods for a component. */
final class MembersInjectionMethods {
  private final Map<Key, MethodSpec> membersInjectionMethods = new LinkedHashMap<>();
  private final ComponentImplementation componentImplementation;
  private final ComponentBindingExpressions bindingExpressions;
  private final BindingGraph graph;
  private final DaggerElements elements;
  private final DaggerTypes types;

  MembersInjectionMethods(
      ComponentImplementation componentImplementation,
      ComponentBindingExpressions bindingExpressions,
      BindingGraph graph,
      DaggerElements elements,
      DaggerTypes types) {
    this.componentImplementation = checkNotNull(componentImplementation);
    this.bindingExpressions = checkNotNull(bindingExpressions);
    this.graph = checkNotNull(graph);
    this.elements = checkNotNull(elements);
    this.types = checkNotNull(types);
  }

  /**
   * Returns the members injection {@link MethodSpec} for the given {@link Key}, creating it if
   * necessary.
   */
  MethodSpec getOrCreate(Key key) {
    return reentrantComputeIfAbsent(membersInjectionMethods, key, this::membersInjectionMethod);
  }

  private MethodSpec membersInjectionMethod(Key key) {
    ResolvedBindings resolvedBindings =
        graph.membersInjectionBindings().getOrDefault(key, graph.contributionBindings().get(key));
    Binding binding = resolvedBindings.binding();
    TypeMirror keyType = binding.key().type();
    TypeMirror membersInjectedType =
        isTypeAccessibleFrom(keyType, componentImplementation.name().packageName())
            ? keyType
            : elements.getTypeElement(Object.class).asType();
    TypeName membersInjectedTypeName = TypeName.get(membersInjectedType);
    Name bindingTypeName = binding.bindingTypeElement().get().getSimpleName();
    // TODO(ronshapiro): include type parameters in this name e.g. injectFooOfT, and outer class
    // simple names Foo.Builder -> injectFooBuilder
    String methodName = componentImplementation.getUniqueMethodName("inject" + bindingTypeName);
    ParameterSpec parameter = ParameterSpec.builder(membersInjectedTypeName, "instance").build();
    MethodSpec.Builder methodBuilder =
        methodBuilder(methodName)
            .addModifiers(PRIVATE)
            .returns(membersInjectedTypeName)
            .addParameter(parameter);
    TypeElement canIgnoreReturnValue =
        elements.getTypeElement("com.google.errorprone.annotations.CanIgnoreReturnValue");
    if (canIgnoreReturnValue != null) {
      methodBuilder.addAnnotation(ClassName.get(canIgnoreReturnValue));
    }
    CodeBlock instance = CodeBlock.of("$N", parameter);
    methodBuilder.addCode(
        InjectionSiteMethod.invokeAll(
            injectionSites(binding),
            componentImplementation.name(),
            instance,
            membersInjectedType,
            types,
            request ->
                bindingExpressions
                    .getDependencyArgumentExpression(request, componentImplementation.name())
                    .codeBlock(),
            elements));
    methodBuilder.addStatement("return $L", instance);

    MethodSpec method = methodBuilder.build();
    componentImplementation.addMethod(MEMBERS_INJECTION_METHOD, method);
    return method;
  }

  private static ImmutableSet<InjectionSite> injectionSites(Binding binding) {
    if (binding instanceof ProvisionBinding) {
      return ((ProvisionBinding) binding).injectionSites();
    } else if (binding instanceof MembersInjectionBinding) {
      return ((MembersInjectionBinding) binding).injectionSites();
    }
    throw new IllegalArgumentException(binding.key().toString());
  }
}
