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

import static com.google.auto.common.MoreElements.asExecutable;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.Proxies.proxyName;
import static dagger.internal.codegen.Proxies.requiresProxyAccess;
import static dagger.internal.codegen.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.TypeNames.rawTypeName;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.common.util.concurrent.Futures;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

/**
 * A request fulfillment implementation that invokes methods or constructors directly to fulfill
 * requests whenever possible. In cases where direct invocation is not possible, this implementation
 * delegates to one that uses a {@link javax.inject.Provider}.
 */
final class SimpleMethodRequestFulfillment extends RequestFulfillment {

  private final ProvisionBinding provisionBinding;
  private final RequestFulfillment providerDelegate;
  private final RequestFulfillmentRegistry registry;

  SimpleMethodRequestFulfillment(
      BindingKey bindingKey,
      ProvisionBinding provisionBinding,
      RequestFulfillment providerDelegate,
      RequestFulfillmentRegistry registry) {
    super(bindingKey);
    checkArgument(
        provisionBinding.implicitDependencies().isEmpty(),
        "framework deps are not currently supported");
    checkArgument(!provisionBinding.scope().isPresent());
    checkArgument(!provisionBinding.requiresModuleInstance());
    checkArgument(provisionBinding.bindingElement().isPresent());
    this.provisionBinding = provisionBinding;
    this.providerDelegate = providerDelegate;
    this.registry = registry;
  }

  @Override
  CodeBlock getSnippetForDependencyRequest(DependencyRequest request, ClassName requestingClass) {
    switch (request.kind()) {
      case INSTANCE:
        return invokeMethodOrProxy(requestingClass);
      case FUTURE:
        return CodeBlock.of(
            "$T.immediateFuture($L)", Futures.class, invokeMethodOrProxy(requestingClass));
      default:
        return providerDelegate.getSnippetForDependencyRequest(request, requestingClass);
    }
  }

  private CodeBlock invokeMethodOrProxy(ClassName requestingClass) {
    ExecutableElement bindingElement = asExecutable(provisionBinding.bindingElement().get());
    return requiresProxyAccess(bindingElement, requestingClass.packageName())
        ? invokeProxyMethod(requestingClass)
        : invokeMethod(requestingClass);
  }

  private CodeBlock invokeMethod(ClassName requestingClass) {
    CodeBlock parametersCodeBlock =
        makeParametersCodeBlock(
            provisionBinding
                .explicitDependencies()
                .stream()
                .map(
                    request -> {
                      CodeBlock snippet = getDependencySnippet(requestingClass, request);
                      TypeMirror requestElementType = request.requestElement().get().asType();
                      return isTypeAccessibleFrom(requestElementType, requestingClass.packageName())
                          ? snippet
                          : CodeBlock.of(
                              "($T) $L", rawTypeName(TypeName.get(requestElementType)), snippet);
                    })
                .collect(toList()));
    // we use the type from the key to ensure we get the right generics
    // TODO(gak): use <>?
    ExecutableElement method = asExecutable(provisionBinding.bindingElement().get());
    switch (method.getKind()) {
      case CONSTRUCTOR:
        return CodeBlock.of("new $T($L)", provisionBinding.key().type(), parametersCodeBlock);
      case METHOD:
        checkState(method.getModifiers().contains(STATIC));
        return CodeBlock.of(
            "$T.$L($L)",
            provisionBinding.bindingTypeElement().get(),
            method.getSimpleName(),
            parametersCodeBlock);
      default:
        throw new IllegalStateException();
    }
  }

  private CodeBlock invokeProxyMethod(ClassName requestingClass) {
    return CodeBlock.of(
        "$T.$L($L)",
        generatedClassNameForBinding(provisionBinding),
        proxyName(asExecutable(provisionBinding.bindingElement().get())),
        provisionBinding
            .explicitDependencies()
            .stream()
            .map(request -> getDependencySnippet(requestingClass, request))
            .collect(collectingAndThen(toList(), CodeBlocks::makeParametersCodeBlock)));
  }

  private CodeBlock getDependencySnippet(ClassName requestingClass, DependencyRequest request) {
    return registry
        .getRequestFulfillment(request.bindingKey())
        .getSnippetForDependencyRequest(request, requestingClass);
  }
}
