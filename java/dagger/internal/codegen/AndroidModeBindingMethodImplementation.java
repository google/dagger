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
import static dagger.internal.codegen.CodeBlocks.anonymousProvider;
import static dagger.internal.codegen.ContributionBinding.FactoryCreationStrategy.SINGLETON_INSTANCE;
import static dagger.internal.codegen.DelegateBindingExpression.isBindsScopeStrongerThanDependencyScope;
import static dagger.internal.codegen.GeneratedComponentModel.FieldSpecKind.PRIVATE_METHOD_SCOPED_FIELD;
import static dagger.model.BindingKind.DELEGATE;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.VOLATILE;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.MemoizedSentinel;
import dagger.model.RequestKind;
import dagger.model.Scope;
import javax.lang.model.util.Elements;

/**
 * Defines a method body and return type for a given {@link BindingExpression} in Android mode,
 * which optionally inlines provider and locking optimizations.
 */
final class AndroidModeBindingMethodImplementation extends BindingMethodImplementation {
  private final BindingGraph graph;
  private final GeneratedComponentModel generatedComponentModel;
  private final ComponentBindingExpressions componentBindingExpressions;
  private final BindingExpression bindingExpression;
  private final ContributionBinding binding;
  private final ReferenceReleasingManagerFields referenceReleasingManagerFields;
  private final ClassName componentName;
  private final Supplier<String> fieldName = Suppliers.memoize(this::createField);

  AndroidModeBindingMethodImplementation(
      BindingExpression bindingExpression,
      DaggerTypes types,
      Elements elements,
      BindingGraph graph,
      GeneratedComponentModel generatedComponentModel,
      ComponentBindingExpressions componentBindingExpressions,
      ReferenceReleasingManagerFields referenceReleasingManagerFields) {
    super(bindingExpression, generatedComponentModel.name(), types, elements);
    this.graph = graph;
    this.generatedComponentModel = generatedComponentModel;
    this.componentName = generatedComponentModel.name();
    this.componentBindingExpressions = checkNotNull(componentBindingExpressions);
    this.bindingExpression = bindingExpression;
    this.binding = bindingExpression.resolvedBindings().contributionBinding();
    this.referenceReleasingManagerFields = checkNotNull(referenceReleasingManagerFields);
  }

  @Override
  CodeBlock body() {
    // TODO(user): split this class into 1 class for each request?
    switch (requestKind()) {
      case PROVIDER:
        if (shouldInlineProvider()) {
          // TODO(user): Cache provider field instead of recreating each time.
          return CodeBlock.of("return $L;", anonymousProviderClass());
        }
        break;
      case INSTANCE:
        if (binding.scope().isPresent()) {
          Scope scope = binding.scope().get();
          if (shouldInlineScope(scope)) {
            return scope.isReusable() ? singleCheck() : doubleCheck();
          }
        }
        break;
      default:
        break;
    }
    return super.body();
  }

  /**
   * Providers should be inlined if:
   *
   * <ul>
   *   <li>the binding is scoped, or
   *   <li>the binding is not scoped and a singleton factory class does not exist for it. If a
   *       singleton factory class exists for a non-scoped binding, we use that instead since it's
   *       more efficient than potentially classloading another anonymous provider class.
   * </ul>
   */
  private boolean shouldInlineProvider() {
    return binding.scope().isPresent()
        || !binding.factoryCreationStrategy().equals(SINGLETON_INSTANCE);
  }

  private boolean shouldInlineScope(Scope scope) {
    if (referenceReleasingManagerFields.requiresReleasableReferences(scope)) {
      // TODO(user): enable for releasable references.
      return false;
    } else if (binding.kind().equals(DELEGATE)) {
      // Only scope a delegate binding if its scope is stronger than its dependency's scope.
      return isBindsScopeStrongerThanDependencyScope(resolvedBindings(), graph);
    } else {
      return true;
    }
  }

  private CodeBlock singleCheck() {
    return CodeBlock.builder()
        .beginControlFlow("if ($N instanceof $T)", fieldName.get(), MemoizedSentinel.class)
        .addStatement(
            "$N = $L",
            fieldName.get(),
            bindingExpression.getDependencyExpression(componentName).codeBlock())
        .endControlFlow()
        .addStatement("return ($T) $N", returnType(), fieldName.get())
        .build();
  }

  private CodeBlock doubleCheck() {
    String fieldExpression =
        fieldName.get().equals("local") ? "this." + fieldName.get() : fieldName.get();
    return CodeBlock.builder()
        .addStatement("$T local = $L", TypeName.OBJECT, fieldExpression)
        .beginControlFlow("if (local instanceof $T)", MemoizedSentinel.class)
        .beginControlFlow("synchronized (local)")
        // TODO(user): benchmark to see if this is really faster than instanceof check?
        .beginControlFlow("if (local == $L)", fieldExpression)
        .addStatement(
            "$L = $L",
            fieldExpression,
            bindingExpression.getDependencyExpression(componentName).codeBlock())
        .endControlFlow()
        .addStatement("local = $L", fieldExpression)
        .endControlFlow()
        .endControlFlow()
        .addStatement("return ($T) local", returnType())
        .build();
  }

  private String createField() {
    String name = generatedComponentModel.getUniqueFieldName(BindingVariableNamer.name(binding));
    generatedComponentModel.addField(
        PRIVATE_METHOD_SCOPED_FIELD,
        FieldSpec.builder(TypeName.OBJECT, name, PRIVATE, VOLATILE)
            .initializer("new $T()", MemoizedSentinel.class)
            .build());
    return name;
  }

  /** Returns a {@link TypeSpec} for an anonymous provider class. */
  private CodeBlock anonymousProviderClass() {
    // TODO(user): For scoped bindings that have already been created, use InstanceFactory?
    return anonymousProvider(
        TypeName.get(accessibleType(binding.contributedType())),
        CodeBlock.of(
            "return $L;",
            componentBindingExpressions
                .getDependencyExpression(key(), RequestKind.INSTANCE, componentName)
                .codeBlock()));
  }
}
