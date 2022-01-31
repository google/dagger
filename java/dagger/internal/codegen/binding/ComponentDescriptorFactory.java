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

package dagger.internal.codegen.binding;

import static androidx.room.compiler.processing.XTypeKt.isVoid;
import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static androidx.room.compiler.processing.compat.XConverters.toXProcessing;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.base.ComponentAnnotation.rootComponentAnnotation;
import static dagger.internal.codegen.base.ComponentAnnotation.subcomponentAnnotation;
import static dagger.internal.codegen.base.ModuleAnnotation.moduleAnnotation;
import static dagger.internal.codegen.base.Scopes.productionScope;
import static dagger.internal.codegen.base.Scopes.scopesOf;
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.creatorAnnotationsFor;
import static dagger.internal.codegen.binding.ComponentDescriptor.isComponentContributionMethod;
import static dagger.internal.codegen.binding.ConfigurationAnnotations.enclosedAnnotatedTypes;
import static dagger.internal.codegen.binding.ConfigurationAnnotations.isSubcomponentCreator;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XTypeElements.getAllUnimplementedMethods;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static javax.lang.model.util.ElementFilter.methodsIn;

import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.base.ComponentAnnotation;
import dagger.internal.codegen.base.ModuleAnnotation;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.spi.model.Scope;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;

/** A factory for {@link ComponentDescriptor}s. */
public final class ComponentDescriptorFactory {
  private final XProcessingEnv processingEnv;
  private final DaggerElements elements;
  private final DaggerTypes types;
  private final DependencyRequestFactory dependencyRequestFactory;
  private final ModuleDescriptor.Factory moduleDescriptorFactory;
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  ComponentDescriptorFactory(
      XProcessingEnv processingEnv,
      DaggerElements elements,
      DaggerTypes types,
      DependencyRequestFactory dependencyRequestFactory,
      ModuleDescriptor.Factory moduleDescriptorFactory,
      InjectionAnnotations injectionAnnotations) {
    this.processingEnv = processingEnv;
    this.elements = elements;
    this.types = types;
    this.dependencyRequestFactory = dependencyRequestFactory;
    this.moduleDescriptorFactory = moduleDescriptorFactory;
    this.injectionAnnotations = injectionAnnotations;
  }

  /** Returns a descriptor for a root component type. */
  public ComponentDescriptor rootComponentDescriptor(XTypeElement typeElement) {
    Optional<ComponentAnnotation> annotation = rootComponentAnnotation(typeElement);
    checkArgument(annotation.isPresent(), "%s must have a component annotation", typeElement);
    return create(typeElement, annotation.get());
  }

  /** Returns a descriptor for a subcomponent type. */
  public ComponentDescriptor subcomponentDescriptor(XTypeElement typeElement) {
    Optional<ComponentAnnotation> annotation = subcomponentAnnotation(typeElement);
    checkArgument(annotation.isPresent(), "%s must have a subcomponent annotation", typeElement);
    return create(typeElement, annotation.get());
  }

  /**
   * Returns a descriptor for a fictional component based on a module type in order to validate its
   * bindings.
   */
  public ComponentDescriptor moduleComponentDescriptor(XTypeElement typeElement) {
    Optional<ModuleAnnotation> annotation = moduleAnnotation(typeElement);
    checkArgument(annotation.isPresent(), "%s must have a module annotation", typeElement);
    return create(typeElement, ComponentAnnotation.fromModuleAnnotation(annotation.get()));
  }

