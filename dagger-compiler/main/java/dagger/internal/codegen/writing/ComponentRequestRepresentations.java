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

import static androidx.room.compiler.processing.XTypeKt.isVoid;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static dagger.internal.codegen.xprocessing.Accessibility.isRawTypeAccessible;
import static dagger.internal.codegen.xprocessing.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.xprocessing.XCodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.xprocessing.XFunSpecs.overriding;
import static dagger.internal.codegen.xprocessing.XProcessingEnvs.isPreJava8SourceVersion;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XFunSpec;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.OptionalType;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.BindingRequest;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.FrameworkType;
import dagger.internal.codegen.binding.FrameworkTypeMapper;
import dagger.internal.codegen.binding.MembersInjectionBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.model.RequestKind;
import dagger.internal.codegen.xprocessing.XCodeBlocks;
import dagger.internal.codegen.xprocessing.XExpression;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

/** A central repository of code expressions used to access any binding available to a component. */
@PerComponentImplementation
public final class ComponentRequestRepresentations {
  // TODO(dpb,ronshapiro): refactor this and ComponentRequirementExpressions into a
  // HierarchicalComponentMap<K, V>, or perhaps this use a flattened ImmutableMap, built from its
  // parents? If so, maybe make RequestRepresentation.Factory create it.

  private final Optional<ComponentRequestRepresentations> parent;
  private final BindingGraph graph;
  private final ComponentImplementation componentImplementation;
  private final ComponentRequirementExpressions componentRequirementExpressions;
  private final MembersInjectionBindingRepresentation.Factory
      membersInjectionBindingRepresentationFactory;
  private final ProvisionBindingRepresentation.Factory provisionBindingRepresentationFactory;
  private final ProductionBindingRepresentation.Factory productionBindingRepresentationFactory;
  private final Map<Binding, BindingRepresentation> representations = new HashMap<>();
  private final XProcessingEnv processingEnv;
  private final CompilerOptions compilerOptions;

  @Inject
  ComponentRequestRepresentations(
      @ParentComponent Optional<ComponentRequestRepresentations> parent,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
      ComponentRequirementExpressions componentRequirementExpressions,
      MembersInjectionBindingRepresentation.Factory membersInjectionBindingRepresentationFactory,
      ProvisionBindingRepresentation.Factory provisionBindingRepresentationFactory,
      ProductionBindingRepresentation.Factory productionBindingRepresentationFactory,
      XProcessingEnv processingEnv,
      CompilerOptions compilerOptions) {
    this.parent = parent;
    this.graph = graph;
    this.componentImplementation = componentImplementation;
    this.membersInjectionBindingRepresentationFactory =
        membersInjectionBindingRepresentationFactory;
    this.provisionBindingRepresentationFactory = provisionBindingRepresentationFactory;
    this.productionBindingRepresentationFactory = productionBindingRepresentationFactory;
    this.componentRequirementExpressions = checkNotNull(componentRequirementExpressions);
    this.processingEnv = processingEnv;
    this.compilerOptions = compilerOptions;
  }

  /**
   * Returns an expression that evaluates to the value of a binding request for a binding owned by
   * this component or an ancestor.
   *
   * @param requestingClass the class that will contain the expression
   * @throws IllegalStateException if there is no binding expression that satisfies the request
   */
  public XExpression getDependencyExpression(BindingRequest request, XClassName requestingClass) {
    return getRequestRepresentation(request).getDependencyExpression(requestingClass);
  }

  /**
   * Equivalent to {@link #getDependencyExpression(BindingRequest, XClassName)} that is used only
   * when the request is for implementation of a component method.
   *
   * @throws IllegalStateException if there is no binding expression that satisfies the request
   */
  XExpression getDependencyExpressionForComponentMethod(
      BindingRequest request,
      ComponentMethodDescriptor componentMethod,
      ComponentImplementation componentImplementation) {
    return getRequestRepresentation(request)
        .getDependencyExpressionForComponentMethod(componentMethod, componentImplementation);
  }

  /**
   * Returns the {@link CodeBlock} for the method arguments used with the factory {@code create()}
   * method for the given {@link ContributionBinding binding}.
   */
  XCodeBlock getCreateMethodArgumentsCodeBlock(
      ContributionBinding binding, XClassName requestingClass) {
    return makeParametersCodeBlock(getCreateMethodArgumentsCodeBlocks(binding, requestingClass));
  }

  private ImmutableList<XCodeBlock> getCreateMethodArgumentsCodeBlocks(
      ContributionBinding binding, XClassName requestingClass) {
    ImmutableList.Builder<XCodeBlock> arguments = ImmutableList.builder();

    if (binding.requiresModuleInstance()) {
      arguments.add(
          componentRequirementExpressions.getExpressionDuringInitialization(
              ComponentRequirement.forModule(binding.contributingModule().get().getType()),
              requestingClass));
    }

    binding.dependencies().stream()
        .map(dependency -> frameworkRequest(binding, dependency))
        .map(request -> getDependencyExpression(request, requestingClass))
        .map(XExpression::codeBlock)
        .forEach(arguments::add);

    return arguments.build();
  }

