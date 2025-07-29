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

package dagger.internal.codegen.writing;

import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.writing.ComponentImplementation.MethodSpecKind.MEMBERS_INJECTION_METHOD;
import static dagger.internal.codegen.xprocessing.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XFunSpecs.methodBuilder;
import static javax.lang.model.element.Modifier.PRIVATE;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XFunSpec;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.binding.AssistedInjectionBinding;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.InjectionBinding;
import dagger.internal.codegen.binding.MembersInjectionBinding;
import dagger.internal.codegen.binding.MembersInjectionBinding.InjectionSite;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.writing.InjectionMethods.InjectionSiteMethod;
import dagger.internal.codegen.xprocessing.XExpression;
import dagger.internal.codegen.xprocessing.XFunSpecs;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;

/** Manages the member injection methods for a component. */
@PerComponentImplementation
final class MembersInjectionMethods {
  private final Map<Key, XExpression> injectMethodExpressions = new LinkedHashMap<>();
  private final ComponentImplementation componentImplementation;
  private final ComponentRequestRepresentations bindingExpressions;
  private final BindingGraph graph;
  private final CompilerOptions compilerOptions;
  private final XProcessingEnv processingEnv;

  @Inject
  MembersInjectionMethods(
      ComponentImplementation componentImplementation,
      ComponentRequestRepresentations bindingExpressions,
      BindingGraph graph,
      CompilerOptions compilerOptions,
      XProcessingEnv processingEnv) {
    this.componentImplementation = componentImplementation;
    this.bindingExpressions = bindingExpressions;
    this.graph = graph;
    this.compilerOptions = compilerOptions;
    this.processingEnv = processingEnv;
  }

  /**
   * Returns the members injection {@link XExpression} for the given {@link Key}, creating it if
   * necessary.
   */
  XExpression getInjectExpression(Key key, XCodeBlock instance, XClassName requestingClass) {
    Binding binding =
        graph.localMembersInjectionBinding(key).isPresent()
            ? graph.localMembersInjectionBinding(key).get()
            : graph.localContributionBinding(key).get();
    XExpression expression =
        reentrantComputeIfAbsent(
            injectMethodExpressions, key, k -> injectMethodExpression(binding));
    ShardImplementation shardImplementation = componentImplementation.shardImplementation(binding);
    return XExpression.create(
        expression.type(),
        shardImplementation.name().equals(requestingClass)
            ? XCodeBlock.of("%L(%L)", expression.codeBlock(), instance)
            : XCodeBlock.of(
                "%L.%L(%L)",
                shardImplementation.shardFieldReference(), expression.codeBlock(), instance));
  }

  private XExpression injectMethodExpression(Binding binding) {
    // TODO(bcorso): move Switching Providers and injection methods to Shard classes to avoid
    // exceeding component class constant pool limit.
    // Add to Component Shard so that is can be accessible from Switching Providers.
    ShardImplementation shardImplementation = componentImplementation.shardImplementation(binding);
    XType keyType = binding.key().type().xprocessing();
    XType membersInjectedType =
        isTypeAccessibleFrom(keyType, shardImplementation.name().getPackageName())
            ? keyType
            : processingEnv.requireType(XTypeName.ANY_OBJECT);
    String bindingTypeName = getSimpleName(binding.bindingTypeElement().get());
    // TODO(ronshapiro): include type parameters in this name e.g. injectFooOfT, and outer class
    // simple names Foo.Builder -> injectFooBuilder
    String methodName = shardImplementation.getUniqueMethodName("inject" + bindingTypeName);
    // Technically this usage only needs to be unique within this method, but this will allocate a
    // unique name within the shard. We could optimize this by cloning the UniqueNameSet or using
    // NameAllocator which has a clone method in the future.
    String instanceName = shardImplementation.getUniqueFieldName("instance");
    XTypeName instanceTypeName = membersInjectedType.asTypeName();
    XFunSpecs.Builder methodBuilder =
        methodBuilder(methodName)
            .addModifiers(PRIVATE)
            .returns(instanceTypeName)
            .addParameter(instanceName, instanceTypeName);
    XTypeElement canIgnoreReturnValue =
        processingEnv.findTypeElement("com.google.errorprone.annotations.CanIgnoreReturnValue");
    if (canIgnoreReturnValue != null) {
      methodBuilder.addAnnotation(canIgnoreReturnValue.asClassName());
    }
    XCodeBlock instance = XCodeBlock.of("%N", instanceName);
    XCodeBlock invokeInjectionSites =
        InjectionSiteMethod.invokeAll(
            injectionSites(binding),
            shardImplementation.name(),
            instance,
            membersInjectedType,
            request ->
                bindingExpressions
                    .getDependencyArgumentExpression(request, shardImplementation.name())
                    .codeBlock());
    methodBuilder.addCode(invokeInjectionSites).addStatement("return %L", instance);

    XFunSpec method = methodBuilder.build();
    shardImplementation.addMethod(MEMBERS_INJECTION_METHOD, method);
    return XExpression.create(membersInjectedType, XCodeBlock.of("%N", method));
  }

  private static ImmutableSet<InjectionSite> injectionSites(Binding binding) {
    switch (binding.kind()) {
      case INJECTION:
        return ((InjectionBinding) binding).injectionSites();
      case ASSISTED_INJECTION:
        return ((AssistedInjectionBinding) binding).injectionSites();
      case MEMBERS_INJECTION:
        return ((MembersInjectionBinding) binding).injectionSites();
      default:
        throw new IllegalArgumentException("Unexpected binding kind: " + binding.kind());
    }
  }
}
