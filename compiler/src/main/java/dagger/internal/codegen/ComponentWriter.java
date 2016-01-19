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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ClassWriter;
import dagger.internal.codegen.writer.JavaWriter;
import dagger.internal.codegen.writer.MethodWriter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Generated;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

import static dagger.internal.codegen.Util.componentCanMakeNewInstances;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Creates the implementation class for a component.
 */
final class ComponentWriter extends AbstractComponentWriter {

  ComponentWriter(
      Types types,
      Elements elements,
      Key.Factory keyFactory,
      Kind nullableValidationType,
      ClassName name,
      BindingGraph graph) {
    super(
        types,
        elements,
        keyFactory,
        nullableValidationType,
        name,
        graph,
        new UniqueSubcomponentNamesGenerator(graph).generate());
  }

  /**
   * Generates a map of unique simple names for all subcomponents, keyed by their {@link
   * ComponentDescriptor}.
   */
  private static class UniqueSubcomponentNamesGenerator {

    private static final Splitter QUALIFIED_NAME_SPLITTER = Splitter.on('.');
    private static final Joiner QUALIFIED_NAME_JOINER = Joiner.on('_');

    private final BindingGraph graph;
    private final ImmutableListMultimap<String, ComponentDescriptor>
        componentDescriptorsBySimpleName;
    private final ImmutableListMultimap<ComponentDescriptor, String> componentQualifiedNamePieces;

    private UniqueSubcomponentNamesGenerator(BindingGraph graph) {
      this.graph = graph;
      componentDescriptorsBySimpleName =
          Multimaps.index(
              graph.componentDescriptors(),
              new Function<ComponentDescriptor, String>() {
                @Override
                public String apply(ComponentDescriptor componentDescriptor) {
                  return componentDescriptor.componentDefinitionType().getSimpleName().toString();
                }
              });
      componentQualifiedNamePieces = qualifiedNames(graph.componentDescriptors());
    }

    private ImmutableBiMap<ComponentDescriptor, String> generate() {
      Map<ComponentDescriptor, String> subcomponentImplSimpleNames = new LinkedHashMap<>();
      for (Entry<String, Collection<ComponentDescriptor>> componentEntry :
          componentDescriptorsBySimpleName.asMap().entrySet()) {
        Collection<ComponentDescriptor> components = componentEntry.getValue();
        subcomponentImplSimpleNames.putAll(disambiguateConflictingSimpleNames(components));
      }
      subcomponentImplSimpleNames.remove(graph.componentDescriptor());
      return ImmutableBiMap.copyOf(subcomponentImplSimpleNames);
    }

    private ImmutableBiMap<ComponentDescriptor, String> disambiguateConflictingSimpleNames(
        Collection<ComponentDescriptor> components) {
      Map<String, ComponentDescriptor> generatedSimpleNames = new LinkedHashMap<>();
      // The ending condition is when there is a unique simple name generated for every element
      // in components. The sizes should be equivalent (with one generated name per component).
      for (int levels = 0; generatedSimpleNames.size() != components.size(); levels++) {
        generatedSimpleNames.clear();
        for (ComponentDescriptor component : components) {
          List<String> pieces = componentQualifiedNamePieces.get(component);
          String simpleName =
              QUALIFIED_NAME_JOINER.join(
                      pieces.subList(Math.max(0, pieces.size() - levels - 1), pieces.size()))
                  + "Impl";
          ComponentDescriptor conflict = generatedSimpleNames.put(simpleName, component);
          if (conflict != null) {
            // if the map previously contained an entry for the same simple name, stop early since
            // 2+ subcomponent descriptors will have the same simple name
            break;
          }
        }
      }
      return ImmutableBiMap.copyOf(generatedSimpleNames).inverse();
    }

    private static ImmutableListMultimap<ComponentDescriptor, String> qualifiedNames(
        Iterable<ComponentDescriptor> componentDescriptors) {
      ImmutableListMultimap.Builder<ComponentDescriptor, String> builder =
          ImmutableListMultimap.builder();
      for (ComponentDescriptor component : componentDescriptors) {
        Name qualifiedName = component.componentDefinitionType().getQualifiedName();
        builder.putAll(component, QUALIFIED_NAME_SPLITTER.split(qualifiedName));
      }
      return builder.build();
    }
  }

  @Override
  protected ClassWriter createComponentClass() {
    JavaWriter javaWriter = JavaWriter.inPackage(name.packageName());
    javaWriters.add(javaWriter);

    ClassWriter componentWriter = javaWriter.addClass(name.simpleName());
    componentWriter.addModifiers(PUBLIC, FINAL);
    componentWriter.setSupertype(componentDefinitionType());
    return componentWriter;
  }

  @Override
  protected ClassWriter createBuilder() {
    ClassWriter builderWriter = componentWriter.addNestedClass("Builder");
    builderWriter.addModifiers(STATIC);

    // Only top-level components have the factory builder() method.
    // Mirror the user's builder API type if they had one.
    MethodWriter builderFactoryMethod =
        graph.componentDescriptor().builderSpec().isPresent()
            ? componentWriter.addMethod(
                graph
                    .componentDescriptor()
                    .builderSpec()
                    .get()
                    .builderDefinitionType()
                    .asType(),
                "builder")
            : componentWriter.addMethod(builderWriter, "builder");
    builderFactoryMethod.addModifiers(PUBLIC, STATIC);
    builderFactoryMethod.body().addSnippet("return new %s();", builderWriter.name());
    return builderWriter;
  }

  @Override
  protected void addFactoryMethods() {
    if (canInstantiateAllRequirements()) {
      MethodWriter factoryMethod =
          componentWriter.addMethod(componentDefinitionTypeName(), "create");
      factoryMethod.addModifiers(PUBLIC, STATIC);
      // TODO(gak): replace this with something that doesn't allocate a builder
      factoryMethod
          .body()
          .addSnippet(
              "return builder().%s();",
              graph.componentDescriptor().builderSpec().isPresent()
                  ? graph
                      .componentDescriptor()
                      .builderSpec()
                      .get()
                      .buildMethod()
                      .getSimpleName()
                  : "build");
    }
  }

  /** {@code true} if all of the graph's required dependencies can be automatically constructed. */
  private boolean canInstantiateAllRequirements() {
    return Iterables.all(
        graph.componentRequirements(),
        new Predicate<TypeElement>() {
          @Override
          public boolean apply(TypeElement dependency) {
            return componentCanMakeNewInstances(dependency);
          }
        });
  }
}
