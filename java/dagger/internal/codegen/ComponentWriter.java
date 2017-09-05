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

import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.TypeSpecs.addSupertype;
import static java.lang.Character.isUpperCase;
import static java.lang.String.format;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Creates the implementation class for a component.
 */
final class ComponentWriter extends AbstractComponentWriter {

  ComponentWriter(
      Types types,
      Elements elements,
      Key.Factory keyFactory,
      CompilerOptions compilerOptions,
      ClassName name,
      BindingGraph graph) {
    super(
        types,
        elements,
        keyFactory,
        compilerOptions,
        name,
        graph,
        new UniqueSubcomponentNamesGenerator(graph).generate(),
        new OptionalFactories(),
        new ComponentBindingExpressions(),
        new ComponentRequirementFields());
  }

  /**
   * Generates a map of unique simple names for all subcomponents, keyed by their {@link
   * ComponentDescriptor}.
   */
  private static class UniqueSubcomponentNamesGenerator {

    private static final Splitter QUALIFIED_NAME_SPLITTER = Splitter.on('.');

    private final BindingGraph graph;
    private final ImmutableListMultimap<String, ComponentDescriptor>
        componentDescriptorsBySimpleName;
    private final ImmutableMap<ComponentDescriptor, Namer> componentNamers;

    private UniqueSubcomponentNamesGenerator(BindingGraph graph) {
      this.graph = graph;
      componentDescriptorsBySimpleName =
          Multimaps.index(
              graph.componentDescriptors(),
              componentDescriptor ->
                  componentDescriptor.componentDefinitionType().getSimpleName().toString());
      componentNamers = qualifiedNames(graph.componentDescriptors());
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

      // Let's see if we can get away with using simpleName() everywhere.
      for (ComponentDescriptor component : components) {
        Namer namer = componentNamers.get(component);
        if (generatedSimpleNames.containsKey(namer.simpleName())) {
          break;
        }
        generatedSimpleNames.put(namer.simpleName(), component);
      }

      if (generatedSimpleNames.size() != components.size()) {
        // Simple approach didn't work out, let's use more complicated names.
        // We keep them small to fix https://github.com/google/dagger/issues/421.
        generatedSimpleNames.clear();
        UniqueNameSet nameSet = new UniqueNameSet();
        for (ComponentDescriptor component : components) {
          Namer namer = componentNamers.get(component);
          String simpleName = namer.simpleName();
          String basePrefix = namer.uniquingPrefix();
          generatedSimpleNames.put(format("%s_%s", nameSet.getUniqueName(basePrefix), simpleName),
              component);
        }
      }
      return ImmutableBiMap.copyOf(generatedSimpleNames).inverse();
    }

    private static ImmutableMap<ComponentDescriptor, Namer> qualifiedNames(
        Iterable<ComponentDescriptor> componentDescriptors) {
      ImmutableMap.Builder<ComponentDescriptor, Namer> builder = ImmutableMap.builder();
      for (ComponentDescriptor component : componentDescriptors) {
        builder.put(component, new Namer(component.componentDefinitionType()));
      }
      return builder.build();
    }

    private static final class Namer {
      final TypeElement typeElement;

      Namer(TypeElement typeElement) {
        this.typeElement = typeElement;
      }

      String simpleName() {
        return typeElement.getSimpleName().toString();
      }

      /** Returns a prefix that could make {@link #simpleName()} more unique. */
      String uniquingPrefix() {
        String containerName = typeElement.getEnclosingElement().getSimpleName().toString();

        // If parent element looks like a class, use its initials as a prefix.
        if (!containerName.isEmpty() && isUpperCase(containerName.charAt(0))) {
          return CharMatcher.javaLowerCase().removeFrom(containerName);
        }

        // Not in a normally named class. Prefix with the initials of the elements leading here.
        Name qualifiedName = typeElement.getQualifiedName();
        Iterator<String> pieces = QUALIFIED_NAME_SPLITTER.split(qualifiedName).iterator();
        StringBuilder b = new StringBuilder();

        while (pieces.hasNext()) {
          String next = pieces.next();
          if (pieces.hasNext()) {
            b.append(next.charAt(0));
          }
        }

        // Note that a top level class in the root package will be prefixed "$_".
        return b.length() > 0 ? b.toString() : "$";
      }
    }
  }

  @Override
  protected void decorateComponent() {
    component.addModifiers(PUBLIC, FINAL);
    addSupertype(component, graph.componentType());
  }

  private void addBuilderFactoryMethod() {
    // Only top-level components have the factory builder() method.
    // Mirror the user's builder API type if they had one.
    MethodSpec builderFactoryMethod =
        methodBuilder("builder")
            .addModifiers(PUBLIC, STATIC)
            .returns(
                graph.componentDescriptor().builderSpec().isPresent()
                    ? ClassName.get(
                    graph.componentDescriptor().builderSpec().get().builderDefinitionType())
                    : builderName())
            .addStatement("return new $T()", builderName())
            .build();
    component.addMethod(builderFactoryMethod);
  }

  @Override
  protected void addBuilderClass(TypeSpec builder) {
    component.addType(builder);
  }

  @Override
  protected void addFactoryMethods() {
    addBuilderFactoryMethod();
    if (canInstantiateAllRequirements()) {
      CharSequence buildMethodName =
          graph.componentDescriptor().builderSpec().isPresent()
              ? graph.componentDescriptor().builderSpec().get().buildMethod().getSimpleName()
              : "build";
      component.addMethod(
          methodBuilder("create")
              .returns(ClassName.get(graph.componentType()))
              .addModifiers(PUBLIC, STATIC)
              .addStatement("return new Builder().$L()", buildMethodName)
              .build());
    }
  }

  /** {@code true} if all of the graph's required dependencies can be automatically constructed. */
  private boolean canInstantiateAllRequirements() {
    return !Iterables.any(
        graph.componentRequirements(),
        dependency -> dependency.requiresAPassedInstance(elements, types));
  }

  @Override
  protected boolean requiresReleasableReferences(Scope scope) {
    return graph.scopesRequiringReleasableReferenceManagers().contains(scope);
  }
}
