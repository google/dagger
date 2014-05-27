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

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Queues;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static dagger.internal.codegen.AnnotationMirrors.getAnnotationMirror;
import static javax.lang.model.element.Modifier.ABSTRACT;

/**
 * The logical representation of a {@link Component} definition.
 *
 * @author Gregory Kick
 * @since 2.0
 */
@AutoValue
abstract class ComponentDescriptor {
  ComponentDescriptor() {}

  /**
   * The type (interface or abstract class) that defines the component. This is the element to which
   * the {@link Component} annotation was applied.
   */
  abstract TypeElement componentDefinitionType();

  /**
   * The set of {@linkplain DependencyRequest dependency requests} representing  the provision
   * methods in the component definition.  To access the method element itself, use
   * {@link DependencyRequest#requestElement()}.
   */
  abstract ImmutableSet<DependencyRequest> provisionRequests();

  /**
   * The set of {@linkplain DependencyRequest dependency requests} representing the members
   * injection methods in the component definition.  To access the method element itself, use
   * {@link DependencyRequest#requestElement()}.
   */
  abstract ImmutableSet<DependencyRequest> membersInjectionRequests();

  /**
   * The total set of modules (those declared in {@link Component#modules} and their transitive
   * dependencies) required to construct the object graph declared by the component.
   */
  abstract ImmutableSet<TypeElement> moduleDependencies();

  /**
   * Returns the mapping from {@link Key} to {@link ProvisionBinding} that represents the full
   * adjacency matrix for the object graph.
   */
  abstract ImmutableSetMultimap<Key, ProvisionBinding> resolvedBindings();

  static final class Factory {
    private final Elements elements;
    private final Types types;
    private final InjectBindingRegistry injectBindingRegistry;
    private final ProvisionBinding.Factory provisionBindingFactory;
    private final DependencyRequest.Factory dependencyRequestFactory;

    Factory(Elements elements, Types types, InjectBindingRegistry injectBindingRegistry,
        ProvisionBinding.Factory provisionBindingFactory,
        DependencyRequest.Factory dependencyRequestFactory) {
      this.elements = elements;
      this.types = types;
      this.injectBindingRegistry = injectBindingRegistry;
      this.provisionBindingFactory = provisionBindingFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
    }

    private ImmutableSet<TypeElement> getTransitiveModules(ImmutableSet<TypeElement> seedModules) {
      Queue<TypeElement> moduleQueue = Queues.newArrayDeque(seedModules);
      LinkedHashSet<TypeElement> moduleElements = Sets.newLinkedHashSet();
      for (TypeElement moduleElement = moduleQueue.poll();
          moduleElement != null;
          moduleElement = moduleQueue.poll()) {
        moduleElements.add(moduleElement);
        AnnotationMirror moduleMirror =
            getAnnotationMirror(moduleElement, Module.class).get();
        ImmutableSet<TypeElement> moduleDependencies = MoreTypes.asTypeElements(types,
            ConfigurationAnnotations.getModuleIncludes(elements, moduleMirror));
        for (TypeElement dependencyType : moduleDependencies) {
          if (!moduleElements.contains(dependencyType)) {
            moduleQueue.add(dependencyType);
          }
        }
      }
      return ImmutableSet.copyOf(moduleElements);
    }

    ComponentDescriptor create(TypeElement componentDefinitionType) {
      AnnotationMirror componentMirror =
          getAnnotationMirror(componentDefinitionType, Component.class).get();
      ImmutableSet<TypeElement> moduleTypes = MoreTypes.asTypeElements(types,
          ConfigurationAnnotations.getComponentModules(elements, componentMirror));
      ImmutableSet<TypeElement> transitiveModules = getTransitiveModules(moduleTypes);

      ImmutableSetMultimap.Builder<Key, ProvisionBinding> bindingIndexBuilder =
          ImmutableSetMultimap.builder();

      for (TypeElement module : transitiveModules) {
        // traverse the modules, collect the bindings
        List<ExecutableElement> moduleMethods =
            ElementFilter.methodsIn(elements.getAllMembers(module));
        for (ExecutableElement moduleMethod : moduleMethods) {
          if (moduleMethod.getAnnotation(Provides.class) != null) {
            ProvisionBinding providesMethodBinding =
                provisionBindingFactory.forProvidesMethod(moduleMethod);
            bindingIndexBuilder.put(providesMethodBinding.providedKey(), providesMethodBinding);
          }
        }
      }

      ImmutableSetMultimap<Key, ProvisionBinding> explicitBindings = bindingIndexBuilder.build();

      ImmutableSet.Builder<DependencyRequest> provisionRequestsBuilder = ImmutableSet.builder();
      ImmutableSet.Builder<DependencyRequest> membersInjectionRequestsBuilder =
          ImmutableSet.builder();

      Deque<DependencyRequest> requestsToResolve = Queues.newArrayDeque();

      for (ExecutableElement componentMethod
          : ElementFilter.methodsIn(elements.getAllMembers(componentDefinitionType))) {
        if (componentMethod.getModifiers().contains(ABSTRACT)) {
          List<? extends VariableElement> parameters = componentMethod.getParameters();
          switch (parameters.size()) {
            case 0:
              // provision method
              DependencyRequest provisionRequest =
                  dependencyRequestFactory.forComponentProvisionMethod(componentMethod);
              provisionRequestsBuilder.add(provisionRequest);
              requestsToResolve.addLast(provisionRequest);
              break;
            case 1:
              // members injection method
              membersInjectionRequestsBuilder.add(
                  dependencyRequestFactory.forComponentMembersInjectionMethod(componentMethod));
              break;
            default:
              throw new IllegalStateException();
          }
        }
      }

      SetMultimap<Key, ProvisionBinding> resolvedBindings = LinkedHashMultimap.create();

      for (DependencyRequest requestToResolve = requestsToResolve.pollLast();
          requestToResolve != null;
          requestToResolve = requestsToResolve.pollLast()) {
        Key key = requestToResolve.key();
        if (!resolvedBindings.containsKey(key)) {
          ImmutableSet<ProvisionBinding> explicitBindingsForKey = explicitBindings.get(key);
          if (explicitBindingsForKey.isEmpty()) {
            Optional<ProvisionBinding> injectBinding =
                injectBindingRegistry.getBindingForKey(key);
            if (injectBinding.isPresent()) {
              requestsToResolve.addAll(injectBinding.get().dependencies());
              resolvedBindings.put(key, injectBinding.get());
            } else {
              // TODO(gak): generate a factory for an @Inject dependency that wasn't run with the
              // processor
              throw new UnsupportedOperationException("@Injected classes that weren't run with the "
                  + "compoenent processor are (briefly) unsupported: " + key);
            }
          } else {
            resolvedBindings.putAll(key, explicitBindingsForKey);
          }
          for (ProvisionBinding binding : explicitBindingsForKey) {
            requestsToResolve.addAll(binding.dependencies());
          }
        }
      }

      return new AutoValue_ComponentDescriptor(
          componentDefinitionType,
          provisionRequestsBuilder.build(),
          membersInjectionRequestsBuilder.build(),
          moduleTypes,
          ImmutableSetMultimap.copyOf(resolvedBindings));
    }
  }
}
