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

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Preconditions.checkArgument;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.anonymousClassBuilder;
import static dagger.internal.codegen.ContributionBinding.FactoryCreationStrategy.SINGLETON_INSTANCE;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/**
 * A binding expression that wraps the dependency expressions in a private, no-arg method.
 *
 * <p>Dependents of this binding expression will just called the no-arg method.
 */
final class PrivateMethodBindingExpression extends BindingExpression {
  private final ClassName componentName;
  private final GeneratedComponentModel generatedComponentModel;
  private final BindingExpression delegate;
  private final Map<DependencyRequest.Kind, String> methodNames =
      new EnumMap<>(DependencyRequest.Kind.class);
  private final ContributionBinding binding;
  private final CompilerOptions compilerOptions;
  private final DaggerTypes types;
  private final Elements elements;

  PrivateMethodBindingExpression(
      ResolvedBindings resolvedBindings,
      ClassName componentName,
      GeneratedComponentModel generatedComponentModel,
      BindingExpression delegate,
      CompilerOptions compilerOptions,
      DaggerTypes types,
      Elements elements) {
    super(resolvedBindings);
    this.componentName = componentName;
    this.generatedComponentModel = generatedComponentModel;
    this.delegate = delegate;
    binding = resolvedBindings.contributionBinding();
    this.compilerOptions = compilerOptions;
    this.types = types;
    this.elements = elements;
  }

  @Override
  Expression getComponentMethodExpression(DependencyRequest request, ClassName requestingClass) {
    checkArgument(request.bindingKey().equals(resolvedBindings().bindingKey()));
    if (ignorePrivateMethodStrategy(request.kind())) {
      return delegate.getDependencyExpression(request.kind(), requestingClass);
    }

    return findComponentMethod(request.kind())
            .map(method -> method.dependencyRequest().get().equals(request))
            .orElse(false)
        ? Expression.create(returnType(request.kind()), methodBody(request.kind()))
        : getDependencyExpression(request.kind(), requestingClass);
  }

  @Override
  Expression getDependencyExpression(
      DependencyRequest.Kind requestKind, ClassName requestingClass) {
    if (ignorePrivateMethodStrategy(requestKind) || isNullaryProvisionMethod(requestKind)) {
      return delegate.getDependencyExpression(requestKind, requestingClass);
    }

    if (!methodNames.containsKey(requestKind)) {
      Optional<ComponentMethodDescriptor> componentMethod = findComponentMethod(requestKind);
      String name =
          componentMethod.isPresent()
              ? componentMethod.get().methodElement().getSimpleName().toString()
              : generatedComponentModel.getUniqueMethodName(methodName(requestKind));
      methodNames.put(requestKind, name);
      if (!componentMethod.isPresent()) {
        createMethod(name, requestKind);
      }
    }

    CodeBlock invocation =
        componentName.equals(requestingClass)
            ? CodeBlock.of("$N()", methodNames.get(requestKind))
            : CodeBlock.of("$T.this.$N()", componentName, methodNames.get(requestKind));
    return Expression.create(returnType(requestKind), invocation);
  }

  private boolean ignorePrivateMethodStrategy(DependencyRequest.Kind requestKind) {
    switch (requestKind) {
      case INSTANCE:
      case FUTURE:
        return false;
      case PROVIDER:
      case LAZY:
      case PROVIDER_OF_LAZY:
        return !compilerOptions.experimentalAndroidMode()
            || binding.factoryCreationStrategy().equals(SINGLETON_INSTANCE);
      default:
        return !compilerOptions.experimentalAndroidMode();
    }
  }

  private boolean isNullaryProvisionMethod(DependencyRequest.Kind requestKind) {
    return (requestKind.equals(DependencyRequest.Kind.INSTANCE)
            || requestKind.equals(DependencyRequest.Kind.FUTURE))
        && binding.dependencies().isEmpty()
        && !findComponentMethod(requestKind).isPresent();
  }

  /** Returns the first component method associated with this request kind, if one exists. */
  private Optional<ComponentMethodDescriptor> findComponentMethod(
      DependencyRequest.Kind requestKind) {
    // There could be multiple component methods with the same request key and kind.
    // We arbitrarily choose the first one, and designate it to contain the implementation code.
    return resolvedBindings()
        .owningComponent()
        .componentMethods()
        .stream()
        .filter(method -> componentMethodMatchesRequestBindingKeyAndKind(method, requestKind))
        .findFirst();
  }