  private static BindingRequest frameworkRequest(
      ContributionBinding binding, DependencyRequest dependency) {
    // TODO(bcorso): See if we can get rid of FrameworkTypeMatcher
    FrameworkType frameworkType =
        FrameworkTypeMapper.forBindingType(binding.bindingType())
            .getFrameworkType(dependency.kind());
    return BindingRequest.bindingRequest(dependency.key(), frameworkType);
  }

  /**
   * Returns an expression that evaluates to the value of a dependency request, for passing to a
   * binding method, an {@code @Inject}-annotated constructor or member, or a proxy for one.
   *
   * <p>If the method is a generated static {@link InjectionMethods injection method}, each
   * parameter will be {@link Object} if the dependency's raw type is inaccessible. If that is the
   * case for this dependency, the returned expression will use a cast to evaluate to the raw type.
   *
   * @param requestingClass the class that will contain the expression
   */
  XExpression getDependencyArgumentExpression(
      DependencyRequest dependencyRequest, XClassName requestingClass) {

    XType dependencyType = dependencyRequest.key().type().xprocessing();
    BindingRequest bindingRequest = bindingRequest(dependencyRequest);
    XExpression dependencyExpression = getDependencyExpression(bindingRequest, requestingClass);

    // The factory method will use a type like Foo<Innaccessible> at the declaration site so we need
    // to cast to the raw type, Foo, at the call site if any type arguments are inaccessible.
    if (dependencyRequest.kind().equals(RequestKind.INSTANCE)
        && !isTypeAccessibleFrom(dependencyType, requestingClass.getPackageName())
        && isRawTypeAccessible(dependencyType, requestingClass.getPackageName())) {
      return dependencyExpression.castTo(dependencyType.getRawType());
    }

    return dependencyExpression;
  }

  /** Returns the implementation of a component method. */
  public XFunSpec getComponentMethod(ComponentMethodDescriptor componentMethod) {
    return overriding(
            componentMethod.methodElement(),
            graph.componentTypeElement().getType(),
            compilerOptions)
        .addCode(getComponentMethodCodeBlock(componentMethod))
        .build();
  }

  private XCodeBlock getComponentMethodCodeBlock(ComponentMethodDescriptor componentMethod) {
    XExpression expression = getComponentMethodExpression(componentMethod);
    if (isVoid(componentMethod.methodElement().getReturnType())) {
      return XCodeBlocks.isEmpty(expression.codeBlock())
          ? expression.codeBlock()
          : XCodeBlock.of("%L;", expression.codeBlock());
    }
    return XCodeBlock.of("return %L;", expression.codeBlock());
  }

  private XExpression getComponentMethodExpression(ComponentMethodDescriptor componentMethod) {
    checkArgument(componentMethod.dependencyRequest().isPresent());
    BindingRequest request = bindingRequest(componentMethod.dependencyRequest().get());
    RequestRepresentation requestRepresentation = getRequestRepresentation(request);

    XExpression expression =
        requestRepresentation.getDependencyExpressionForComponentMethod(
            componentMethod, componentImplementation);

    // Cast if the expression type does not match the component method's return type. This is useful
    // for types that have protected accessibility to the component but are not accessible to other
    // classes, e.g. shards, that may need to handle the implementation of the binding.
    XType returnType =
        componentMethod.methodElement()
            .asMemberOf(componentImplementation.graph().componentTypeElement().getType())
            .getReturnType();

    if (isVoid(returnType)) {
      return expression;
    }

    // When compiling with -source 7, javac's type inference isn't strong enough to match things
    // like Optional<javax.inject.Provider<T>> to Optional<dagger.internal.Provider<T>>.
    if (isPreJava8SourceVersion(processingEnv)
        && (MapType.isMapOfProvider(returnType)
            || OptionalType.isOptionalProviderType(returnType))) {
      return expression.castTo(returnType.getRawType());
    }

    return !expression.type().isAssignableTo(returnType)
        ? expression.castTo(returnType)
        : expression;
  }

  /** Returns the {@link RequestRepresentation} for the given {@link BindingRequest}. */
  RequestRepresentation getRequestRepresentation(BindingRequest request) {
    Optional<Binding> localBinding =
        request.isRequestKind(RequestKind.MEMBERS_INJECTION)
            ? graph.localMembersInjectionBinding(request.key())
            : graph.localContributionBinding(request.key());

    if (localBinding.isPresent()) {
      return getBindingRepresentation(localBinding.get()).getRequestRepresentation(request);
    }

    checkArgument(parent.isPresent(), "no expression found for %s", request);
    return parent.get().getRequestRepresentation(request);
  }

  private BindingRepresentation getBindingRepresentation(Binding binding) {
    return reentrantComputeIfAbsent(
        representations, binding, this::getBindingRepresentationUncached);
  }

  private BindingRepresentation getBindingRepresentationUncached(Binding binding) {
    switch (binding.bindingType()) {
      case MEMBERS_INJECTION:
        return membersInjectionBindingRepresentationFactory.create(
            (MembersInjectionBinding) binding);
      case PROVISION:
        return provisionBindingRepresentationFactory.create((ContributionBinding) binding);
      case PRODUCTION:
        return productionBindingRepresentationFactory.create((ContributionBinding) binding);
    }
    throw new AssertionError();
  }
}
