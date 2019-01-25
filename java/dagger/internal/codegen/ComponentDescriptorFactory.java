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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.MoreTypes.isTypeOf;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.ComponentCreatorAnnotation.creatorAnnotationsFor;
import static dagger.internal.codegen.ComponentDescriptor.isComponentContributionMethod;
import static dagger.internal.codegen.ConfigurationAnnotations.enclosedAnnotatedTypes;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentDependencies;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentModules;
import static dagger.internal.codegen.ConfigurationAnnotations.isSubcomponentCreator;
import static dagger.internal.codegen.DaggerElements.getAnnotationMirror;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;
import static dagger.internal.codegen.Scopes.productionScope;
import static dagger.internal.codegen.Scopes.scopesOf;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.VOID;
import static javax.lang.model.util.ElementFilter.methodsIn;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dagger.Lazy;
import dagger.Subcomponent;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodKind;
import dagger.model.DependencyRequest;
import dagger.model.Scope;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
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
        checkComponentKind(
            typeElement, ComponentKind::isRoot, "%s must have a component annotation"));
  }

  /** Returns a descriptor for a subcomponent type. */
  ComponentDescriptor subcomponentDescriptor(TypeElement typeElement) {
    return create(
        typeElement,
        checkComponentKind(
            typeElement, kind -> !kind.isRoot(), "%s must have a subcomponent annotation"));
  }

  /**
   * Returns a descriptor for a fictional component based on a module type in order to validate its
   * bindings.
   */
  ComponentDescriptor moduleComponentDescriptor(TypeElement typeElement) {
    return create(
        typeElement,
        checkComponentKind(
            typeElement, ComponentKind::isForModuleValidation, "%s must have a module annotation"));
  }

  private ComponentKind checkComponentKind(
      TypeElement typeElement,
      Predicate<ComponentKind> componentKindPredicate,
      String errorMessageTemplate) {
    Optional<ComponentKind> kind = ComponentKind.forAnnotatedElement(typeElement);
    checkArgument(
        kind.isPresent() && componentKindPredicate.test(kind.get()),
        errorMessageTemplate,
        typeElement);
    return kind.get();
  }

  private ComponentDescriptor create(TypeElement typeElement, ComponentKind kind) {
    AnnotationMirror componentAnnotation =
        getAnnotationMirror(typeElement, kind.annotation()).get();
    DeclaredType declaredComponentType = MoreTypes.asDeclared(typeElement.asType());
    ImmutableSet<ComponentRequirement> componentDependencies =
        kind.isRoot() && !kind.isForModuleValidation()
            ? getComponentDependencies(componentAnnotation).stream()
                .map(ComponentRequirement::forDependency)
                .collect(toImmutableSet())
            : ImmutableSet.of();

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

    ImmutableSet<TypeElement> modules =
        kind.isForModuleValidation()
            ? ImmutableSet.of(typeElement)
            : getComponentModules(componentAnnotation).stream()
                .map(MoreTypes::asTypeElement)
                .collect(toImmutableSet());

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
    if (!kind.isForModuleValidation()) {
      ImmutableSet<ExecutableElement> unimplementedMethods =
          elements.getUnimplementedMethods(typeElement);
      for (ExecutableElement componentMethod : unimplementedMethods) {
        ExecutableType resolvedMethod =
            MoreTypes.asExecutable(types.asMemberOf(declaredComponentType, componentMethod));
        ComponentMethodDescriptor componentMethodDescriptor =
            getDescriptorForComponentMethod(typeElement, kind, componentMethod);
        componentMethodsBuilder.add(componentMethodDescriptor);
        switch (componentMethodDescriptor.kind()) {
          case SUBCOMPONENT:
          case PRODUCTION_SUBCOMPONENT:
            subcomponentsByFactoryMethod.put(
                componentMethodDescriptor,
                subcomponentDescriptor(MoreTypes.asTypeElement(resolvedMethod.getReturnType())));
            break;

          case SUBCOMPONENT_BUILDER:
          case PRODUCTION_SUBCOMPONENT_BUILDER:
            subcomponentsByBuilderMethod.put(
                componentMethodDescriptor,
                subcomponentDescriptor(
                    MoreElements.asType(
                        MoreTypes.asElement(resolvedMethod.getReturnType())
                            .getEnclosingElement())));
            break;

          default: // nothing special to do for other methods.
        }
      }
    }

    // Validation should have ensured that this set will have at most one element.
    ImmutableSet<DeclaredType> enclosedCreators =
        creatorAnnotationsFor(kind).stream()
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
    if (kind.isProducer()) {
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
      ComponentKind componentKind,
      ExecutableElement componentMethod) {
    ExecutableType resolvedComponentMethod =
        MoreTypes.asExecutable(
            types.asMemberOf(MoreTypes.asDeclared(componentElement.asType()), componentMethod));
    TypeMirror returnType = resolvedComponentMethod.getReturnType();
    if (returnType.getKind().equals(DECLARED)) {
      if (isTypeOf(Provider.class, returnType) || isTypeOf(Lazy.class, returnType)) {
        return ComponentMethodDescriptor.forProvision(
            componentMethod,
            dependencyRequestFactory.forComponentProvisionMethod(
                componentMethod, resolvedComponentMethod));
      } else if (!getQualifier(componentMethod).isPresent()) {
        Element returnTypeElement = MoreTypes.asElement(returnType);
        if (ConfigurationAnnotations.isSubcomponent(returnTypeElement)) {
          return ComponentMethodDescriptor.forSubcomponent(
              isAnnotationPresent(returnTypeElement, Subcomponent.class)
                  ? ComponentMethodKind.SUBCOMPONENT
                  : ComponentMethodKind.PRODUCTION_SUBCOMPONENT,
              componentMethod);
        } else if (isSubcomponentCreator(returnTypeElement)) {
          DependencyRequest dependencyRequest =
              dependencyRequestFactory.forComponentProvisionMethod(
                  componentMethod, resolvedComponentMethod);
          return ComponentMethodDescriptor.forSubcomponentCreator(
              isAnnotationPresent(returnTypeElement, Subcomponent.Builder.class)
                  ? ComponentMethodKind.SUBCOMPONENT_BUILDER
                  : ComponentMethodKind.PRODUCTION_SUBCOMPONENT_BUILDER,
              dependencyRequest,
              componentMethod);
        }
      }
    }

    // a typical provision method
    if (componentMethod.getParameters().isEmpty()
        && !componentMethod.getReturnType().getKind().equals(VOID)) {
      switch (componentKind) {
        case COMPONENT:
        case SUBCOMPONENT:
          return ComponentMethodDescriptor.forProvision(
              componentMethod,
              dependencyRequestFactory.forComponentProvisionMethod(
                  componentMethod, resolvedComponentMethod));
        case PRODUCTION_COMPONENT:
        case PRODUCTION_SUBCOMPONENT:
          return ComponentMethodDescriptor.forProvision(
              componentMethod,
              dependencyRequestFactory.forComponentProductionMethod(
                  componentMethod, resolvedComponentMethod));
        default:
          throw new AssertionError();
      }
    }

    List<? extends TypeMirror> parameterTypes = resolvedComponentMethod.getParameterTypes();
    if (parameterTypes.size() == 1
        && (returnType.getKind().equals(VOID)
            || MoreTypes.equivalence().equivalent(returnType, parameterTypes.get(0)))) {
      return ComponentMethodDescriptor.forMembersInjection(
          componentMethod,
          dependencyRequestFactory.forComponentMembersInjectionMethod(
              componentMethod, resolvedComponentMethod));
    }

    throw new IllegalArgumentException("not a valid component method: " + componentMethod);
  }
}
