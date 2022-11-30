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

package dagger.internal.codegen.writing;

import static androidx.room.compiler.processing.XElementKt.isConstructor;
import static androidx.room.compiler.processing.XElementKt.isMethod;
import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static dagger.internal.codegen.javapoet.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.javapoet.TypeNames.rawTypeName;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.writing.InjectionMethods.ProvisionMethod.requiresInjectionMethod;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XProcessingEnvs.isPreJava8SourceVersion;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.writing.InjectionMethods.ProvisionMethod;
import dagger.spi.model.DependencyRequest;
import java.util.Optional;

/**
 * A binding expression that invokes methods or constructors directly (without attempting to scope)
 * {@link dagger.spi.model.RequestKind#INSTANCE} requests.
 */
final class SimpleMethodRequestRepresentation extends RequestRepresentation {
  private final CompilerOptions compilerOptions;
  private final XProcessingEnv processingEnv;
  private final ProvisionBinding provisionBinding;
  private final ComponentRequestRepresentations componentRequestRepresentations;
  private final MembersInjectionMethods membersInjectionMethods;
  private final ComponentRequirementExpressions componentRequirementExpressions;
  private final ShardImplementation shardImplementation;
  private final boolean isExperimentalMergedMode;

  @AssistedInject
  SimpleMethodRequestRepresentation(
      @Assisted ProvisionBinding binding,
      MembersInjectionMethods membersInjectionMethods,
      CompilerOptions compilerOptions,
      XProcessingEnv processingEnv,
      ComponentRequestRepresentations componentRequestRepresentations,
      ComponentRequirementExpressions componentRequirementExpressions,
      ComponentImplementation componentImplementation) {
    this.compilerOptions = compilerOptions;
    this.processingEnv = processingEnv;
    this.provisionBinding = binding;
    checkArgument(
        provisionBinding.implicitDependencies().isEmpty(),
        "framework deps are not currently supported");
    checkArgument(provisionBinding.bindingElement().isPresent());
    this.componentRequestRepresentations = componentRequestRepresentations;
    this.membersInjectionMethods = membersInjectionMethods;
    this.componentRequirementExpressions = componentRequirementExpressions;
    this.shardImplementation = componentImplementation.shardImplementation(binding);
    this.isExperimentalMergedMode =
        componentImplementation.compilerMode().isExperimentalMergedMode();
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    return requiresInjectionMethod(provisionBinding, compilerOptions, requestingClass)
        ? invokeInjectionMethod(requestingClass)
        : invokeMethod(requestingClass);
  }

  private Expression invokeMethod(ClassName requestingClass) {
    // TODO(dpb): align this with the contents of InlineMethods.create
    CodeBlock arguments =
        makeParametersCodeBlock(
            ProvisionMethod.invokeArguments(
                provisionBinding,
                request -> dependencyArgument(request, requestingClass).codeBlock(),
                shardImplementation::getUniqueFieldNameForAssistedParam));
    XElement bindingElement = provisionBinding.bindingElement().get();
    XTypeElement bindingTypeElement = provisionBinding.bindingTypeElement().get();
    CodeBlock invocation;
    if (isConstructor(bindingElement)) {
      invocation = CodeBlock.of("new $T($L)", constructorTypeName(requestingClass), arguments);
    } else if (isMethod(bindingElement)) {
      CodeBlock module;
      Optional<CodeBlock> requiredModuleInstance = moduleReference(requestingClass);
      if (requiredModuleInstance.isPresent()) {
        module = requiredModuleInstance.get();
      } else if (bindingTypeElement.isKotlinObject() && !bindingTypeElement.isCompanionObject()) {
        // Call through the singleton instance.
        // See: https://kotlinlang.org/docs/reference/java-to-kotlin-interop.html#static-methods
        module = CodeBlock.of("$T.INSTANCE", bindingTypeElement.getClassName());
      } else {
        module = CodeBlock.of("$T", bindingTypeElement.getClassName());
      }
      invocation =
          CodeBlock.of("$L.$L($L)", module, asMethod(bindingElement).getJvmName(), arguments);
    } else {
      throw new AssertionError("Unexpected binding element: " + bindingElement);
    }

    return Expression.create(simpleMethodReturnType(), invocation);
  }

  private TypeName constructorTypeName(ClassName requestingClass) {
    XType type = provisionBinding.key().type().xprocessing();
    return type.getTypeArguments().stream()
            .allMatch(t -> isTypeAccessibleFrom(t, requestingClass.packageName()))
        ? type.getTypeName()
        : rawTypeName(type.getTypeName());
  }

  private Expression invokeInjectionMethod(ClassName requestingClass) {
    return injectMembers(
        ProvisionMethod.invoke(
            provisionBinding,
            request -> dependencyArgument(request, requestingClass).codeBlock(),
            shardImplementation::getUniqueFieldNameForAssistedParam,
            requestingClass,
            moduleReference(requestingClass),
            compilerOptions),
        requestingClass);
  }

  private Expression dependencyArgument(DependencyRequest dependency, ClassName requestingClass) {
    return isExperimentalMergedMode
        ? componentRequestRepresentations
            .getExperimentalSwitchingProviderDependencyRepresentation(bindingRequest(dependency))
            .getDependencyExpression(dependency.kind(), provisionBinding)
        : componentRequestRepresentations.getDependencyArgumentExpression(
            dependency, requestingClass);
  }

  private Expression injectMembers(CodeBlock instance, ClassName requestingClass) {
    if (provisionBinding.injectionSites().isEmpty()) {
      return Expression.create(simpleMethodReturnType(), instance);
    }
    if (isPreJava8SourceVersion(processingEnv)) {
      // Java 7 type inference can't figure out that instance in
      // injectParameterized(Parameterized_Factory.newParameterized()) is Parameterized<T> and not
      // Parameterized<Object>
      if (!provisionBinding.key().type().xprocessing().getTypeArguments().isEmpty()) {
        TypeName keyType = provisionBinding.key().type().xprocessing().getTypeName();
        instance = CodeBlock.of("($T) ($T) $L", keyType, rawTypeName(keyType), instance);
      }
    }
    return isExperimentalMergedMode
        ? membersInjectionMethods.getInjectExpressionExperimental(
            provisionBinding, instance, requestingClass)
        : membersInjectionMethods.getInjectExpression(
            provisionBinding.key(), instance, requestingClass);
  }

  private Optional<CodeBlock> moduleReference(ClassName requestingClass) {
    return provisionBinding.requiresModuleInstance()
        ? provisionBinding
            .contributingModule()
            .map(XTypeElement::getType)
            .map(ComponentRequirement::forModule)
            .map(
                module ->
                    isExperimentalMergedMode
                        ? CodeBlock.of("(($T) dependencies[0])", module.type().getTypeName())
                        : componentRequirementExpressions.getExpression(module, requestingClass))
        : Optional.empty();
  }

  private XType simpleMethodReturnType() {
    return provisionBinding
        .contributedPrimitiveType()
        .orElse(provisionBinding.key().type().xprocessing());
  }

  @AssistedFactory
  static interface Factory {
    SimpleMethodRequestRepresentation create(ProvisionBinding binding);
  }
}