  private ComponentDescriptor create(
      XTypeElement typeElement, ComponentAnnotation componentAnnotation) {
    ImmutableSet<ComponentRequirement> componentDependencies =
        componentAnnotation.dependencyTypes().stream()
            .map(ComponentRequirement::forDependency)
            .collect(toImmutableSet());

    ImmutableMap.Builder<XMethodElement, ComponentRequirement> dependenciesByDependencyMethod =
        ImmutableMap.builder();
    for (ComponentRequirement componentDependency : componentDependencies) {
      for (ExecutableElement dependencyMethod :
          methodsIn(elements.getAllMembers(toJavac(componentDependency.typeElement())))) {
        if (isComponentContributionMethod(dependencyMethod)) {
          dependenciesByDependencyMethod.put(
              (XMethodElement) toXProcessing(dependencyMethod, processingEnv), componentDependency);
        }
      }
    }

    // Start with the component's modules. For fictional components built from a module, start with
    // that module.
    ImmutableSet<XTypeElement> modules =
        componentAnnotation.isRealComponent()
            ? componentAnnotation.modules()
            : ImmutableSet.of(typeElement);

    ImmutableSet<ModuleDescriptor> transitiveModules =
        moduleDescriptorFactory.transitiveModules(modules);

    ImmutableSet<ComponentDescriptor> subcomponentsFromModules =
        transitiveModules.stream()
            .flatMap(transitiveModule -> transitiveModule.subcomponentDeclarations().stream())
            .map(SubcomponentDeclaration::subcomponentType)
            .map(this::subcomponentDescriptor)
            .collect(toImmutableSet());

    ImmutableSet.Builder<ComponentMethodDescriptor> componentMethodsBuilder =
        ImmutableSet.builder();
    ImmutableBiMap.Builder<ComponentMethodDescriptor, ComponentDescriptor>
        subcomponentsByFactoryMethod = ImmutableBiMap.builder();
    ImmutableBiMap.Builder<ComponentMethodDescriptor, ComponentDescriptor>
        subcomponentsByBuilderMethod = ImmutableBiMap.builder();
    if (componentAnnotation.isRealComponent()) {
      for (XMethodElement componentMethod : getAllUnimplementedMethods(typeElement)) {
        ComponentMethodDescriptor componentMethodDescriptor =
            getDescriptorForComponentMethod(componentAnnotation, typeElement, componentMethod);
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
    ImmutableSet<XTypeElement> enclosedCreators =
        enclosedAnnotatedTypes(typeElement, creatorAnnotationsFor(componentAnnotation));
    Optional<ComponentCreatorDescriptor> creatorDescriptor =
        enclosedCreators.isEmpty()
            ? Optional.empty()
            : Optional.of(
                ComponentCreatorDescriptor.create(
                    getOnlyElement(enclosedCreators), types, dependencyRequestFactory));

    ImmutableSet<Scope> scopes = scopesOf(typeElement);
    if (componentAnnotation.isProduction()) {
      scopes =
          ImmutableSet.<Scope>builder().addAll(scopes).add(productionScope(processingEnv)).build();
    }

    return ComponentDescriptor.create(
        componentAnnotation,
        typeElement,
        componentDependencies,
        transitiveModules,
        dependenciesByDependencyMethod.buildOrThrow(),
        scopes,
        subcomponentsFromModules,
        subcomponentsByFactoryMethod.build(),
        subcomponentsByBuilderMethod.build(),
        componentMethodsBuilder.build(),
        creatorDescriptor);
  }

  private ComponentMethodDescriptor getDescriptorForComponentMethod(
      ComponentAnnotation componentAnnotation,
      XTypeElement componentElement,
      XMethodElement componentMethod) {
    ComponentMethodDescriptor.Builder descriptor =
        ComponentMethodDescriptor.builder(componentMethod);

    XMethodType resolvedComponentMethod = componentMethod.asMemberOf(componentElement.getType());
    XType returnType = resolvedComponentMethod.getReturnType();
    if (isDeclared(returnType) && !injectionAnnotations.getQualifier(componentMethod).isPresent()) {
      XTypeElement returnTypeElement = returnType.getTypeElement();
      if (subcomponentAnnotation(returnTypeElement).isPresent()) {
        // It's a subcomponent factory method. There is no dependency request, and there could be
        // any number of parameters. Just return the descriptor.
        return descriptor.subcomponent(subcomponentDescriptor(returnTypeElement)).build();
      }
      if (isSubcomponentCreator(returnTypeElement)) {
        descriptor.subcomponent(
            subcomponentDescriptor(returnTypeElement.getEnclosingTypeElement()));
      }
    }

    switch (componentMethod.getParameters().size()) {
      case 0:
        checkArgument(!isVoid(returnType), "component method cannot be void: %s", componentMethod);
        descriptor.dependencyRequest(
            componentAnnotation.isProduction()
                ? dependencyRequestFactory.forComponentProductionMethod(
                    componentMethod, resolvedComponentMethod)
                : dependencyRequestFactory.forComponentProvisionMethod(
                    componentMethod, resolvedComponentMethod));
        break;

      case 1:
        checkArgument(
            isVoid(returnType)
                // TODO(bcorso): Replace this with isSameType()?
                || returnType
                    .getTypeName()
                    .equals(resolvedComponentMethod.getParameterTypes().get(0).getTypeName()),
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
