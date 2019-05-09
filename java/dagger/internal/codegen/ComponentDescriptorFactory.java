/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static com.google.auto.common.MoreElements.asType;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.ComponentAnnotation.subcomponentAnnotation;
import static dagger.internal.codegen.ComponentCreatorAnnotation.creatorAnnotationsFor;
import static dagger.internal.codegen.ComponentDescriptor.isComponentContributionMethod;
import static dagger.internal.codegen.ConfigurationAnnotations.enclosedAnnotatedTypes;
import static dagger.internal.codegen.ConfigurationAnnotations.isSubcomponentCreator;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;
import static dagger.internal.codegen.Scopes.productionScope;
import static dagger.internal.codegen.Scopes.scopesOf;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.VOID;
import static javax.lang.model.util.ElementFilter.methodsIn;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.Scope;
import java.util.Optional;
import java.util.function.Function;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

/** A factory for {@link ComponentDescriptor}s. */
final class ComponentDescriptorFactory {
  private final DaggerElements elements;
  private final DaggerTypes types;
  private final DependencyRequestFactory dependencyRequestFactory;
  private final ModuleDescriptor.Factory moduleDescriptorFactory;

  @Inject
  ComponentDescriptorFactory(
      DaggerElements elements,
      DaggerTypes types,
      DependencyRequestFactory dependencyRequestFactory,
      ModuleDescriptor.Factory moduleDescriptorFactory) {
    this.elements = elements;
    this.types = types;
    this.dependencyRequestFactory = dependencyRequestFactory;
    this.moduleDescriptorFactory = moduleDescriptorFactory;
  }

  /** Returns a descriptor for a root component type. */
  ComponentDescriptor rootComponentDescriptor(TypeElement typeElement) {
    return create(
        typeElement,
        checkAnnotation(
            typeElement,
            ComponentAnnotation::rootComponentAnnotation,
            "must have a component annotation"));
  }

  /** Returns a descriptor for a subcomponent type. */
  ComponentDescriptor subcomponentDescriptor(TypeElement typeElement) {
    return create(
        typeElement,
        checkAnnotation(
            typeElement,
            ComponentAnnotation::subcomponentAnnotation,
            "must have a subcomponent annotation"));
  }

  /**
   * Returns a descriptor for a fictional component based on a module type in order to validate its
   * bindings.
   */
  ComponentDescriptor moduleComponentDescriptor(TypeElement typeElement) {
    return create(
        typeElement,
        ComponentAnnotation.fromModuleAnnotation(
            checkAnnotation(
                typeElement, ModuleAnnotation::moduleAnnotation, "must have a module annotation")));
  }

  private static <A> A checkAnnotation(
      TypeElement typeElement,
      Function<TypeElement, Optional<A>> annotationFunction,
      String message) {
    return annotationFunction
        .apply(typeElement)
        .orElseThrow(() -> new IllegalArgumentException(typeElement + " " + message));
  }

