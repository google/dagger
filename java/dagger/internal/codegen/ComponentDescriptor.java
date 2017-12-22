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
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.ConfigurationAnnotations.enclosedBuilders;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentDependencies;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentModules;
import static dagger.internal.codegen.ConfigurationAnnotations.isSubcomponent;
import static dagger.internal.codegen.ConfigurationAnnotations.isSubcomponentBuilder;
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
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeTraverser;
import com.google.common.util.concurrent.ListenableFuture;
import com.squareup.javapoet.ClassName;
import dagger.BindsInstance;
import dagger.Component;
import dagger.Lazy;
import dagger.Module;
import dagger.Subcomponent;
import dagger.model.Scope;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
import dagger.releasablereferences.CanReleaseReferences;
import java.lang.annotation.Annotation;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * The logical representation of a {@link Component} or {@link ProductionComponent} definition.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@AutoValue
abstract class ComponentDescriptor {
  enum Kind {
    COMPONENT(Component.class, Component.Builder.class, true),
    SUBCOMPONENT(Subcomponent.class, Subcomponent.Builder.class, false),
    PRODUCTION_COMPONENT(ProductionComponent.class, ProductionComponent.Builder.class, true),
    PRODUCTION_SUBCOMPONENT(
        ProductionSubcomponent.class, ProductionSubcomponent.Builder.class, false);

    private final Class<? extends Annotation> annotationType;
    private final Class<? extends Annotation> builderType;
    private final boolean isTopLevel;

    /**
     * Returns the kind of an annotated element if it is annotated with one of the
     * {@linkplain #annotationType() annotation types}.
     *
     * @throws IllegalArgumentException if the element is annotated with more than one of the
     *     annotation types
     */
    static Optional<Kind> forAnnotatedElement(TypeElement element) {
      Set<Kind> kinds = EnumSet.noneOf(Kind.class);
      for (Kind kind : values()) {
        if (isAnnotationPresent(element, kind.annotationType())) {
          kinds.add(kind);
        }
      }
      checkArgument(
          kinds.size() <= 1, "%s cannot be annotated with more than one of %s", element, kinds);
      return Optional.ofNullable(getOnlyElement(kinds, null));
    }

    /**
     * Returns the kind of an annotated element if it is annotated with one of the
     * {@linkplain #builderAnnotationType() annotation types}.
     *
     * @throws IllegalArgumentException if the element is annotated with more than one of the
     *     annotation types
     */
    static Optional<Kind> forAnnotatedBuilderElement(TypeElement element) {
      Set<Kind> kinds = EnumSet.noneOf(Kind.class);
      for (Kind kind : values()) {
        if (isAnnotationPresent(element, kind.builderAnnotationType())) {
          kinds.add(kind);
        }
      }
      checkArgument(
          kinds.size() <= 1, "%s cannot be annotated with more than one of %s", element, kinds);
      return Optional.ofNullable(getOnlyElement(kinds, null));
    }

    Kind(
        Class<? extends Annotation> annotationType,
        Class<? extends Annotation> builderType,
        boolean isTopLevel) {
      this.annotationType = annotationType;
      this.builderType = builderType;
      this.isTopLevel = isTopLevel;
    }

    Class<? extends Annotation> annotationType() {
      return annotationType;
    }

    Class<? extends Annotation> builderAnnotationType() {
      return builderType;
    }

    ImmutableSet<ModuleDescriptor.Kind> moduleKinds() {
      switch (this) {
        case COMPONENT:
        case SUBCOMPONENT:
          return Sets.immutableEnumSet(ModuleDescriptor.Kind.MODULE);
        case PRODUCTION_COMPONENT:
        case PRODUCTION_SUBCOMPONENT:
          return Sets.immutableEnumSet(
              ModuleDescriptor.Kind.MODULE, ModuleDescriptor.Kind.PRODUCER_MODULE);
        default:
          throw new AssertionError(this);
      }
    }

    ImmutableSet<Kind> subcomponentKinds() {
      switch (this) {
        case COMPONENT:
        case SUBCOMPONENT:
          return ImmutableSet.of(SUBCOMPONENT, PRODUCTION_SUBCOMPONENT);
        case PRODUCTION_COMPONENT:
        case PRODUCTION_SUBCOMPONENT:
          return ImmutableSet.of(PRODUCTION_SUBCOMPONENT);
        default:
          throw new AssertionError();
      }
    }

