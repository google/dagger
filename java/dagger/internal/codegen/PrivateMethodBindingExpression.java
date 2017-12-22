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
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.anonymousClassBuilder;
import static dagger.internal.codegen.ContributionBinding.FactoryCreationStrategy.SINGLETON_INSTANCE;
import static dagger.internal.codegen.GeneratedComponentModel.FieldSpecKind.PRIVATE_METHOD_SCOPED_FIELD;
import static dagger.internal.codegen.GeneratedComponentModel.MethodSpecKind.PRIVATE_METHOD;
import static dagger.internal.codegen.RequestKinds.requestType;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.VOLATILE;

import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.MemoizedSentinel;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.model.RequestKind;
import dagger.model.Scope;
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
  private final GeneratedComponentModel generatedComponentModel;
  private final ComponentBindingExpressions componentBindingExpressions;
  private final BindingExpression delegate;
  // TODO(user): No need for a map. Each PMBE instance only handles 1 request kind now.
  private final Map<RequestKind, String> methodNames = new EnumMap<>(RequestKind.class);
  private final Map<RequestKind, String> fieldNames = new EnumMap<>(RequestKind.class);
  private final ContributionBinding binding;
  private final CompilerOptions compilerOptions;
  private final ReferenceReleasingManagerFields referenceReleasingManagerFields;
  private final DaggerTypes types;
  private final Elements elements;

  PrivateMethodBindingExpression(
      GeneratedComponentModel generatedComponentModel,
      ComponentBindingExpressions componentBindingExpressions,
      BindingExpression delegate,
      ReferenceReleasingManagerFields referenceReleasingManagerFields,
      CompilerOptions compilerOptions,
      DaggerTypes types,
      Elements elements) {
    super(delegate.resolvedBindings(), delegate.requestKind());
    this.generatedComponentModel = generatedComponentModel;
    this.componentBindingExpressions = componentBindingExpressions;
    this.delegate = delegate;
    this.binding = resolvedBindings().contributionBinding();
    this.referenceReleasingManagerFields = referenceReleasingManagerFields;
    this.compilerOptions = compilerOptions;
    this.types = types;
    this.elements = elements;
  }

  @Override
  protected CodeBlock doGetComponentMethodImplementation(
      ComponentMethodDescriptor componentMethod, ClassName requestingClass) {
    if (!canInlineScope() && ignorePrivateMethodStrategy()) {
      return delegate.getComponentMethodImplementation(componentMethod, requestingClass);
    }

    return findComponentMethod().map(method -> method.equals(componentMethod)).orElse(false)
        ? methodBody()
        : super.doGetComponentMethodImplementation(componentMethod, requestingClass);
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    if (!canInlineScope() && (ignorePrivateMethodStrategy() || isNullaryProvisionMethod())) {
      return delegate.getDependencyExpression(requestingClass);
    }

    if (!methodNames.containsKey(requestKind())) {
      Optional<ComponentMethodDescriptor> componentMethod = findComponentMethod();
      String name =
          componentMethod.isPresent()
              ? componentMethod.get().methodElement().getSimpleName().toString()
              : generatedComponentModel.getUniqueMethodName(methodName());
      methodNames.put(requestKind(), name);
      if (!componentMethod.isPresent()) {
        createMethod(name);
      }
    }

    CodeBlock invocation =
        componentName().equals(requestingClass)
            ? CodeBlock.of("$N()", methodNames.get(requestKind()))
            : CodeBlock.of("$T.this.$N()", componentName(), methodNames.get(requestKind()));
    return Expression.create(returnType(), invocation);
  }

  private ClassName componentName() {
    return generatedComponentModel.name();
  }

  // TODO(user): Invert this method to return true if we are using the private method strategy.
  private boolean ignorePrivateMethodStrategy() {
    // TODO(user): move experimental android logic out of this class
    switch (requestKind()) {
      case INSTANCE:
      case FUTURE:
        return false;
      case PROVIDER:
      case LAZY:
      case PROVIDER_OF_LAZY:
        return !compilerOptions.experimentalAndroidMode()
            || (binding.scope().isPresent() && !canInlineScope())
            || binding.factoryCreationStrategy().equals(SINGLETON_INSTANCE);
      default:
        return !compilerOptions.experimentalAndroidMode();
    }
  }

  private boolean isNullaryProvisionMethod() {
    return (requestKind().equals(RequestKind.INSTANCE) || requestKind().equals(RequestKind.FUTURE))
        && binding.dependencies().isEmpty()
        && !findComponentMethod().isPresent();
  }

  private boolean canInlineScope() {
    // TODO(user): Enable for releasable references
    return compilerOptions.experimentalAndroidMode()
        && binding.scope().isPresent()
        && !referenceReleasingManagerFields.requiresReleasableReferences(binding.scope().get());
  }

  /** Returns the first component method associated with this request kind, if one exists. */
  private Optional<ComponentMethodDescriptor> findComponentMethod() {
    // There could be multiple component methods with the same request key and kind.
    // We arbitrarily choose the first one, and designate it to contain the implementation code.
    return resolvedBindings()
        .owningComponent()
        .componentMethods()
        .stream()
        .filter(method -> componentMethodMatchesRequestBindingKeyAndKind(method))
        .findFirst();
  }

  /** Returns true if the component method matches the dependency request binding key and kind. */
  private boolean componentMethodMatchesRequestBindingKeyAndKind(
      ComponentMethodDescriptor componentMethod) {
    return componentMethod
        .dependencyRequest()
        .filter(request -> request.key().equals(key()))
        .filter(request -> request.kind().equals(requestKind()))
        .isPresent();
  }

  /** Creates the no-arg method used for dependency expressions. */
  private void createMethod(String name) {
    // TODO(user): Consider when we can make this method static.
    // TODO(user): Fix the order that these generated methods are written to the component.
    generatedComponentModel.addMethod(
        PRIVATE_METHOD,
        methodBuilder(name)
            .addModifiers(PRIVATE)
            .returns(TypeName.get(returnType()))
            .addCode(methodBody())
            .build());
  }

  /** Returns the return type for the dependency request. */
  private TypeMirror returnType() {
    if (requestKind().equals(RequestKind.INSTANCE)
        && binding.contributedPrimitiveType().isPresent()) {
      return binding.contributedPrimitiveType().get();
    }
    return accessibleType(requestType(requestKind(), binding.contributedType(), types));
  }

  /** Returns the method body for the dependency request. */
  private CodeBlock methodBody() {
    switch (requestKind()) {
      case PROVIDER:
        // TODO(user): Cache provider field instead of recreating each time.
        return CodeBlock.of("return $L;", providerTypeSpec());
      case INSTANCE:
        if (canInlineScope()) {
          Scope scope = resolvedBindings().scope().get();
          return scope.isReusable() ? singleCheck() : doubleCheck();
        }
        // fall through
      default:
        return CodeBlock.of(
            "return $L;", delegate.getDependencyExpression(componentName()).codeBlock());
    }
  }

  private CodeBlock singleCheck() {
    String fieldName = getMemoizedFieldName();
    return CodeBlock.builder()
        .beginControlFlow("if ($N instanceof $T)", fieldName, MemoizedSentinel.class)
        .addStatement(
            "$N = $L", fieldName, delegate.getDependencyExpression(componentName()).codeBlock())
        .endControlFlow()
        .addStatement("return ($T) $N", returnType(), fieldName)
        .build();
  }

  private CodeBlock doubleCheck() {
    String fieldName = getMemoizedFieldName();
    // add "this." if the fieldName clashes with the local variable name.
    fieldName = fieldName.contentEquals("local") ? "this." + fieldName : fieldName;
    return CodeBlock.builder()
        .addStatement("$T local = $L", TypeName.OBJECT, fieldName)
        .beginControlFlow("if (local instanceof $T)", MemoizedSentinel.class)
        .beginControlFlow("synchronized (local)")
        // TODO(user): benchmark to see if this is really faster than instanceof check?
        .beginControlFlow("if (local == $L)", fieldName)
        .addStatement(
            "$L = $L", fieldName, delegate.getDependencyExpression(componentName()).codeBlock())
        .endControlFlow()
        .addStatement("local = $L", fieldName)
        .endControlFlow()
        .endControlFlow()
        .addStatement("return ($T) local", returnType())
        .build();
  }

  private String getMemoizedFieldName() {
    if (!fieldNames.containsKey(requestKind())) {
      String name = generatedComponentModel.getUniqueFieldName(BindingVariableNamer.name(binding));
      generatedComponentModel.addField(
          PRIVATE_METHOD_SCOPED_FIELD,
          FieldSpec.builder(TypeName.OBJECT, name, PRIVATE, VOLATILE)
              .initializer("new $T()", MemoizedSentinel.class)
              .build());
      fieldNames.put(requestKind(), name);
    }
    return fieldNames.get(requestKind());
  }

  /** Returns a {@link TypeSpec} for an anonymous provider class. */
  private TypeSpec providerTypeSpec() {
    // TODO(user): For scoped bindings that have already been created, use InstanceFactory?
    return anonymousClassBuilder("")
        .addSuperinterface(TypeName.get(returnType()))
        .addMethod(
            methodBuilder("get")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(TypeName.get(accessibleType(binding.contributedType())))
                .addStatement(
                    "return $L",
                    componentBindingExpressions
                        .getDependencyExpression(key(), RequestKind.INSTANCE, componentName())
                        .codeBlock())
                .build())
        .build();
  }

  /** Returns the canonical name for a no-arg dependency expression method. */
  private String methodName() {
    // TODO(user): Use a better name for @MapKey binding instances.
    // TODO(user): Include the binding method as part of the method name.
    if (requestKind().equals(RequestKind.INSTANCE)) {
      return "get" + bindingName();
    }
    return "get" + bindingName() + dependencyKindName(requestKind());
  }

  /** Returns the canonical name for the {@link Binding}. */
  private String bindingName() {
    return LOWER_CAMEL.to(UPPER_CAMEL, BindingVariableNamer.name(binding));
  }

  /** Returns a canonical name for the {@link RequestKind}. */
  private static String dependencyKindName(RequestKind kind) {
    return UPPER_UNDERSCORE.to(UPPER_CAMEL, kind.name());
  }

  /** Returns a {@link TypeName} for the binding that is accessible to the component. */
  private TypeMirror accessibleType(TypeMirror typeMirror) {
    if (Accessibility.isTypeAccessibleFrom(typeMirror, componentName().packageName())) {
      return typeMirror;
    } else if (Accessibility.isRawTypeAccessible(typeMirror, componentName().packageName())
        && typeMirror.getKind().equals(TypeKind.DECLARED)) {
      return types.getDeclaredType(MoreTypes.asTypeElement(typeMirror));
    } else {
      return elements.getTypeElement(Object.class.getCanonicalName()).asType();
    }
  }
}