  /** Returns true if the component method matches the dependency request binding key and kind. */
  private boolean componentMethodMatchesRequestBindingKeyAndKind(
      ComponentMethodDescriptor componentMethod, DependencyRequest.Kind requestKind) {
    return componentMethod
        .dependencyRequest()
        .filter(request -> request.bindingKey().equals(resolvedBindings().bindingKey()))
        .filter(request -> request.kind().equals(requestKind))
        .isPresent();
  }

  /** Creates the no-arg method used for dependency expressions. */
  private void createMethod(String name, DependencyRequest.Kind requestKind) {
    // TODO(user): Consider when we can make this method static.
    // TODO(user): Fix the order that these generated methods are written to the component.
    generatedComponentModel.addMethod(
        methodBuilder(name)
            .addModifiers(PRIVATE)
            .returns(TypeName.get(returnType(requestKind)))
            .addStatement("return $L", methodBody(requestKind))
            .build());
  }

  /** Returns the return type for the dependency request. */
  private TypeMirror returnType(DependencyRequest.Kind requestKind) {
    if (requestKind.equals(DependencyRequest.Kind.INSTANCE)
        && binding.contributedPrimitiveType().isPresent()) {
      return binding.contributedPrimitiveType().get();
    }
    return accessibleType(requestKind.type(binding.contributedType(), types));
  }

  /** Returns the method body for the dependency request. */
  private CodeBlock methodBody(DependencyRequest.Kind requestKind) {
    switch (requestKind) {
      case PROVIDER:
        // TODO(user): Cache provider field instead of recreating each time.
        return CodeBlock.of("$L", providerTypeSpec());
      case LAZY:
      case PROVIDER_OF_LAZY:
        // TODO(user): Refactor the delegate BindingExpression to handle these cases?
        // Don't use delegate.getDependencyExpression() because that will inline the provider
        // dependency instead of delegating to the private method. To use the private method,
        // recursively call this.getDependencyExpression().
        return FrameworkType.PROVIDER.to(
            requestKind,
            getDependencyExpression(DependencyRequest.Kind.PROVIDER, componentName).codeBlock());
      case INSTANCE:
      case PRODUCER:
      case FUTURE:
        return delegate.getDependencyExpression(requestKind, componentName).codeBlock();
      default:
        throw new AssertionError("Unhandled DependencyRequest: " + requestKind);
    }
  }

  /** Returns a {@link TypeSpec} for an anonymous provider class. */
  private TypeSpec providerTypeSpec() {
    return anonymousClassBuilder("")
        .addSuperinterface(TypeName.get(returnType(DependencyRequest.Kind.PROVIDER)))
        .addMethod(
            methodBuilder("get")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(TypeName.get(accessibleType(binding.contributedType())))
                .addStatement(
                    "return $L",
                    getDependencyExpression(DependencyRequest.Kind.INSTANCE, componentName)
                        .codeBlock())
                .build())
        .build();
  }

  /** Returns the canonical name for a no-arg dependency expression method. */
  private String methodName(DependencyRequest.Kind dependencyKind) {
    // TODO(user): Use a better name for @MapKey binding instances.
    // TODO(user): Include the binding method as part of the method name.
    if (dependencyKind.equals(DependencyRequest.Kind.INSTANCE)) {
      return "get" + bindingName();
    }
    return "get" + bindingName() + dependencyKindName(dependencyKind);
  }

  /** Returns the canonical name for the {@link Binding}. */
  private String bindingName() {
    return LOWER_CAMEL.to(UPPER_CAMEL, BindingVariableNamer.name(binding));
  }

  /** Returns a canonical name for the {@link DependencyRequest.Kind}. */
  private static String dependencyKindName(DependencyRequest.Kind kind) {
    return UPPER_UNDERSCORE.to(UPPER_CAMEL, kind.name());
  }

  /** Returns a {@link TypeName} for the binding that is accessible to the component. */
  private TypeMirror accessibleType(TypeMirror typeMirror) {
    if (Accessibility.isTypeAccessibleFrom(typeMirror, componentName.packageName())) {
      return typeMirror;
    } else if (Accessibility.isRawTypeAccessible(typeMirror, componentName.packageName())
        && typeMirror.getKind().equals(TypeKind.DECLARED)) {
      return types.getDeclaredType(MoreTypes.asTypeElement(typeMirror));
    } else {
      return elements.getTypeElement(Object.class.getCanonicalName()).asType();
    }
  }
}