    boolean isTopLevel() {
      return isTopLevel;
    }

    boolean isProducer() {
      switch (this) {
        case COMPONENT:
        case SUBCOMPONENT:
          return false;
        case PRODUCTION_COMPONENT:
        case PRODUCTION_SUBCOMPONENT:
          return true;
        default:
          throw new AssertionError();
      }
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
   * The set of component dependencies listed in {@link Component#dependencies} or {@link
   * ProductionComponent#dependencies()}.
   */
  abstract ImmutableSet<ComponentRequirement> dependencies();

  /**
   * The set of {@link ModuleDescriptor modules} declared directly in {@link Component#modules}.
   * Use {@link #transitiveModules} to get the full set of modules available upon traversing
   * {@link Module#includes}.
   */
  abstract ImmutableSet<ModuleDescriptor> modules();

  /**
   * Returns the set of {@link ModuleDescriptor modules} declared in {@link Component#modules} and
   * those reachable by traversing {@link Module#includes}.
   *
   * <p>Note that for subcomponents this <em>will not</em> include descriptors for any modules that
   * are declared in parent components.
   */
  abstract ImmutableSet<ModuleDescriptor> transitiveModules();

  ImmutableSet<TypeElement> transitiveModuleTypes() {
    return FluentIterable.from(transitiveModules())
        .transform(ModuleDescriptor::moduleElement)
        .toSet();
  }

  private static ImmutableSet<ModuleDescriptor> transitiveModules(
      Iterable<ModuleDescriptor> topLevelModules) {
    Set<ModuleDescriptor> transitiveModules = new LinkedHashSet<>();
    for (ModuleDescriptor module : topLevelModules) {
      addTransitiveModules(transitiveModules, module);
    }
    return ImmutableSet.copyOf(transitiveModules);
  }

  private static void addTransitiveModules(
      Set<ModuleDescriptor> transitiveModules, ModuleDescriptor module) {
    if (transitiveModules.add(module)) {
      for (ModuleDescriptor includedModule : module.includedModules()) {
        addTransitiveModules(transitiveModules, includedModule);
      }
    }
  }

  /**
   * This component's {@linkplain #dependencies() dependencies} keyed by each provision or
   * production method implemented by that dependency. Note that the dependencies' types are not
   * simply the enclosing type of the method; a method may be declared by a supertype of the actual
   * dependency.
   */
  abstract ImmutableMap<ExecutableElement, ComponentRequirement> dependenciesByDependencyMethod();

  /**
   * The scopes of the component.
   */
  abstract ImmutableSet<Scope> scopes();

  /**
   * All {@link Subcomponent}s which are direct children of this component. This includes
   * subcomponents installed from {@link Module#subcomponents()} as well as subcomponent {@linkplain
   * #subcomponentsByFactoryMethod() factory methods} and {@linkplain
   * #subcomponentsByBuilderMethod() builder methods}.
   */
  ImmutableSet<ComponentDescriptor> subcomponents() {
    return ImmutableSet.<ComponentDescriptor>builder()
        .addAll(subcomponentsByFactoryMethod().values())
        .addAll(subcomponentsByBuilderMethod().values())
        .addAll(subcomponentsFromModules())
        .build();
  }

  /**
   * All {@linkplain Subcomponent direct child} components that are declared by a {@linkplain
   * Module#subcomponents() module's subcomponents}.
   */
  abstract ImmutableSet<ComponentDescriptor> subcomponentsFromModules();

  /**
   * All {@linkplain Subcomponent direct child} components that are declared by a subcomponent
   * factory method.
   */
  abstract ImmutableBiMap<ComponentMethodDescriptor, ComponentDescriptor>
      subcomponentsByFactoryMethod();

  /**
   * All {@linkplain Subcomponent direct child} components that are declared by a subcomponent
   * builder method.
   */
  abstract ImmutableBiMap<ComponentMethodDescriptor, ComponentDescriptor>
      subcomponentsByBuilderMethod();

  /**
   * All {@linkplain Subcomponent direct child} components that are declared by an entry point
   * method. This is equivalent to the set of values from {@link #subcomponentsByFactoryMethod()}
   * and {@link #subcomponentsByBuilderMethod()}.
   */
  ImmutableSet<ComponentDescriptor> subcomponentsFromEntryPoints() {
    return ImmutableSet.<ComponentDescriptor>builder()
        .addAll(subcomponentsByFactoryMethod().values())
        .addAll(subcomponentsByBuilderMethod().values())
        .build();
  }

  @Memoized
  ImmutableBiMap<TypeElement, ComponentDescriptor> subcomponentsByBuilderType() {
    ImmutableBiMap.Builder<TypeElement, ComponentDescriptor> subcomponentsByBuilderType =
        ImmutableBiMap.builder();
    for (ComponentDescriptor subcomponent : subcomponents()) {
      if (subcomponent.builderSpec().isPresent()) {
        subcomponentsByBuilderType.put(
            subcomponent.builderSpec().get().builderDefinitionType(), subcomponent);
      }
    }
    return subcomponentsByBuilderType.build();
  }

  abstract ImmutableSet<ComponentMethodDescriptor> componentMethods();

  /** The dependency requests defined by methods on the component type. */
  ImmutableSet<DependencyRequest> entryPoints() {
    return componentMethods()
        .stream()
        .map(ComponentMethodDescriptor::dependencyRequest)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(toImmutableSet());
  }

  // TODO(gak): Consider making this non-optional and revising the
  // interaction between the spec & generation
  abstract Optional<BuilderSpec> builderSpec();

  /**
   * For {@link Component @Component}s, all {@link CanReleaseReferences @CanReleaseReferences}
   * scopes associated with this component or any subcomponent. Otherwise empty.
   */
  ImmutableSet<Scope> releasableReferencesScopes() {
    return kind().equals(Kind.COMPONENT)
        ? SUBCOMPONENT_TRAVERSER
            .breadthFirstTraversal(this)
            .transformAndConcat(ComponentDescriptor::scopes)
            .filter(Scope::canReleaseReferences)
            .toSet()
        : ImmutableSet.<Scope>of();
  }

  /** {@link TreeTraverser} for the subcomponent tree. */
  private static final TreeTraverser<ComponentDescriptor> SUBCOMPONENT_TRAVERSER =
      new TreeTraverser<ComponentDescriptor>() {
        @Override
        public Iterable<ComponentDescriptor> children(ComponentDescriptor node) {
          return node.subcomponents();
        }
      };

  /** A function that returns all {@link #scopes()} of its input. */
  @AutoValue
  abstract static class ComponentMethodDescriptor {
    abstract ComponentMethodKind kind();
    abstract Optional<DependencyRequest> dependencyRequest();
    abstract ExecutableElement methodElement();

    static ComponentMethodDescriptor create(
        ComponentMethodKind kind,
        Optional<DependencyRequest> dependencyRequest,
        ExecutableElement methodElement) {
      return new AutoValue_ComponentDescriptor_ComponentMethodDescriptor(
          kind, dependencyRequest, methodElement);
    }

    static ComponentMethodDescriptor forProvision(
        ExecutableElement methodElement, DependencyRequest dependencyRequest) {
      return create(ComponentMethodKind.PROVISION, Optional.of(dependencyRequest), methodElement);
    }

    static ComponentMethodDescriptor forMembersInjection(
        ExecutableElement methodElement, DependencyRequest dependencyRequest) {
      return create(
          ComponentMethodKind.MEMBERS_INJECTION, Optional.of(dependencyRequest), methodElement);
    }

    static ComponentMethodDescriptor forSubcomponent(
        ComponentMethodKind kind, ExecutableElement methodElement) {
      return create(kind, Optional.empty(), methodElement);
    }

    static ComponentMethodDescriptor forSubcomponentBuilder(
        ComponentMethodKind kind,
        DependencyRequest dependencyRequestForBuilder,
        ExecutableElement methodElement) {
      return create(kind, Optional.of(dependencyRequestForBuilder), methodElement);
    }
  }

  enum ComponentMethodKind {
    PROVISION,
    PRODUCTION,
    MEMBERS_INJECTION,
    SUBCOMPONENT,
    SUBCOMPONENT_BUILDER,
    PRODUCTION_SUBCOMPONENT,
    PRODUCTION_SUBCOMPONENT_BUILDER;

    boolean isSubcomponentKind() {
      return this == SUBCOMPONENT || this == PRODUCTION_SUBCOMPONENT;
    }

    /**
     * Returns the component kind associated with this component method, if it exists. Otherwise,
     * throws.
     */
    Kind componentKind() {
      switch (this) {
        case SUBCOMPONENT:
        case SUBCOMPONENT_BUILDER:
          return Kind.SUBCOMPONENT;
        case PRODUCTION_SUBCOMPONENT:
        case PRODUCTION_SUBCOMPONENT_BUILDER:
          return Kind.PRODUCTION_SUBCOMPONENT;
        default:
          throw new IllegalStateException("no component associated with method " + this);
      }
    }
  }

  @AutoValue
  abstract static class BuilderRequirementMethod {
    abstract ExecutableElement method();

    abstract ComponentRequirement requirement();
  }

  @AutoValue
  abstract static class BuilderSpec {
    abstract TypeElement builderDefinitionType();
    abstract ImmutableSet<BuilderRequirementMethod> requirementMethods();
    abstract ExecutableElement buildMethod();
    abstract TypeMirror componentType();
  }

  static final class Factory {
    private final DaggerElements elements;
    private final Types types;
    private final DependencyRequestFactory dependencyRequestFactory;
    private final ModuleDescriptor.Factory moduleDescriptorFactory;

    Factory(
        DaggerElements elements,
        Types types,
        DependencyRequestFactory dependencyRequestFactory,
        ModuleDescriptor.Factory moduleDescriptorFactory) {
      this.elements = elements;
      this.types = types;
      this.dependencyRequestFactory = dependencyRequestFactory;
      this.moduleDescriptorFactory = moduleDescriptorFactory;
    }

    /**
     * Returns a component descriptor for a type annotated with either {@link Component @Component}
     * or {@link ProductionComponent @ProductionComponent}.
     */
    ComponentDescriptor forComponent(TypeElement componentDefinitionType) {
      Optional<Kind> kind = Kind.forAnnotatedElement(componentDefinitionType);
      checkArgument(
          kind.isPresent() && kind.get().isTopLevel(),
          "%s must be annotated with @Component or @ProductionComponent",
          componentDefinitionType);
      return create(componentDefinitionType, kind.get(), Optional.empty());
    }

    private ComponentDescriptor create(
        TypeElement componentDefinitionType, Kind kind, Optional<Kind> parentKind) {
      DeclaredType declaredComponentType = MoreTypes.asDeclared(componentDefinitionType.asType());
      AnnotationMirror componentMirror =
          getAnnotationMirror(componentDefinitionType, kind.annotationType()).get();
      ImmutableSet<ComponentRequirement> componentDependencies =
          kind.isTopLevel()
              ? getComponentDependencies(componentMirror)
                  .stream()
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

      ImmutableSet.Builder<ModuleDescriptor> modulesBuilder = ImmutableSet.builder();
      for (TypeMirror componentModulesType : getComponentModules(componentMirror)) {
        modulesBuilder.add(
            moduleDescriptorFactory.create(MoreTypes.asTypeElement(componentModulesType)));
      }
      if (kind.equals(Kind.PRODUCTION_COMPONENT)
          || (kind.equals(Kind.PRODUCTION_SUBCOMPONENT)
              && parentKind.isPresent()
              && (parentKind.get().equals(Kind.COMPONENT)
                  || parentKind.get().equals(Kind.SUBCOMPONENT)))) {
        modulesBuilder.add(descriptorForMonitoringModule(componentDefinitionType));
        modulesBuilder.add(descriptorForProductionExecutorModule(componentDefinitionType));
      }
      ImmutableSet<ModuleDescriptor> modules = modulesBuilder.build();
      ImmutableSet<ModuleDescriptor> transitiveModules = transitiveModules(modules);
      ImmutableSet.Builder<ComponentDescriptor> subcomponentsFromModules = ImmutableSet.builder();
      for (ModuleDescriptor module : transitiveModules) {
        for (SubcomponentDeclaration subcomponentDeclaration : module.subcomponentDeclarations()) {
          TypeElement subcomponent = subcomponentDeclaration.subcomponentType();
          subcomponentsFromModules.add(
              create(
                  subcomponent, Kind.forAnnotatedElement(subcomponent).get(), Optional.of(kind)));
        }
      }
      ImmutableSet<ExecutableElement> unimplementedMethods =
          elements.getUnimplementedMethods(componentDefinitionType);

      ImmutableSet.Builder<ComponentMethodDescriptor> componentMethodsBuilder =
          ImmutableSet.builder();

      ImmutableBiMap.Builder<ComponentMethodDescriptor, ComponentDescriptor>
          subcomponentsByFactoryMethod = ImmutableBiMap.builder();
      ImmutableBiMap.Builder<ComponentMethodDescriptor, ComponentDescriptor>
          subcomponentsByBuilderMethod = ImmutableBiMap.builder();
      for (ExecutableElement componentMethod : unimplementedMethods) {
        ExecutableType resolvedMethod =
            MoreTypes.asExecutable(types.asMemberOf(declaredComponentType, componentMethod));
        ComponentMethodDescriptor componentMethodDescriptor =
            getDescriptorForComponentMethod(componentDefinitionType, kind, componentMethod);
        componentMethodsBuilder.add(componentMethodDescriptor);
        switch (componentMethodDescriptor.kind()) {
          case SUBCOMPONENT:
          case PRODUCTION_SUBCOMPONENT:
            subcomponentsByFactoryMethod.put(
                componentMethodDescriptor,
                create(
                    MoreElements.asType(MoreTypes.asElement(resolvedMethod.getReturnType())),
                    componentMethodDescriptor.kind().componentKind(),
                    Optional.of(kind)));
            break;
          case SUBCOMPONENT_BUILDER:
          case PRODUCTION_SUBCOMPONENT_BUILDER:
            subcomponentsByBuilderMethod.put(
                componentMethodDescriptor,
                create(
                    MoreElements.asType(
                        MoreTypes.asElement(resolvedMethod.getReturnType()).getEnclosingElement()),
                    componentMethodDescriptor.kind().componentKind(),
                    Optional.of(kind)));
            break;
          default: // nothing special to do for other methods.
        }
      }

      ImmutableList<DeclaredType> enclosedBuilders = kind.builderAnnotationType() == null
          ? ImmutableList.<DeclaredType>of()
          : enclosedBuilders(componentDefinitionType, kind.builderAnnotationType());
      Optional<DeclaredType> builderType =
          Optional.ofNullable(getOnlyElement(enclosedBuilders, null));
      Optional<BuilderSpec> builderSpec = createBuilderSpec(builderType);

      ImmutableSet<Scope> scopes = scopesOf(componentDefinitionType);
      if (kind.isProducer()) {
        scopes = FluentIterable.from(scopes).append(productionScope(elements)).toSet();
      }

      return new AutoValue_ComponentDescriptor(
          kind,
          componentMirror,
          componentDefinitionType,
          componentDependencies,
          modules,
          transitiveModules,
          dependenciesByDependencyMethod.build(),
          scopes,
          subcomponentsFromModules.build(),
          subcomponentsByFactoryMethod.build(),
          subcomponentsByBuilderMethod.build(),
          componentMethodsBuilder.build(),
          builderSpec);
    }

    private ComponentMethodDescriptor getDescriptorForComponentMethod(
        TypeElement componentElement, Kind componentKind, ExecutableElement componentMethod) {
      ExecutableType resolvedComponentMethod =
          MoreTypes.asExecutable(
              types.asMemberOf(MoreTypes.asDeclared(componentElement.asType()), componentMethod));
      TypeMirror returnType = resolvedComponentMethod.getReturnType();
      if (returnType.getKind().equals(DECLARED)) {
        if (MoreTypes.isTypeOf(Provider.class, returnType)
            || MoreTypes.isTypeOf(Lazy.class, returnType)) {
          return ComponentMethodDescriptor.forProvision(
              componentMethod,
              dependencyRequestFactory.forComponentProvisionMethod(
                  componentMethod, resolvedComponentMethod));
        } else if (!getQualifier(componentMethod).isPresent()) {
          Element returnTypeElement = MoreTypes.asElement(returnType);
          if (isSubcomponent(returnTypeElement)) {
            return ComponentMethodDescriptor.forSubcomponent(
                isAnnotationPresent(returnTypeElement, Subcomponent.class)
                    ? ComponentMethodKind.SUBCOMPONENT
                    : ComponentMethodKind.PRODUCTION_SUBCOMPONENT,
                componentMethod);
          } else if (isSubcomponentBuilder(returnTypeElement)) {
            DependencyRequest dependencyRequest =
                dependencyRequestFactory.forComponentProvisionMethod(
                    componentMethod, resolvedComponentMethod);
            return ComponentMethodDescriptor.forSubcomponentBuilder(
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

    private Optional<BuilderSpec> createBuilderSpec(Optional<DeclaredType> builderType) {
      if (!builderType.isPresent()) {
        return Optional.empty();
      }
      TypeElement element = MoreTypes.asTypeElement(builderType.get());
      ImmutableSet<ExecutableElement> methods = elements.getUnimplementedMethods(element);
      ImmutableSet.Builder<BuilderRequirementMethod> requirementMethods = ImmutableSet.builder();
      ExecutableElement buildMethod = null;
      for (ExecutableElement method : methods) {
        if (method.getParameters().isEmpty()) {
          buildMethod = method;
        } else {
          ExecutableType resolved =
              MoreTypes.asExecutable(types.asMemberOf(builderType.get(), method));
          requirementMethods.add(
              new AutoValue_ComponentDescriptor_BuilderRequirementMethod(
                  method, requirementForBuilderMethod(method, resolved)));
        }
      }
      verify(buildMethod != null); // validation should have ensured this.
      return Optional.of(
          new AutoValue_ComponentDescriptor_BuilderSpec(
              element,
              requirementMethods.build(),
              buildMethod,
              element.getEnclosingElement().asType()));
    }

    private ComponentRequirement requirementForBuilderMethod(
        ExecutableElement method, ExecutableType resolvedType) {
      checkArgument(method.getParameters().size() == 1);
      if (isAnnotationPresent(method, BindsInstance.class)) {
        DependencyRequest request =
            dependencyRequestFactory.forRequiredResolvedVariable(
                getOnlyElement(method.getParameters()),
                getOnlyElement(resolvedType.getParameterTypes()));
        return ComponentRequirement.forBoundInstance(
            request.key(), request.isNullable(), method.getSimpleName().toString());
      }

      TypeMirror type = getOnlyElement(resolvedType.getParameterTypes());
      return ConfigurationAnnotations.getModuleAnnotation(MoreTypes.asTypeElement(type)).isPresent()
          ? ComponentRequirement.forModule(type)
          : ComponentRequirement.forDependency(type);
    }

    /**
     * Returns a descriptor for a generated module that handles monitoring for production
     * components. This module is generated in the {@link MonitoringModuleProcessingStep}.
     *
     * @throws TypeNotPresentException if the module has not been generated yet. This will cause the
     *     processor to retry in a later processing round.
     */
    private ModuleDescriptor descriptorForMonitoringModule(TypeElement componentDefinitionType) {
      ClassName monitoringModuleName =
          SourceFiles.generatedMonitoringModuleName(componentDefinitionType);
      TypeElement monitoringModule = elements.checkTypePresent(monitoringModuleName.toString());
      return moduleDescriptorFactory.create(monitoringModule);
    }

    /**
     * Returns a descriptor for a generated module that handles the producer executor for production
     * components. This module is generated in the {@link ProductionExecutorModuleProcessingStep}.
     *
     * @throws TypeNotPresentException if the module has not been generated yet. This will cause the
     *     processor to retry in a later processing round.
     */
    // TODO(beder): Replace this with a single class when the producers client library exists.
    private ModuleDescriptor descriptorForProductionExecutorModule(
        TypeElement componentDefinitionType) {
      ClassName productionExecutorModuleName =
          SourceFiles.generatedProductionExecutorModuleName(componentDefinitionType);
      TypeElement productionExecutorModule =
          elements.checkTypePresent(productionExecutorModuleName.toString());
      return moduleDescriptorFactory.create(productionExecutorModule);
    }
  }

  /**
   * No-argument methods defined on {@link Object} that are ignored for contribution.
   */
  private static final ImmutableSet<String> NON_CONTRIBUTING_OBJECT_METHOD_NAMES =
      ImmutableSet.of("toString", "hashCode", "clone", "getClass");

  static boolean isComponentContributionMethod(Elements elements, ExecutableElement method) {
    return method.getParameters().isEmpty()
        && !method.getReturnType().getKind().equals(VOID)
        && !elements
            .getTypeElement(Object.class.getCanonicalName())
            .equals(method.getEnclosingElement())
        && !NON_CONTRIBUTING_OBJECT_METHOD_NAMES.contains(method.getSimpleName().toString());
  }

  static boolean isComponentProductionMethod(Elements elements, ExecutableElement method) {
    return isComponentContributionMethod(elements, method)
        && MoreTypes.isTypeOf(ListenableFuture.class, method.getReturnType());
  }
}
