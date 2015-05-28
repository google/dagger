/*
 * Copyright (C) 2014 Google, Inc.
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
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.Component;
import dagger.Lazy;
import dagger.MembersInjector;
import dagger.Subcomponent;
import dagger.producers.ProductionComponent;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.ConfigurationAnnotations.enclosedBuilders;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentDependencies;
import static dagger.internal.codegen.ConfigurationAnnotations.isComponent;
import static dagger.internal.codegen.InjectionAnnotations.getScopeAnnotation;
import static dagger.internal.codegen.Util.unwrapOptionalEquivalence;
import static dagger.internal.codegen.Util.wrapOptionalInEquivalence;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.VOID;

/**
 * The logical representation of a {@link Component} or {@link ProductionComponent} definition.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@AutoValue
abstract class ComponentDescriptor {
  ComponentDescriptor() {}

  enum Kind {
    COMPONENT(Component.class, null),
    SUBCOMPONENT(Subcomponent.class, null),
    PRODUCTION_COMPONENT(ProductionComponent.class, null);

    private final Class<? extends Annotation> annotationType;
    private final Class<? extends Annotation> builderType;

    Kind(Class<? extends Annotation> annotationType, Class<? extends Annotation> builderType) {
      this.annotationType = annotationType;
      this.builderType = builderType;
    }

    Class<? extends Annotation> annotationType() {
      return annotationType;
    }
    
    Class<? extends Annotation> builderAnnotationType() {
      return builderType;
    }
  }

  abstract Kind kind();

  abstract AnnotationMirror componentAnnotation();

  /**
   * The type (interface or abstract class) that defines the component. This is the element to which
   * the {@link Component} annotation was applied.
   */
  abstract TypeElement componentDefinitionType();

  /**
   * The set of component dependencies listed in {@link Component#dependencies}.
   */
  abstract ImmutableSet<TypeElement> dependencies();

  /**
   * An index of the type to which this component holds a reference (the type listed in
   * {@link Component#dependencies} or {@link ProductionComponent#dependencies} as opposed to the
   * enclosing type) for each method from a component dependency that can be used for binding.
   */
  abstract ImmutableMap<ExecutableElement, TypeElement> dependencyMethodIndex();

  /**
   * The element representing {@link Executor}, if it should be a dependency of this component.
   */
  abstract Optional<TypeElement> executorDependency();

  /**
   * An optional annotation constraining the scope of this component.
   */
  Optional<AnnotationMirror> scope() {
    return unwrapOptionalEquivalence(wrappedScope());
  }

  /**
   * An optional annotation constraining the scope of this component wrapped in an
   * {@link com.google.common.base.Equivalence.Wrapper} to preserve comparison semantics of
   * {@link AnnotationMirror}.
   */
  abstract Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedScope();

  abstract ImmutableMap<ExecutableElement, ComponentDescriptor> subcomponents();

  abstract ImmutableSet<ComponentMethodDescriptor> componentMethods();

  // TODO(gak): Consider making this non-optional and revising the
  // interaction between the spec & generation
  abstract Optional<BuilderSpec> builderSpec();

  @AutoValue
  static abstract class ComponentMethodDescriptor {
    abstract ComponentMethodKind kind();
    abstract Optional<DependencyRequest> dependencyRequest();
    abstract ExecutableElement methodElement();
  }

  enum ComponentMethodKind {
    PROVISON,
    PRODUCTION,
    MEMBERS_INJECTION,
    SUBCOMPONENT,
    SUBCOMPONENT_BUILDER,
  }
  
  @AutoValue
  static abstract class BuilderSpec {    
    abstract TypeElement builderDefinitionType();
    abstract Map<TypeElement, ExecutableElement> methodMap();
    abstract ExecutableElement buildMethod();
    abstract TypeMirror componentType();
  }

  static final class Factory {
    private final Elements elements;
    private final Types types;
    private final DependencyRequest.Factory dependencyRequestFactory;

    Factory(Elements elements, Types types, DependencyRequest.Factory dependencyRequestFactory) {
      this.elements = elements;
      this.types = types;
      this.dependencyRequestFactory = dependencyRequestFactory;
    }

    ComponentDescriptor forComponent(TypeElement componentDefinitionType) {
      return create(componentDefinitionType, Kind.COMPONENT);
    }

    ComponentDescriptor forProductionComponent(TypeElement componentDefinitionType) {
      return create(componentDefinitionType, Kind.PRODUCTION_COMPONENT);
    }

    private ComponentDescriptor create(TypeElement componentDefinitionType, Kind kind) {
      DeclaredType declaredComponentType = MoreTypes.asDeclared(componentDefinitionType.asType());
      AnnotationMirror componentMirror =
          getAnnotationMirror(componentDefinitionType, kind.annotationType())
              .or(getAnnotationMirror(componentDefinitionType, Subcomponent.class))
              .get();
      ImmutableSet<TypeElement> componentDependencyTypes =
          isComponent(componentDefinitionType)
              ? MoreTypes.asTypeElements(getComponentDependencies(componentMirror))
              : ImmutableSet.<TypeElement>of();

      ImmutableMap.Builder<ExecutableElement, TypeElement> dependencyMethodIndex =
          ImmutableMap.builder();

      for (TypeElement componentDependency : componentDependencyTypes) {
        List<ExecutableElement> dependencyMethods =
            ElementFilter.methodsIn(elements.getAllMembers(componentDependency));
        for (ExecutableElement dependencyMethod : dependencyMethods) {
          if (isComponentContributionMethod(elements, dependencyMethod)) {
            dependencyMethodIndex.put(dependencyMethod, componentDependency);
          }
        }
      }

      Optional<TypeElement> executorDependency =
          kind.equals(Kind.PRODUCTION_COMPONENT)
              ? Optional.of(elements.getTypeElement(Executor.class.getCanonicalName()))
              : Optional.<TypeElement>absent();

      ImmutableSet<ExecutableElement> unimplementedMethods =
          Util.getUnimplementedMethods(elements, componentDefinitionType);

      ImmutableSet.Builder<ComponentMethodDescriptor> componentMethodsBuilder =
          ImmutableSet.builder();

      ImmutableMap.Builder<ExecutableElement, ComponentDescriptor> subcomponentDescriptors =
          ImmutableMap.builder();
      for (ExecutableElement componentMethod : unimplementedMethods) {
        ExecutableType resolvedMethod =
            MoreTypes.asExecutable(types.asMemberOf(declaredComponentType, componentMethod));
        ComponentMethodDescriptor componentMethodDescriptor =
            getDescriptorForComponentMethod(componentDefinitionType, kind, componentMethod);
        componentMethodsBuilder.add(componentMethodDescriptor);
        switch (componentMethodDescriptor.kind()) {
          case SUBCOMPONENT:
            subcomponentDescriptors.put(componentMethod,
                create(MoreElements.asType(MoreTypes.asElement(resolvedMethod.getReturnType())),
                    Kind.SUBCOMPONENT));
            break;
          case SUBCOMPONENT_BUILDER:
            subcomponentDescriptors.put(componentMethod, create(MoreElements.asType(
                MoreTypes.asElement(resolvedMethod.getReturnType()).getEnclosingElement()),
                    Kind.SUBCOMPONENT));
            break;
          default: // nothing special to do for other methods.
        }
        
      }
      
      ImmutableList<DeclaredType> enclosedBuilders = kind.builderAnnotationType() == null
          ? ImmutableList.<DeclaredType>of()
          : enclosedBuilders(componentDefinitionType, kind.builderAnnotationType());
      Optional<DeclaredType> builderType =
          Optional.fromNullable(getOnlyElement(enclosedBuilders, null));        

      Optional<AnnotationMirror> scope = getScopeAnnotation(componentDefinitionType);
      return new AutoValue_ComponentDescriptor(
          kind,
          componentMirror,
          componentDefinitionType,
          componentDependencyTypes,
          dependencyMethodIndex.build(),
          executorDependency,
          wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), scope),
          subcomponentDescriptors.build(),
          componentMethodsBuilder.build(),
          createBuilderSpec(builderType));
    }

    private ComponentMethodDescriptor getDescriptorForComponentMethod(TypeElement componentElement,
        Kind componentKind,
        ExecutableElement componentMethod) {
      ExecutableType resolvedComponentMethod = MoreTypes.asExecutable(types.asMemberOf(
          MoreTypes.asDeclared(componentElement.asType()), componentMethod));
      TypeMirror returnType = resolvedComponentMethod.getReturnType();
      if (returnType.getKind().equals(DECLARED)) {
        if (MoreTypes.isTypeOf(Provider.class, returnType)
            || MoreTypes.isTypeOf(Lazy.class, returnType)) {
          return new AutoValue_ComponentDescriptor_ComponentMethodDescriptor(
              ComponentMethodKind.PROVISON,
              Optional.of(dependencyRequestFactory.forComponentProvisionMethod(componentMethod,
                  resolvedComponentMethod)),
              componentMethod);
        } else if (MoreTypes.isTypeOf(MembersInjector.class, returnType)) {
          return new AutoValue_ComponentDescriptor_ComponentMethodDescriptor(
              ComponentMethodKind.MEMBERS_INJECTION,
              Optional.of(dependencyRequestFactory.forComponentMembersInjectionMethod(
                  componentMethod,
                  resolvedComponentMethod)),
              componentMethod);
        } else if (isAnnotationPresent(MoreTypes.asElement(returnType), Subcomponent.class)) {
          return new AutoValue_ComponentDescriptor_ComponentMethodDescriptor(
              ComponentMethodKind.SUBCOMPONENT,
              Optional.<DependencyRequest>absent(),
              componentMethod);
        }
      }

      // a typical provision method
      if (componentMethod.getParameters().isEmpty()
          && !componentMethod.getReturnType().getKind().equals(VOID)) {
        switch (componentKind) {
          case COMPONENT:
          case SUBCOMPONENT:
            return new AutoValue_ComponentDescriptor_ComponentMethodDescriptor(
                ComponentMethodKind.PROVISON,
                Optional.of(dependencyRequestFactory.forComponentProvisionMethod(componentMethod,
                    resolvedComponentMethod)),
                componentMethod);
          case PRODUCTION_COMPONENT:
            return new AutoValue_ComponentDescriptor_ComponentMethodDescriptor(
                ComponentMethodKind.PRODUCTION,
                Optional.of(dependencyRequestFactory.forComponentProductionMethod(componentMethod,
                    resolvedComponentMethod)),
                componentMethod);
          default:
            throw new AssertionError();
        }
      }

      List<? extends TypeMirror> parameterTypes = resolvedComponentMethod.getParameterTypes();
      if (parameterTypes.size() == 1
          && (returnType.getKind().equals(VOID)
              || MoreTypes.equivalence().equivalent(returnType, parameterTypes.get(0)))) {
        return new AutoValue_ComponentDescriptor_ComponentMethodDescriptor(
            ComponentMethodKind.MEMBERS_INJECTION,
            Optional.of(dependencyRequestFactory.forComponentMembersInjectionMethod(
                componentMethod,
                resolvedComponentMethod)),
            componentMethod);
      }

      throw new IllegalArgumentException("not a valid component method: " + componentMethod);
    }

    private Optional<BuilderSpec> createBuilderSpec(Optional<DeclaredType> builderType) {
      if (!builderType.isPresent()) {
        return Optional.absent();
      }
      TypeElement element = MoreTypes.asTypeElement(builderType.get());
      ImmutableSet<ExecutableElement> methods = Util.getUnimplementedMethods(elements, element);
      ImmutableMap.Builder<TypeElement, ExecutableElement> map = ImmutableMap.builder();
      ExecutableElement buildMethod = null;
      for (ExecutableElement method : methods) {
        if (method.getParameters().isEmpty()) {
          buildMethod = method;
        } else {
          ExecutableType resolved =
              MoreTypes.asExecutable(types.asMemberOf(builderType.get(), method));
          map.put(MoreTypes.asTypeElement(getOnlyElement(resolved.getParameterTypes())), method);
        }
      }
      verify(buildMethod != null); // validation should have ensured this.
      return Optional.<BuilderSpec>of(new AutoValue_ComponentDescriptor_BuilderSpec(element,
          map.build(), buildMethod, element.getEnclosingElement().asType()));
    }
  }

  static boolean isComponentContributionMethod(Elements elements, ExecutableElement method) {
    return method.getParameters().isEmpty()
        && !method.getReturnType().getKind().equals(VOID)
        && !elements.getTypeElement(Object.class.getCanonicalName())
            .equals(method.getEnclosingElement());
  }

  static boolean isComponentProductionMethod(Elements elements, ExecutableElement method) {
    return isComponentContributionMethod(elements, method)
        && MoreTypes.isTypeOf(ListenableFuture.class, method.getReturnType());
  }
}
