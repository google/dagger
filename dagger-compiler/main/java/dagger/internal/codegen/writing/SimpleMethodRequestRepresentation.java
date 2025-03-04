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

import static androidx.room.compiler.codegen.compat.XConverters.toJavaPoet;
import static androidx.room.compiler.processing.XElementKt.isConstructor;
import static androidx.room.compiler.processing.XElementKt.isMethod;
import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.javapoet.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.langmodel.Accessibility.isElementAccessibleFrom;
import static dagger.internal.codegen.langmodel.Accessibility.isRawTypeAccessible;
import static dagger.internal.codegen.langmodel.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.xprocessing.XElements.asExecutable;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XProcessingEnvs.isPreJava8SourceVersion;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.AssistedInjectionBinding;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.InjectionBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.writing.InjectionMethods.ProvisionMethod;
import java.util.Optional;

/**
 * A binding expression that invokes methods or constructors directly (without attempting to scope)
 * {@link dagger.internal.codegen.model.RequestKind#INSTANCE} requests.
 */
final class SimpleMethodRequestRepresentation extends RequestRepresentation {
  private static final ImmutableSet<BindingKind> VALID_BINDING_KINDS =
      ImmutableSet.of(BindingKind.INJECTION, BindingKind.ASSISTED_INJECTION, BindingKind.PROVISION);

  private final CompilerOptions compilerOptions;
  private final XProcessingEnv processingEnv;
  private final ContributionBinding binding;
  private final ComponentRequestRepresentations componentRequestRepresentations;
  private final MembersInjectionMethods membersInjectionMethods;
  private final ComponentRequirementExpressions componentRequirementExpressions;
  private final ShardImplementation shardImplementation;

  @AssistedInject
  SimpleMethodRequestRepresentation(
      @Assisted ContributionBinding binding,
      MembersInjectionMethods membersInjectionMethods,
      CompilerOptions compilerOptions,
      XProcessingEnv processingEnv,
      ComponentRequestRepresentations componentRequestRepresentations,
      ComponentRequirementExpressions componentRequirementExpressions,
      ComponentImplementation componentImplementation) {
    this.compilerOptions = compilerOptions;
    this.processingEnv = processingEnv;
    this.binding = binding;
    checkArgument(VALID_BINDING_KINDS.contains(binding.kind()));
    checkArgument(binding.bindingElement().isPresent());
    this.componentRequestRepresentations = componentRequestRepresentations;
    this.membersInjectionMethods = membersInjectionMethods;
    this.componentRequirementExpressions = componentRequirementExpressions;
    this.shardImplementation = componentImplementation.shardImplementation(binding);
  }

  @Override
  Expression getDependencyExpression(XClassName requestingClass) {
    return requiresInjectionMethod(requestingClass)
        ? invokeInjectionMethod(requestingClass)
        : invokeMethod(requestingClass);
  }

  private Expression invokeMethod(XClassName requestingClass) {
    // TODO(dpb): align this with the contents of InlineMethods.create
    CodeBlock arguments =
        makeParametersCodeBlock(
            ProvisionMethod.invokeArguments(
                binding,
                request -> dependencyArgument(request, requestingClass).codeBlock(),
                shardImplementation::getUniqueFieldNameForAssistedParam));
    XElement bindingElement = binding.bindingElement().get();
    XTypeElement bindingTypeElement = binding.bindingTypeElement().get();
    CodeBlock invocation;
    if (isConstructor(bindingElement)) {
      invocation =
          CodeBlock.of(
              "new $T($L)",
              toJavaPoet(constructorTypeName(requestingClass)),
              arguments);
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

  private XTypeName constructorTypeName(XClassName requestingClass) {
    XType type = binding.key().type().xprocessing();
    return type.getTypeArguments().stream()
            .allMatch(t -> isTypeAccessibleFrom(t, requestingClass.getPackageName()))
        ? type.asTypeName()
        : type.getRawType().asTypeName();
  }

  private Expression invokeInjectionMethod(XClassName requestingClass) {
    return injectMembers(
        ProvisionMethod.invoke(
            binding,
            request -> dependencyArgument(request, requestingClass).codeBlock(),
            shardImplementation::getUniqueFieldNameForAssistedParam,
            requestingClass,
            moduleReference(requestingClass),
            compilerOptions),
        requestingClass);
  }

  private Expression dependencyArgument(DependencyRequest dependency, XClassName requestingClass) {
    return componentRequestRepresentations.getDependencyArgumentExpression(
        dependency, requestingClass);
  }

  private Expression injectMembers(CodeBlock instance, XClassName requestingClass) {
    if (!hasInjectionSites(binding)) {
      return Expression.create(simpleMethodReturnType(), instance);
    }
    if (isPreJava8SourceVersion(processingEnv)) {
      // Java 7 type inference can't figure out that instance in
      // injectParameterized(Parameterized_Factory.newParameterized()) is Parameterized<T> and not
      // Parameterized<Object>
      if (!binding.key().type().xprocessing().getTypeArguments().isEmpty()) {
        XType keyType = binding.key().type().xprocessing();
        XTypeName keyTypeName = keyType.asTypeName();
        XTypeName rawKeyTypeName = keyType.getRawType().asTypeName();
        instance = CodeBlock.of(
            "($T) ($T) $L",
            toJavaPoet(keyTypeName),
            toJavaPoet(rawKeyTypeName),
            instance);
      }
    }
    return membersInjectionMethods.getInjectExpression(binding.key(), instance, requestingClass);
  }

  private Optional<CodeBlock> moduleReference(XClassName requestingClass) {
    return binding.requiresModuleInstance()
        ? binding
            .contributingModule()
            .map(XTypeElement::getType)
            .map(ComponentRequirement::forModule)
            .map(module -> componentRequirementExpressions.getExpression(module, requestingClass))
        : Optional.empty();
  }

  private XType simpleMethodReturnType() {
    return binding.contributedPrimitiveType().orElse(binding.key().type().xprocessing());
  }

  private boolean requiresInjectionMethod(XClassName requestingClass) {
    XExecutableElement executableElement = asExecutable(binding.bindingElement().get());
    return hasInjectionSites(binding)
        || binding.shouldCheckForNull(compilerOptions)
        || !isElementAccessibleFrom(executableElement, requestingClass.getPackageName())
        // This check should be removable once we drop support for -source 7
        || executableElement.getParameters().stream()
            .map(XExecutableParameterElement::getType)
            .anyMatch(type -> !isRawTypeAccessible(type, requestingClass.getPackageName()));
  }

  private static boolean hasInjectionSites(ContributionBinding binding) {
    switch (binding.kind()) {
      case INJECTION:
        return !((InjectionBinding) binding).injectionSites().isEmpty();
      case ASSISTED_INJECTION:
        return !((AssistedInjectionBinding) binding).injectionSites().isEmpty();
      case PROVISION:
        return false;
      default:
        throw new AssertionError("Unexpected binding kind: " + binding.kind());
    }
  }

  @AssistedFactory
  static interface Factory {
    SimpleMethodRequestRepresentation create(ContributionBinding binding);
  }
}
