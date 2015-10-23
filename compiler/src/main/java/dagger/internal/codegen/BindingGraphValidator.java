/*
 * Copyright (C) 2015 Google, Inc.
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

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import dagger.Component;
import dagger.internal.codegen.ComponentDescriptor.BuilderSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.ContributionBinding.BindingType;
import dagger.internal.codegen.writer.TypeNames;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Formatter;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.auto.common.MoreTypes.isTypeOf;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentDependencies;
import static dagger.internal.codegen.ErrorMessages.INDENT;
import static dagger.internal.codegen.ErrorMessages.MEMBERS_INJECTION_WITH_UNBOUNDED_TYPE;
import static dagger.internal.codegen.ErrorMessages.NULLABLE_TO_NON_NULLABLE;
import static dagger.internal.codegen.ErrorMessages.REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_FORMAT;
import static dagger.internal.codegen.ErrorMessages.REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_OR_PRODUCER_FORMAT;
import static dagger.internal.codegen.ErrorMessages.REQUIRES_PROVIDER_FORMAT;
import static dagger.internal.codegen.ErrorMessages.REQUIRES_PROVIDER_OR_PRODUCER_FORMAT;
import static dagger.internal.codegen.ErrorMessages.stripCommonTypePrefixes;
import static dagger.internal.codegen.InjectionAnnotations.getScopeAnnotation;

public class BindingGraphValidator implements Validator<BindingGraph> {

  private final Types types;
  private final InjectBindingRegistry injectBindingRegistry;
  private final ValidationType scopeCycleValidationType;
  private final Diagnostic.Kind nullableValidationType;
  private final ProvisionBindingFormatter provisionBindingFormatter;
  private final ProductionBindingFormatter productionBindingFormatter;
  private final MethodSignatureFormatter methodSignatureFormatter;
  private final DependencyRequestFormatter dependencyRequestFormatter;
  private final KeyFormatter keyFormatter;

  BindingGraphValidator(
      Types types,
      InjectBindingRegistry injectBindingRegistry,
      ValidationType scopeCycleValidationType,
      Diagnostic.Kind nullableValidationType,
      ProvisionBindingFormatter provisionBindingFormatter,
      ProductionBindingFormatter productionBindingFormatter,
      MethodSignatureFormatter methodSignatureFormatter,
      DependencyRequestFormatter dependencyRequestFormatter,
      KeyFormatter keyFormatter) {
    this.types = types;
    this.injectBindingRegistry = injectBindingRegistry;
    this.scopeCycleValidationType = scopeCycleValidationType;
    this.nullableValidationType = nullableValidationType;
    this.provisionBindingFormatter = provisionBindingFormatter;
    this.productionBindingFormatter = productionBindingFormatter;
    this.methodSignatureFormatter = methodSignatureFormatter;
    this.dependencyRequestFormatter = dependencyRequestFormatter;
    this.keyFormatter = keyFormatter;
  }

  private class Validation {
    final BindingGraph topLevelGraph;
    final ValidationReport.Builder<BindingGraph> reportBuilder;

    Validation(BindingGraph topLevelGraph) {
      this.topLevelGraph = topLevelGraph;
      this.reportBuilder = ValidationReport.Builder.about(topLevelGraph);
    }

    ValidationReport<BindingGraph> buildReport() {
      return reportBuilder.build();
    }

    void validateSubgraph(BindingGraph subject) {
      validateComponentScope(subject);
      validateDependencyScopes(subject);
      validateBuilders(subject);

      for (ComponentMethodDescriptor componentMethod :
           subject.componentDescriptor().componentMethods()) {
        Optional<DependencyRequest> entryPoint = componentMethod.dependencyRequest();
        if (entryPoint.isPresent()) {
          traverseRequest(entryPoint.get(), new ArrayDeque<ResolvedRequest>(),
              Sets.<BindingKey>newHashSet(), subject,
              Sets.<DependencyRequest>newHashSet());
        }
      }

      for (BindingGraph subgraph : subject.subgraphs().values()) {
        validateSubgraph(subgraph);
      }
    }

    private void traverseRequest(
        DependencyRequest request,
        Deque<ResolvedRequest> bindingPath,
        Set<BindingKey> keysInPath,
        BindingGraph graph,
        Set<DependencyRequest> resolvedRequests) {
      verify(bindingPath.size() == keysInPath.size(),
          "mismatched path vs keys -- (%s vs %s)", bindingPath, keysInPath);
      BindingKey requestKey = request.bindingKey();
      if (keysInPath.contains(requestKey)) {
        reportCycle(request, bindingPath);
        return;
      }

      // If request has already been resolved, avoid re-traversing the binding path.
      if (resolvedRequests.add(request)) {
        ResolvedRequest resolvedRequest = ResolvedRequest.create(request, graph);
        bindingPath.push(resolvedRequest);
        keysInPath.add(requestKey);
        validateResolvedBinding(bindingPath, resolvedRequest.binding());

        for (Binding binding : resolvedRequest.binding().bindings()) {
          for (DependencyRequest nextRequest : binding.implicitDependencies()) {
            traverseRequest(nextRequest, bindingPath, keysInPath, graph, resolvedRequests);
          }
        }
        bindingPath.poll();
        keysInPath.remove(requestKey);
      }
    }

    /**
     * Validates that the set of bindings resolved is consistent with the type of the binding, and
     * returns true if the bindings are valid.
     */
    private boolean validateResolvedBinding(
        Deque<ResolvedRequest> path, ResolvedBindings resolvedBinding) {
      if (resolvedBinding.bindings().isEmpty()) {
        reportMissingBinding(path);
        return false;
      }

      ImmutableSet.Builder<ProvisionBinding> provisionBindingsBuilder =
          ImmutableSet.builder();
      ImmutableSet.Builder<ProductionBinding> productionBindingsBuilder =
          ImmutableSet.builder();
      ImmutableSet.Builder<MembersInjectionBinding> membersInjectionBindingsBuilder =
          ImmutableSet.builder();
      for (Binding binding : resolvedBinding.bindings()) {
        if (binding instanceof ProvisionBinding) {
          provisionBindingsBuilder.add((ProvisionBinding) binding);
        }
        if (binding instanceof ProductionBinding) {
          productionBindingsBuilder.add((ProductionBinding) binding);
        }
        if (binding instanceof MembersInjectionBinding) {
          membersInjectionBindingsBuilder.add((MembersInjectionBinding) binding);
        }
      }
      ImmutableSet<ProvisionBinding> provisionBindings = provisionBindingsBuilder.build();
      ImmutableSet<ProductionBinding> productionBindings = productionBindingsBuilder.build();
      ImmutableSet<MembersInjectionBinding> membersInjectionBindings =
          membersInjectionBindingsBuilder.build();

      switch (resolvedBinding.bindingKey().kind()) {
        case CONTRIBUTION:
          if (!membersInjectionBindings.isEmpty()) {
            throw new IllegalArgumentException(
                "contribution binding keys should never have members injection bindings");
          }
          Set<ContributionBinding> combined = Sets.union(provisionBindings, productionBindings);
          if (!validateNullability(path.peek().request(), combined)) {
            return false;
          }
          if (!productionBindings.isEmpty() && doesPathRequireProvisionOnly(path)) {
            reportProviderMayNotDependOnProducer(path);
            return false;
          }
          if ((provisionBindings.size() + productionBindings.size()) <= 1) {
            return true;
          }
          ImmutableListMultimap<BindingType, ContributionBinding> bindingsByType =
              ContributionBinding.bindingTypesFor(
                  Iterables.<ContributionBinding>concat(provisionBindings, productionBindings));
          if (bindingsByType.keySet().size() > 1) {
            reportMultipleBindingTypes(path);
            return false;
          } else if (getOnlyElement(bindingsByType.keySet()).equals(BindingType.UNIQUE)) {
            reportDuplicateBindings(path);
            return false;
          }
          break;
        case MEMBERS_INJECTION:
          if (!provisionBindings.isEmpty() || !productionBindings.isEmpty()) {
            throw new IllegalArgumentException(
                "members injection binding keys should never have contribution bindings");
          }
          if (membersInjectionBindings.size() > 1) {
            reportDuplicateBindings(path);
            return false;
          }
          if (membersInjectionBindings.size() == 1) {
            MembersInjectionBinding binding = getOnlyElement(membersInjectionBindings);
            if (!validateMembersInjectionBinding(binding, path)) {
              return false;
            }
          }
          break;
        default:
          throw new AssertionError();
      }
      return true;
    }

    /** Ensures that if the request isn't nullable, then each contribution is also not nullable. */
    private boolean validateNullability(
        DependencyRequest request, Set<ContributionBinding> bindings) {
      boolean valid = true;
      if (!request.isNullable()) {
        String typeName = null;
        for (ContributionBinding binding : bindings) {
          if (binding.nullableType().isPresent()) {
            String methodSignature;
            if (binding instanceof ProvisionBinding) {
              ProvisionBinding provisionBinding = (ProvisionBinding) binding;
              methodSignature = provisionBindingFormatter.format(provisionBinding);
            } else {
              ProductionBinding productionBinding = (ProductionBinding) binding;
              methodSignature = productionBindingFormatter.format(productionBinding);
            }
            // Note: the method signature will include the @Nullable in it!
            // TODO(sameb): Sometimes javac doesn't include the Element in its output.
            // (Maybe this happens if the code was already compiled before this point?)
            // ... we manually print ouf the request in that case, otherwise the error
            // message is kind of useless.
            if (typeName == null) {
              typeName = TypeNames.forTypeMirror(request.key().type()).toString();
            }
            reportBuilder.addItem(
                String.format(NULLABLE_TO_NON_NULLABLE, typeName, methodSignature)
                + "\n at: " + dependencyRequestFormatter.format(request),
                nullableValidationType,
                request.requestElement());
            valid = false;
          }
        }
      }
      return valid;
    }

    /**
     * Validates a members injection binding, returning false (and reporting the error) if it wasn't
     * valid.
     */
    private boolean validateMembersInjectionBinding(
        MembersInjectionBinding binding, final Deque<ResolvedRequest> path) {
      return binding.key().type().accept(new SimpleTypeVisitor6<Boolean, Void>() {
        @Override protected Boolean defaultAction(TypeMirror e, Void p) {
          reportBuilder.addItem("Invalid members injection request.",
              path.peek().request().requestElement());
          return false;
        }

        @Override public Boolean visitDeclared(DeclaredType type, Void ignored) {
          // If the key has type arguments, validate that each type argument is declared.
          // Otherwise the type argument may be a wildcard (or other type), and we can't
          // resolve that to actual types.  If the arg was an array, validate the type
          // of the array.
          for (TypeMirror arg : type.getTypeArguments()) {
            boolean declared;
            switch (arg.getKind()) {
              case ARRAY:
                declared = MoreTypes.asArray(arg).getComponentType().accept(
                    new SimpleTypeVisitor6<Boolean, Void>() {
                      @Override protected Boolean defaultAction(TypeMirror e, Void p) {
                        return false;
                      }

                      @Override public Boolean visitDeclared(DeclaredType t, Void p) {
                        for (TypeMirror arg : t.getTypeArguments()) {
                          if (!arg.accept(this, null)) {
                            return false;
                          }
                        }
                        return true;
                      }

                      @Override public Boolean visitArray(ArrayType t, Void p) {
                        return t.getComponentType().accept(this, null);
                      }

                      @Override public Boolean visitPrimitive(PrimitiveType t, Void p) {
                        return true;
                      }
                    }, null);
                break;
              case DECLARED:
                declared = true;
                break;
              default:
                declared = false;
            }
            if (!declared) {
              ImmutableList<String> printableDependencyPath = FluentIterable.from(path)
                  .transform(REQUEST_FROM_RESOLVED_REQUEST)
                  .transform(dependencyRequestFormatter)
                  .filter(Predicates.not(Predicates.equalTo("")))
                  .toList()
                  .reverse();
              reportBuilder.addItem(
                  String.format(MEMBERS_INJECTION_WITH_UNBOUNDED_TYPE,
                      arg.toString(),
                      type.toString(),
                      Joiner.on('\n').join(printableDependencyPath)),
                      path.peek().request().requestElement());
              return false;
            }
          }

          TypeElement element = MoreElements.asType(type.asElement());
          // Also validate that the key is not the erasure of a generic type.
          // If it is, that means the user referred to Foo<T> as just 'Foo',
          // which we don't allow.  (This is a judgement call -- we *could*
          // allow it and instantiate the type bounds... but we don't.)
          if (!MoreTypes.asDeclared(element.asType()).getTypeArguments().isEmpty()
              && types.isSameType(types.erasure(element.asType()), type)) {
              ImmutableList<String> printableDependencyPath = FluentIterable.from(path)
                  .transform(REQUEST_FROM_RESOLVED_REQUEST)
                  .transform(dependencyRequestFormatter)
                  .filter(Predicates.not(Predicates.equalTo("")))
                  .toList()
                  .reverse();
            reportBuilder.addItem(
                String.format(ErrorMessages.MEMBERS_INJECTION_WITH_RAW_TYPE,
                    type.toString(),
                    Joiner.on('\n').join(printableDependencyPath)),
                path.peek().request().requestElement());
            return false;
          }

          return true; // valid
        }
      }, null);
    }

    /**
     * Validates that among the dependencies are at most one scoped dependency,
     * that there are no cycles within the scoping chain, and that singleton
     * components have no scoped dependencies.
     */
    private void validateDependencyScopes(BindingGraph subject) {
      ComponentDescriptor descriptor = subject.componentDescriptor();
      Optional<AnnotationMirror> scope = descriptor.scope();
      ImmutableSet<TypeElement> scopedDependencies = scopedTypesIn(descriptor.dependencies());
      if (scope.isPresent()) {
        // Dagger 1.x scope compatibility requires this be suppress-able.
        if (scopeCycleValidationType.diagnosticKind().isPresent()
            && isTypeOf(Singleton.class, scope.get().getAnnotationType())) {
          // Singleton is a special-case representing the longest lifetime, and therefore
          // @Singleton components may not depend on scoped components
          if (!scopedDependencies.isEmpty()) {
            StringBuilder message = new StringBuilder(
                "This @Singleton component cannot depend on scoped components:\n");
            appendIndentedComponentsList(message, scopedDependencies);
            reportBuilder.addItem(message.toString(),
                scopeCycleValidationType.diagnosticKind().get(),
                descriptor.componentDefinitionType(),
                descriptor.componentAnnotation());
          }
        } else if (scopedDependencies.size() > 1) {
          // Scoped components may depend on at most one scoped component.
          StringBuilder message = new StringBuilder(ErrorMessages.format(scope.get()))
              .append(' ')
              .append(descriptor.componentDefinitionType().getQualifiedName())
              .append(" depends on more than one scoped component:\n");
          appendIndentedComponentsList(message, scopedDependencies);
          reportBuilder.addItem(message.toString(),
              descriptor.componentDefinitionType(),
              descriptor.componentAnnotation());
        } else {
          // Dagger 1.x scope compatibility requires this be suppress-able.
          if (!scopeCycleValidationType.equals(ValidationType.NONE)) {
            validateScopeHierarchy(descriptor.componentDefinitionType(),
                descriptor.componentDefinitionType(),
                new ArrayDeque<Equivalence.Wrapper<AnnotationMirror>>(),
                new ArrayDeque<TypeElement>());
          }
        }
      } else {
        // Scopeless components may not depend on scoped components.
        if (!scopedDependencies.isEmpty()) {
          StringBuilder message =
              new StringBuilder(descriptor.componentDefinitionType().getQualifiedName())
                  .append(" (unscoped) cannot depend on scoped components:\n");
          appendIndentedComponentsList(message, scopedDependencies);
          reportBuilder.addItem(message.toString(),
              descriptor.componentDefinitionType(),
              descriptor.componentAnnotation());
        }
      }
    }

    private void validateBuilders(BindingGraph subject) {
      ComponentDescriptor componentDesc = subject.componentDescriptor();
      if (!componentDesc.builderSpec().isPresent()) {
        // If no builder, nothing to validate.
        return;
      }

      Set<TypeElement> allDependents = subject.componentRequirements();
      Set<TypeElement> requiredDependents =
          Sets.filter(allDependents, new Predicate<TypeElement>() {
            @Override public boolean apply(TypeElement input) {
              return !Util.componentCanMakeNewInstances(input);
            }
          });
      final BuilderSpec spec = componentDesc.builderSpec().get();
      Map<TypeElement, ExecutableElement> allSetters = spec.methodMap();

      ErrorMessages.ComponentBuilderMessages msgs =
          ErrorMessages.builderMsgsFor(subject.componentDescriptor().kind());
      Set<TypeElement> extraSetters = Sets.difference(allSetters.keySet(), allDependents);
      if (!extraSetters.isEmpty()) {
        Collection<ExecutableElement> excessMethods =
            Maps.filterKeys(allSetters, Predicates.in(extraSetters)).values();
        Iterable<String> formatted = FluentIterable.from(excessMethods).transform(
            new Function<ExecutableElement, String>() {
              @Override public String apply(ExecutableElement input) {
                return methodSignatureFormatter.format(input,
                    Optional.of(MoreTypes.asDeclared(spec.builderDefinitionType().asType())));
              }});
        reportBuilder.addItem(String.format(msgs.extraSetters(), formatted),
            spec.builderDefinitionType());
      }

      Set<TypeElement> missingSetters = Sets.difference(requiredDependents, allSetters.keySet());
      if (!missingSetters.isEmpty()) {
        reportBuilder.addItem(String.format(msgs.missingSetters(), missingSetters),
            spec.builderDefinitionType());
      }
    }

    /**
     * Validates that scopes do not participate in a scoping cycle - that is to say, scoped
     * components are in a hierarchical relationship terminating with Singleton.
     *
     * <p>As a side-effect, this means scoped components cannot have a dependency cycle between
     * themselves, since a component's presence within its own dependency path implies a cyclical
     * relationship between scopes.
     */
    private void validateScopeHierarchy(TypeElement rootComponent,
        TypeElement componentType,
        Deque<Equivalence.Wrapper<AnnotationMirror>> scopeStack,
        Deque<TypeElement> scopedDependencyStack) {
      Optional<AnnotationMirror> scope = getScopeAnnotation(componentType);
      if (scope.isPresent()) {
        Equivalence.Wrapper<AnnotationMirror> wrappedScope =
            AnnotationMirrors.equivalence().wrap(scope.get());
        if (scopeStack.contains(wrappedScope)) {
          scopedDependencyStack.push(componentType);
          // Current scope has already appeared in the component chain.
          StringBuilder message = new StringBuilder();
          message.append(rootComponent.getQualifiedName());
          message.append(" depends on scoped components in a non-hierarchical scope ordering:\n");
          appendIndentedComponentsList(message, scopedDependencyStack);
          if (scopeCycleValidationType.diagnosticKind().isPresent()) {
            reportBuilder.addItem(message.toString(),
                scopeCycleValidationType.diagnosticKind().get(),
                rootComponent, getAnnotationMirror(rootComponent, Component.class).get());
          }
          scopedDependencyStack.pop();
        } else {
          Optional<AnnotationMirror> componentAnnotation =
              getAnnotationMirror(componentType, Component.class);
          if (componentAnnotation.isPresent()) {
            ImmutableSet<TypeElement> scopedDependencies = scopedTypesIn(
                MoreTypes.asTypeElements(getComponentDependencies(componentAnnotation.get())));
            if (scopedDependencies.size() == 1) {
              // empty can be ignored (base-case), and > 1 is a different error reported separately.
              scopeStack.push(wrappedScope);
              scopedDependencyStack.push(componentType);
              validateScopeHierarchy(rootComponent, getOnlyElement(scopedDependencies),
                  scopeStack, scopedDependencyStack);
              scopedDependencyStack.pop();
              scopeStack.pop();
            }
          } // else: we skip component dependencies which are not components
        }
      }
    }

    /**
     * Validates that the scope (if any) of this component are compatible with the scopes of the
     * bindings available in this component
     */
    void validateComponentScope(final BindingGraph subject) {
      ImmutableMap<BindingKey, ResolvedBindings> resolvedBindings = subject.resolvedBindings();
      Optional<Equivalence.Wrapper<AnnotationMirror>> componentScope =
          subject.componentDescriptor().wrappedScope();
      ImmutableSet.Builder<String> incompatiblyScopedMethodsBuilder = ImmutableSet.builder();
      for (ResolvedBindings bindings : resolvedBindings.values()) {
        if (bindings.bindingKey().kind().equals(BindingKey.Kind.CONTRIBUTION)) {
          for (ContributionBinding contributionBinding : bindings.ownedContributionBindings()) {
            if (contributionBinding instanceof ProvisionBinding) {
              ProvisionBinding provisionBinding = (ProvisionBinding) contributionBinding;
              if (provisionBinding.scope().isPresent()
                  && !componentScope.equals(provisionBinding.wrappedScope())) {
                // Scoped components cannot reference bindings to @Provides methods or @Inject
                // types decorated by a different scope annotation. Unscoped components cannot
                // reference to scoped @Provides methods or @Inject types decorated by any
                // scope annotation.
                switch (provisionBinding.bindingKind()) {
                  case PROVISION:
                    ExecutableElement provisionMethod =
                        MoreElements.asExecutable(provisionBinding.bindingElement());
                    incompatiblyScopedMethodsBuilder.add(
                        methodSignatureFormatter.format(provisionMethod));
                    break;
                  case INJECTION:
                    incompatiblyScopedMethodsBuilder.add(stripCommonTypePrefixes(
                        provisionBinding.scope().get().toString()) + " class "
                            + provisionBinding.bindingTypeElement().getQualifiedName());
                    break;
                  default:
                    throw new IllegalStateException();
                }
              }
            }
          }
        }
      }
      ImmutableSet<String> incompatiblyScopedMethods = incompatiblyScopedMethodsBuilder.build();
      if (!incompatiblyScopedMethods.isEmpty()) {
        TypeElement componentType = subject.componentDescriptor().componentDefinitionType();
        StringBuilder message = new StringBuilder(componentType.getQualifiedName());
        if (componentScope.isPresent()) {
          message.append(" scoped with ");
          message.append(stripCommonTypePrefixes(ErrorMessages.format(componentScope.get().get())));
          message.append(" may not reference bindings with different scopes:\n");
        } else {
          message.append(" (unscoped) may not reference scoped bindings:\n");
        }
        for (String method : incompatiblyScopedMethods) {
          message.append(ErrorMessages.INDENT).append(method).append("\n");
        }
        reportBuilder.addItem(message.toString(), componentType,
            subject.componentDescriptor().componentAnnotation());
      }
    }

    @SuppressWarnings("resource") // Appendable is a StringBuilder.
    private void reportProviderMayNotDependOnProducer(Deque<ResolvedRequest> path) {
      StringBuilder errorMessage = new StringBuilder();
      if (path.size() == 1) {
        new Formatter(errorMessage).format(
            ErrorMessages.PROVIDER_ENTRY_POINT_MAY_NOT_DEPEND_ON_PRODUCER_FORMAT,
            keyFormatter.format(path.peek().request().key()));
      } else {
        ImmutableSet<ProvisionBinding> dependentProvisions =
            provisionsDependingOnLatestRequest(path);
        // TODO(beder): Consider displaying all dependent provisions in the error message. If we do
        // that, should we display all productions that depend on them also?
        new Formatter(errorMessage).format(ErrorMessages.PROVIDER_MAY_NOT_DEPEND_ON_PRODUCER_FORMAT,
            keyFormatter.format(dependentProvisions.iterator().next().key()));
      }
      reportBuilder.addItem(errorMessage.toString(), path.getLast().request().requestElement());
    }

    private void reportMissingBinding(Deque<ResolvedRequest> path) {
      Key key = path.peek().request().key();
      BindingKey bindingKey = path.peek().request().bindingKey();
      TypeMirror type = key.type();
      String typeName = TypeNames.forTypeMirror(type).toString();
      boolean requiresContributionMethod = !key.isValidImplicitProvisionKey(types);
      boolean requiresProvision = doesPathRequireProvisionOnly(path);
      StringBuilder errorMessage = new StringBuilder();
      String requiresErrorMessageFormat = requiresContributionMethod
          ? requiresProvision
              ? REQUIRES_PROVIDER_FORMAT
              : REQUIRES_PROVIDER_OR_PRODUCER_FORMAT
          : requiresProvision
              ? REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_FORMAT
              : REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_OR_PRODUCER_FORMAT;
      errorMessage.append(String.format(requiresErrorMessageFormat, typeName));
      if (key.isValidMembersInjectionKey()
          && !injectBindingRegistry.getOrFindMembersInjectionBinding(key).injectionSites()
              .isEmpty()) {
        errorMessage.append(" ").append(ErrorMessages.MEMBERS_INJECTION_DOES_NOT_IMPLY_PROVISION);
      }
      ImmutableList<String> printableDependencyPath =
          FluentIterable.from(path)
              .transform(REQUEST_FROM_RESOLVED_REQUEST)
              .transform(dependencyRequestFormatter)
              .filter(Predicates.not(Predicates.equalTo("")))
              .toList()
              .reverse();
      for (String dependency :
          printableDependencyPath.subList(1, printableDependencyPath.size())) {
        errorMessage.append('\n').append(dependency);
      }
      for (String suggestion : MissingBindingSuggestions.forKey(topLevelGraph, bindingKey)) {
        errorMessage.append('\n').append(suggestion);
      }
      reportBuilder.addItem(errorMessage.toString(), path.getLast().request().requestElement());
    }

    private static final int DUPLICATE_SIZE_LIMIT = 10;

    @SuppressWarnings("resource") // Appendable is a StringBuilder.
    private void reportDuplicateBindings(Deque<ResolvedRequest> path) {
      ResolvedBindings resolvedBinding = path.peek().binding();
      StringBuilder builder = new StringBuilder();
      new Formatter(builder).format(ErrorMessages.DUPLICATE_BINDINGS_FOR_KEY_FORMAT,
          keyFormatter.format(path.peek().request().key()));
      for (Binding binding : Iterables.limit(resolvedBinding.bindings(), DUPLICATE_SIZE_LIMIT)) {
        builder.append('\n').append(INDENT);
        // TODO(beder): Refactor the formatters so we don't need these instanceof checks.
        if (binding instanceof ProvisionBinding) {
          builder.append(provisionBindingFormatter.format((ProvisionBinding) binding));
        } else if (binding instanceof ProductionBinding) {
          builder.append(productionBindingFormatter.format((ProductionBinding) binding));
        }
      }
      int numberOfOtherBindings = resolvedBinding.bindings().size() - DUPLICATE_SIZE_LIMIT;
      if (numberOfOtherBindings > 0) {
        builder.append('\n').append(INDENT)
            .append("and ").append(numberOfOtherBindings).append(" other");
      }
      if (numberOfOtherBindings > 1) {
        builder.append('s');
      }
      reportBuilder.addItem(builder.toString(), path.getLast().request().requestElement());
    }

    @SuppressWarnings("resource") // Appendable is a StringBuilder.
    private void reportMultipleBindingTypes(Deque<ResolvedRequest> path) {
      ResolvedBindings resolvedBinding = path.peek().binding();
      StringBuilder builder = new StringBuilder();
      new Formatter(builder).format(ErrorMessages.MULTIPLE_BINDING_TYPES_FOR_KEY_FORMAT,
          keyFormatter.format(path.peek().request().key()));
      ImmutableListMultimap<BindingType, ContributionBinding> bindingsByType =
          ContributionBinding.bindingTypesFor(resolvedBinding.contributionBindings());
      for (BindingType type :
          Ordering.natural().immutableSortedCopy(bindingsByType.keySet())) {
        builder.append(INDENT);
        builder.append(formatBindingType(type));
        builder.append(" bindings:\n");
        for (ContributionBinding binding : bindingsByType.get(type)) {
          builder.append(INDENT).append(INDENT);
          if (binding instanceof ProvisionBinding) {
            builder.append(provisionBindingFormatter.format((ProvisionBinding) binding));
          } else if (binding instanceof ProductionBinding) {
            builder.append(productionBindingFormatter.format((ProductionBinding) binding));
          }
          builder.append('\n');
        }
      }
      reportBuilder.addItem(builder.toString(), path.getLast().request().requestElement());
    }

    private void reportCycle(DependencyRequest request, Deque<ResolvedRequest> path) {
      ImmutableList<DependencyRequest> pathElements = ImmutableList.<DependencyRequest>builder()
          .add(request)
          .addAll(Iterables.transform(path, REQUEST_FROM_RESOLVED_REQUEST))
          .build();
      ImmutableList<String> printableDependencyPath = FluentIterable.from(pathElements)
          .transform(dependencyRequestFormatter)
          .filter(Predicates.not(Predicates.equalTo("")))
          .toList()
          .reverse();
      DependencyRequest rootRequest = path.getLast().request();
      TypeElement componentType =
          MoreElements.asType(rootRequest.requestElement().getEnclosingElement());
      // TODO(cgruber): Restructure to provide a hint for the start and end of the cycle.
      reportBuilder.addItem(
          String.format(ErrorMessages.CONTAINS_DEPENDENCY_CYCLE_FORMAT,
              componentType.getQualifiedName(),
              rootRequest.requestElement().getSimpleName(),
              Joiner.on("\n")
                  .join(printableDependencyPath.subList(1, printableDependencyPath.size()))),
          rootRequest.requestElement());
    }
  }

  @Override
  public ValidationReport<BindingGraph> validate(final BindingGraph subject) {
    Validation validation = new Validation(subject);
    validation.validateSubgraph(subject);
    return validation.buildReport();
  }

  /**
   * Append and format a list of indented component types (with their scope annotations)
   */
  private void appendIndentedComponentsList(StringBuilder message, Iterable<TypeElement> types) {
    for (TypeElement scopedComponent : types) {
      message.append(INDENT);
      Optional<AnnotationMirror> scope = getScopeAnnotation(scopedComponent);
      if (scope.isPresent()) {
        message.append(ErrorMessages.format(scope.get())).append(' ');
      }
      message.append(stripCommonTypePrefixes(scopedComponent.getQualifiedName().toString()))
          .append('\n');
    }
  }

  /**
   * Returns a set of type elements containing only those found in the input set that have
   * a scoping annotation.
   */
  private ImmutableSet<TypeElement> scopedTypesIn(Set<TypeElement> types) {
    return FluentIterable.from(types).filter(new Predicate<TypeElement>() {
      @Override public boolean apply(TypeElement input) {
        return getScopeAnnotation(input).isPresent();
      }
    }).toSet();
  }

  /**
   * Returns whether the given dependency path would require the most recent request to be resolved
   * by only provision bindings.
   */
  private boolean doesPathRequireProvisionOnly(Deque<ResolvedRequest> path) {
    if (path.size() == 1) {
      // if this is an entry-point, then we check the request
      switch (path.peek().request().kind()) {
        case INSTANCE:
        case PROVIDER:
        case LAZY:
        case MEMBERS_INJECTOR:
          return true;
        case PRODUCER:
        case PRODUCED:
        case FUTURE:
          return false;
        default:
          throw new AssertionError();
      }
    }
    // otherwise, the second-most-recent bindings determine whether the most recent one must be a
    // provision
    ImmutableSet<ProvisionBinding> dependentProvisions = provisionsDependingOnLatestRequest(path);
    return !dependentProvisions.isEmpty();
  }

  /**
   * Returns any provision bindings resolved for the second-most-recent request in the given path;
   * that is, returns those provision bindings that depend on the latest request in the path.
   */
  private ImmutableSet<ProvisionBinding> provisionsDependingOnLatestRequest(
      Deque<ResolvedRequest> path) {
    Iterator<ResolvedRequest> iterator = path.iterator();
    final DependencyRequest request = iterator.next().request();
    ResolvedRequest previousResolvedRequest = iterator.next();
    @SuppressWarnings("unchecked")  // validated by instanceof below
    ImmutableSet<ProvisionBinding> bindings = (ImmutableSet<ProvisionBinding>) FluentIterable
        .from(previousResolvedRequest.binding().bindings())
        .filter(new Predicate<Binding>() {
            @Override public boolean apply(Binding binding) {
              return binding instanceof ProvisionBinding
                  && binding.implicitDependencies().contains(request);
            }
        }).toSet();
    return bindings;
  }

  private String formatBindingType(BindingType type) {
    switch(type) {
      case MAP:
        return "Map";
      case SET:
        return "Set";
      case UNIQUE:
        return "Unique";
      default:
        throw new IllegalStateException("Unknown binding type: " + type);
    }
  }

  @AutoValue
  abstract static class ResolvedRequest {
    abstract DependencyRequest request();
    abstract ResolvedBindings binding();

    static ResolvedRequest create(DependencyRequest request, BindingGraph graph) {
      BindingKey bindingKey = request.bindingKey();
      ResolvedBindings resolvedBindings = graph.resolvedBindings().get(bindingKey);
      return new AutoValue_BindingGraphValidator_ResolvedRequest(request,
          resolvedBindings == null
              ? ResolvedBindings.create(bindingKey,
                  ImmutableSet.<Binding>of(), ImmutableSet.<Binding>of())
              : resolvedBindings);
    }
  }

  private static final Function<ResolvedRequest, DependencyRequest> REQUEST_FROM_RESOLVED_REQUEST =
      new Function<ResolvedRequest, DependencyRequest>() {
        @Override public DependencyRequest apply(ResolvedRequest resolvedRequest) {
          return resolvedRequest.request();
        }
      };

  abstract static class Traverser {
    abstract boolean visitResolvedRequest(Deque<ResolvedRequest> path);
  }
}
