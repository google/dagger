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

package dagger.internal.codegen.writing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.binding.SourceFiles.classFileName;
import static dagger.internal.codegen.extension.DaggerCollectors.onlyElement;
import static java.lang.String.format;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.base.ComponentCreatorKind;
import dagger.internal.codegen.base.UniqueNameSet;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.ComponentCreatorDescriptor;
import dagger.internal.codegen.binding.ComponentDescriptor;
import dagger.internal.codegen.binding.KeyFactory;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.spi.model.ComponentPath;
import dagger.spi.model.Key;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;

/**
 * Holds the unique simple names for all components, keyed by their {@link ComponentPath} and {@link
 * Key} of the subcomponent builder.
 */
public final class ComponentNames {
  /** Returns the class name for the top-level generated class. */
  public static ClassName getTopLevelClassName(ComponentDescriptor componentDescriptor) {
    checkState(!componentDescriptor.isSubcomponent());
    ClassName componentName = componentDescriptor.typeElement().getClassName();
    return ClassName.get(componentName.packageName(), "Dagger" + classFileName(componentName));
  }

  private static final Splitter QUALIFIED_NAME_SPLITTER = Splitter.on('.');

  private final CompilerOptions compilerOptions;
  private final ClassName topLevelClassName;
  private final ImmutableMap<ComponentPath, String> namesByPath;
  private final ImmutableMap<ComponentPath, String> creatorNamesByPath;
  private final ImmutableMultimap<Key, ComponentPath> pathsByCreatorKey;

  @Inject
  ComponentNames(
      CompilerOptions compilerOptions, @TopLevel BindingGraph graph, KeyFactory keyFactory) {
    this.compilerOptions = compilerOptions;
    this.topLevelClassName = getTopLevelClassName(graph.componentDescriptor());
    this.namesByPath = namesByPath(graph);
    this.creatorNamesByPath = creatorNamesByPath(namesByPath, graph);
    this.pathsByCreatorKey = pathsByCreatorKey(keyFactory, graph);
  }

  /** Returns the simple component name for the given {@link ComponentDescriptor}. */
  ClassName get(ComponentPath componentPath) {
    return compilerOptions.generatedClassExtendsComponent() && componentPath.atRoot()
        ? topLevelClassName
        : topLevelClassName.nestedClass(namesByPath.get(componentPath) + "Impl");
  }

  /**
   * Returns the component descriptor for the component with the given subcomponent creator {@link
   * Key}.
   */
  ClassName getSubcomponentCreatorName(ComponentPath componentPath, Key creatorKey) {
    checkArgument(pathsByCreatorKey.containsKey(creatorKey));
    // First, find the subcomponent path corresponding to the subcomponent creator key.
    // The key may correspond to multiple paths, so we need to find the one under this component.
    ComponentPath subcomponentPath =
        pathsByCreatorKey.get(creatorKey).stream()
            .filter(path -> path.parent().equals(componentPath))
            .collect(onlyElement());
    return getCreatorName(subcomponentPath);
  }

  /**
   * Returns the simple name for the subcomponent creator implementation for the given {@link
   * ComponentDescriptor}.
   */
  ClassName getCreatorName(ComponentPath componentPath) {
    checkArgument(creatorNamesByPath.containsKey(componentPath));
    return topLevelClassName.nestedClass(creatorNamesByPath.get(componentPath));
  }

  private static ImmutableMap<ComponentPath, String> creatorNamesByPath(
      ImmutableMap<ComponentPath, String> namesByPath, BindingGraph graph) {
    ImmutableMap.Builder<ComponentPath, String> builder = ImmutableMap.builder();
    graph
        .componentDescriptorsByPath()
        .forEach(
            (componentPath, componentDescriptor) -> {
              if (componentPath.atRoot()) {
                ComponentCreatorKind creatorKind =
                    componentDescriptor
                        .creatorDescriptor()
                        .map(ComponentCreatorDescriptor::kind)
                        .orElse(ComponentCreatorKind.BUILDER);
                builder.put(componentPath, creatorKind.typeName());
              } else if (componentDescriptor.creatorDescriptor().isPresent()) {
                ComponentCreatorDescriptor creatorDescriptor =
                    componentDescriptor.creatorDescriptor().get();
                String componentName = namesByPath.get(componentPath);
                builder.put(componentPath, componentName + creatorDescriptor.kind().typeName());
              }
            });
    return builder.build();
  }

  private static ImmutableMap<ComponentPath, String> namesByPath(BindingGraph graph) {
    Map<ComponentPath, String> componentPathsBySimpleName = new LinkedHashMap<>();
    Multimaps.index(graph.componentDescriptorsByPath().keySet(), ComponentNames::simpleName)
        .asMap()
        .values()
        .stream()
        .map(ComponentNames::disambiguateConflictingSimpleNames)
        .forEach(componentPathsBySimpleName::putAll);
    return ImmutableMap.copyOf(componentPathsBySimpleName);
  }

  private static ImmutableMultimap<Key, ComponentPath> pathsByCreatorKey(
      KeyFactory keyFactory, BindingGraph graph) {
    ImmutableMultimap.Builder<Key, ComponentPath> builder = ImmutableMultimap.builder();
    graph
        .componentDescriptorsByPath()
        .forEach(
            (componentPath, componentDescriptor) -> {
              if (componentDescriptor.creatorDescriptor().isPresent()) {
                Key creatorKey =
                    keyFactory.forSubcomponentCreator(
                        componentDescriptor.creatorDescriptor().get().typeElement().getType());
                builder.put(creatorKey, componentPath);
              }
            });
    return builder.build();
  }

  private static ImmutableMap<ComponentPath, String> disambiguateConflictingSimpleNames(
      Collection<ComponentPath> componentsWithConflictingNames) {
    // If there's only 1 component there's nothing to disambiguate so return the simple name.
    if (componentsWithConflictingNames.size() == 1) {
      ComponentPath componentPath = Iterables.getOnlyElement(componentsWithConflictingNames);
      return ImmutableMap.of(componentPath, simpleName(componentPath));
    }

    // There are conflicting simple names, so disambiguate them with a unique prefix.
    // We keep them small to fix https://github.com/google/dagger/issues/421.
    UniqueNameSet nameSet = new UniqueNameSet();
    ImmutableMap.Builder<ComponentPath, String> uniqueNames = ImmutableMap.builder();
    for (ComponentPath componentPath : componentsWithConflictingNames) {
      String simpleName = simpleName(componentPath);
      String basePrefix = uniquingPrefix(componentPath);
      uniqueNames.put(
          componentPath, format("%s_%s", nameSet.getUniqueName(basePrefix), simpleName));
    }
    return uniqueNames.build();
  }

  private static String simpleName(ComponentPath componentPath) {
    return componentPath.currentComponent().className().simpleName();
  }

  /** Returns a prefix that could make the component's simple name more unique. */
  private static String uniquingPrefix(ComponentPath componentPath) {
    ClassName component = componentPath.currentComponent().className();

    if (component.enclosingClassName() != null) {
      return CharMatcher.javaLowerCase().removeFrom(component.enclosingClassName().simpleName());
    }

    // Not in a normally named class. Prefix with the initials of the elements leading here.
    Iterator<String> pieces = QUALIFIED_NAME_SPLITTER.split(component.canonicalName()).iterator();
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