  private ComponentDescriptor create(
      TypeElement typeElement, ComponentAnnotation componentAnnotation) {
    ImmutableSet<ComponentRequirement> componentDependencies =
        componentAnnotation.dependencyTypes().stream()
            .map(ComponentRequirement::forDependency)
            .collect(toImmutableSet());

    ImmutableMap.Builder<ExecutableElement, ComponentRequirement> dependenciesByDependencyMethod =
        ImmutableMap.builder();

    for (ComponentRequirement componentDependency : componentDependencies) {
      for (ExecutableElement dependencyMethod :
          methodsIn(elements.getAllMembers(componentDependency.typeElement()))) {
        if (isComponentContributionMethod(elements, dependencyMethod)) {
          dependenciesByDependencyMethod.put(dependencyMethod, componentDependency);
        }
      }
    }

    // Start with the component's modules. For fictional components built from a module, start with
    // that module.
    ImmutableSet<TypeElement> modules =
        componentAnnotation.isRealComponent()
            ? componentAnnotation.modules()
            : ImmutableSet.of(typeElement);

    ImmutableSet<ModuleDescriptor> transitiveModules =
        moduleDescriptorFactory.transitiveModules(modules);

    ImmutableSet.Builder<ComponentDescriptor> subcomponentsFromModules = ImmutableSet.builder();
    for (ModuleDescriptor module : transitiveModules) {
      for (SubcomponentDeclaration subcomponentDeclaration : module.subcomponentDeclarations()) {
        TypeElement subcomponent = subcomponentDeclaration.subcomponentType();
        subcomponentsFromModules.add(subcomponentDescriptor(subcomponent));
      }
    }

    ImmutableSet.Builder<ComponentMethodDescriptor> componentMethodsBuilder =
        ImmutableSet.builder();
    ImmutableBiMap.Builder<ComponentMethodDescriptor, ComponentDescriptor>
        subcomponentsByFactoryMethod = ImmutableBiMap.builder();
    ImmutableBiMap.Builder<ComponentMethodDescriptor, ComponentDescriptor>
        subcomponentsByBuilderMethod = ImmutableBiMap.builder();
    if (componentAnnotation.isRealComponent()) {
      ImmutableSet<ExecutableElement> unimplementedMethods =
          elements.getUnimplementedMethods(typeElement);
      for (ExecutableElement componentMethod : unimplementedMethods) {
        ComponentMethodDescriptor componentMethodDescriptor =
            getDescriptorForComponentMethod(typeElement, componentAnnotation, componentMethod);
        componentMethodsBuilder.add(componentMethodDescriptor);
        componentMethodDescriptor
            .subcomponent()
            .ifPresent(
                subcomponent -> {
                  // If the dependency request is present, that means the method returns the
                  // subcomponent factory.
                  if (componentMethodDescriptor.dependencyRequest().isPresent()) {
                    subcomponentsByBuilderMethod.put(componentMethodDescriptor, subcomponent);
                  } else {
                    subcomponentsByFactoryMethod.put(componentMethodDescriptor, subcomponent);
                  }
                });
      }
    }

    // Validation should have ensured that this set will have at most one element.
    ImmutableSet<DeclaredType> enclosedCreators =
        creatorAnnotationsFor(componentAnnotation).stream()
            .flatMap(
                creatorAnnotation ->
                    enclosedAnnotatedTypes(typeElement, creatorAnnotation).stream())
            .collect(toImmutableSet());
    Optional<ComponentCreatorDescriptor> creatorDescriptor =
        enclosedCreators.isEmpty()
            ? Optional.empty()
            : Optional.of(
                ComponentCreatorDescriptor.create(
                    getOnlyElement(enclosedCreators), elements, types, dependencyRequestFactory));

    ImmutableSet<Scope> scopes = scopesOf(typeElement);
    if (componentAnnotation.isProduction()) {
      scopes = ImmutableSet.<Scope>builder().addAll(scopes).add(productionScope(elements)).build();
    }

    return new AutoValue_ComponentDescriptor(
        componentAnnotation,
        typeElement,
        componentDependencies,
        transitiveModules,
        dependenciesByDependencyMethod.build(),
        scopes,
        subcomponentsFromModules.build(),
        subcomponentsByFactoryMethod.build(),
        subcomponentsByBuilderMethod.build(),
        componentMethodsBuilder.build(),
        creatorDescriptor);
  }

  private ComponentMethodDescriptor getDescriptorForComponentMethod(
      TypeElement componentElement,
      ComponentAnnotation componentAnnotation,
      ExecutableElement componentMethod) {
    ComponentMethodDescriptor.Builder descriptor =
        ComponentMethodDescriptor.builder(componentMethod);

    ExecutableType resolvedComponentMethod =
        MoreTypes.asExecutable(
            types.asMemberOf(MoreTypes.asDeclared(componentElement.asType()), componentMethod));
    TypeMirror returnType = resolvedComponentMethod.getReturnType();
    if (returnType.getKind().equals(DECLARED) && !getQualifier(componentMethod).isPresent()) {
      TypeElement returnTypeElement = asTypeElement(returnType);
      if (subcomponentAnnotation(returnTypeElement).isPresent()) {
        // It's a subcomponent factory method. There is no dependency request, and there could be
        // any number of parameters. Just return the descriptor.
        return descriptor.subcomponent(subcomponentDescriptor(returnTypeElement)).build();
      }
      if (isSubcomponentCreator(returnTypeElement)) {
        descriptor.subcomponent(
            subcomponentDescriptor(asType(returnTypeElement.getEnclosingElement())));
      }
    }

    switch (componentMethod.getParameters().size()) {
      case 0:
        checkArgument(
            !returnType.getKind().equals(VOID),
            "component method cannot be void: %s",
            componentMethod);
        descriptor.dependencyRequest(
            componentAnnotation.isProduction()
                ? dependencyRequestFactory.forComponentProductionMethod(
                    componentMethod, resolvedComponentMethod)
                : dependencyRequestFactory.forComponentProvisionMethod(
                    componentMethod, resolvedComponentMethod));
        break;

      case 1:
        checkArgument(
            returnType.getKind().equals(VOID)
                || MoreTypes.equivalence()
                    .equivalent(returnType, resolvedComponentMethod.getParameterTypes().get(0)),
            "members injection method must return void or parameter type: %s",
            componentMethod);
        descriptor.dependencyRequest(
            dependencyRequestFactory.forComponentMembersInjectionMethod(
                componentMethod, resolvedComponentMethod));
        break;

      default:
        throw new IllegalArgumentException(
            "component method has too many parameters: " + componentMethod);
    }

    return descriptor.build();
  }
}
