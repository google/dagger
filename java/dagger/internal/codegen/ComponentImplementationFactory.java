/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.ComponentGenerator.componentName;
import static dagger.internal.codegen.Util.reentrantComputeIfAbsent;
import static javax.tools.Diagnostic.Kind.WARNING;

import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.serialization.ProtoSerialization.InconsistentSerializedProtoException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.TypeElement;

/** Factory for {@link ComponentImplementation}s. */
@Singleton
final class ComponentImplementationFactory implements ClearableCache {
  private final Map<TypeElement, ComponentImplementation> topLevelComponentCache = new HashMap<>();
  private final KeyFactory keyFactory;
  private final CompilerOptions compilerOptions;
  private final BindingGraphFactory bindingGraphFactory;
  private final TopLevelImplementationComponent.Builder topLevelImplementationComponentBuilder;
  private final DeserializedComponentImplementationBuilder
      deserializedComponentImplementationBuilder;
  private final DaggerElements elements;
  private final Messager messager;

  @Inject
  ComponentImplementationFactory(
      KeyFactory keyFactory,
      CompilerOptions compilerOptions,
      BindingGraphFactory bindingGraphFactory,
      TopLevelImplementationComponent.Builder topLevelImplementationComponentBuilder,
      DeserializedComponentImplementationBuilder deserializedComponentImplementationBuilder,
      DaggerElements elements,
      Messager messager) {
    this.keyFactory = keyFactory;
    this.compilerOptions = compilerOptions;
    this.bindingGraphFactory = bindingGraphFactory;
    this.topLevelImplementationComponentBuilder = topLevelImplementationComponentBuilder;
    this.deserializedComponentImplementationBuilder = deserializedComponentImplementationBuilder;
    this.elements = elements;
    this.messager = messager;
  }

  /**
   * Returns a top-level (non-nested) component implementation for a binding graph.
   *
   * @throws IllegalStateException if the binding graph is for a subcomponent and
   *     ahead-of-time-subcomponents mode is not enabled
   */
  ComponentImplementation createComponentImplementation(BindingGraph bindingGraph) {
    return reentrantComputeIfAbsent(
        topLevelComponentCache,
        bindingGraph.componentTypeElement(),
        component -> createComponentImplementationUncached(bindingGraph));
  }

  private ComponentImplementation createComponentImplementationUncached(BindingGraph bindingGraph) {
    ComponentImplementation componentImplementation =
        ComponentImplementation.topLevelComponentImplementation(
            bindingGraph,
            componentName(bindingGraph.componentTypeElement()),
            new SubcomponentNames(bindingGraph, keyFactory),
            compilerOptions);

    // TODO(dpb): explore using optional bindings for the "parent" bindings
    CurrentImplementationSubcomponent currentImplementationSubcomponent =
        topLevelImplementationComponentBuilder
            .topLevelComponent(componentImplementation)
            .build()
            .currentImplementationSubcomponentBuilder()
            .componentImplementation(componentImplementation)
            .bindingGraph(bindingGraph)
            .parentBuilder(Optional.empty())
            .parentBindingExpressions(Optional.empty())
            .parentRequirementExpressions(Optional.empty())
            .build();

    if (componentImplementation.isAbstract()) {
      checkState(
          compilerOptions.aheadOfTimeSubcomponents(),
          "Calling 'componentImplementation()' on %s when not generating ahead-of-time "
              + "subcomponents.",
          bindingGraph.componentTypeElement());
      return currentImplementationSubcomponent.subcomponentBuilder().build();
    } else {
      return currentImplementationSubcomponent.rootComponentBuilder().build();
    }
  }

  /** Returns the superclass of the child nested within a superclass of the parent component. */
  ComponentImplementation findChildSuperclassImplementation(
      ComponentDescriptor child, ComponentImplementation parentImplementation) {
    // If the current component has superclass implementations, a superclass may contain a
    // reference to the child. Traverse this component's superimplementation hierarchy looking for
    // the child's implementation. The child superclass implementation may not be present in the
    // direct superclass implementations if the subcomponent builder was previously a pruned
    // binding.
    for (Optional<ComponentImplementation> parent = parentImplementation.superclassImplementation();
        parent.isPresent();
        parent = parent.get().superclassImplementation()) {
      Optional<ComponentImplementation> superclass = parent.get().childImplementation(child);
      if (superclass.isPresent()) {
        return superclass.get();
      }
    }

    if (compilerOptions.emitModifiableMetadataAnnotations()) {
      ClassName childSuperclassName = componentName(child.typeElement());
      TypeElement generatedChildSuperclassImplementation =
          elements.getTypeElement(childSuperclassName);
      if (generatedChildSuperclassImplementation != null) {
        try {
          return deserializedComponentImplementationBuilder.create(
              child, generatedChildSuperclassImplementation);
        } catch (InconsistentSerializedProtoException e) {
          messager.printMessage(
              WARNING,
              String.format(
                  "%s was compiled with a different version of Dagger than the version in this "
                      + "compilation. To ensure the validity of Dagger's generated code, compile "
                      + "all Dagger code with the same version.",
                  child.typeElement().getQualifiedName()));
        }
      } else if (compilerOptions.forceUseSerializedComponentImplementations()) {
        throw new TypeNotPresentException(childSuperclassName.toString(), null);
      }
    }

    // Otherwise, the superclass implementation is top-level, so we must recreate the
    // implementation object for the base implementation of the child by truncating the binding
    // graph at the child.
    BindingGraph truncatedBindingGraph = bindingGraphFactory.create(child, false);
    return createComponentImplementation(truncatedBindingGraph);
  }

  @Override
  public void clearCache() {
    topLevelComponentCache.clear();
  }
}
