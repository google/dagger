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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static javax.lang.model.type.TypeKind.VOID;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;

/** A binding expression for members injection bindings. */
final class MembersInjectionBindingExpression extends BindingExpression {
  private final FrameworkInstanceBindingExpression membersInjectorExpression;
  private final GeneratedComponentModel generatedComponentModel;
  private final MembersInjectionBinding binding;
  private final MembersInjectionMethods membersInjectionMethods;

  MembersInjectionBindingExpression(
      FrameworkInstanceBindingExpression membersInjectorExpression,
      GeneratedComponentModel generatedComponentModel,
      MembersInjectionMethods membersInjectionMethods) {
    super(membersInjectorExpression.resolvedBindings(), membersInjectorExpression.requestKind());
    checkArgument(bindingKey().kind().equals(BindingKey.Kind.MEMBERS_INJECTION));
    checkArgument(requestKind().equals(DependencyRequest.Kind.MEMBERS_INJECTOR));
    this.membersInjectorExpression = membersInjectorExpression;
    this.generatedComponentModel = generatedComponentModel;
    this.binding = resolvedBindings().membersInjectionBinding().get();
    this.membersInjectionMethods = membersInjectionMethods;
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    return membersInjectorExpression.getDependencyExpression(requestingClass);
  }

  @Override
  protected CodeBlock doGetComponentMethodImplementation(
      ComponentMethodDescriptor componentMethod, ClassName requestingClass) {
    ExecutableElement methodElement = componentMethod.methodElement();
    List<? extends VariableElement> parameters = methodElement.getParameters();
    if (parameters.isEmpty() /* i.e. it's a request for a MembersInjector<T> */) {
      return membersInjectorExpression.getComponentMethodImplementation(
          componentMethod, generatedComponentModel.name());
    }

    ParameterSpec parameter = ParameterSpec.get(getOnlyElement(parameters));
    if (binding.injectionSites().isEmpty()) {
      return methodElement.getReturnType().getKind().equals(VOID)
          ? CodeBlock.of("")
          : CodeBlock.of("return $N;", parameter);
    } else {
      return methodElement.getReturnType().getKind().equals(VOID)
          ? CodeBlock.of("$L;", membersInjectionInvocation(parameter))
          : CodeBlock.of("return $L;", membersInjectionInvocation(parameter));
    }
  }

  CodeBlock membersInjectionInvocation(ParameterSpec target) {
    return CodeBlock.of("$N($N)", membersInjectionMethods.getOrCreate(binding.key()), target);
  }
}
