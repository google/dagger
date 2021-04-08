/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.processor.internal;

import static java.lang.Character.isUpperCase;
import static java.lang.String.format;
import static java.util.Comparator.comparing;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;
import com.squareup.javapoet.ClassName;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;

/**
 * Utility class for getting the generated component name.
 *
 * <p>This should not be used externally.
 */
public final class ComponentNames {
  private static final Splitter QUALIFIED_NAME_SPLITTER = Splitter.on('.');

  private final boolean renameTestComponents;
  private final String destinationPackage;
  private final ImmutableMap<ClassName, String> simpleNameByClassName;

  public static ComponentNames withoutRenaming() {
    return new ComponentNames(
        /*renameTestComponents=*/ false, /*destinationPackage=*/ null, ImmutableMap.of());
  }

  public static ComponentNames withRenamingIntoPackage(
      String destinationPackage, ImmutableList<TypeElement> roots) {
    ImmutableMap.Builder<ClassName, String> builder = ImmutableMap.builder();
    ImmutableListMultimap<String, TypeElement> rootsBySimpleName =
        Multimaps.index(roots, typeElement -> typeElement.getSimpleName().toString());
    rootsBySimpleName.asMap().values().stream()
        .map(ComponentNames::disambiguateConflictingSimpleNames)
        .forEach(builder::putAll);
    return new ComponentNames(/*renameTestComponents=*/ true, destinationPackage, builder.build());
  }

  private ComponentNames(
      boolean renameTestComponents,
      String destinationPackage,
      ImmutableMap<ClassName, String> simpleNameByClassName) {
    this.renameTestComponents = renameTestComponents;
    this.destinationPackage = destinationPackage;
    this.simpleNameByClassName = simpleNameByClassName;
  }

  /** Returns the name of the generated component wrapper. */
  public ClassName generatedComponentsWrapper(ClassName root) {
    return Processors.append(
        Processors.getEnclosedClassName(maybeRenameComponent(root)), "_HiltComponents");
  }

  /** Returns the name of the generated component. */
  public ClassName generatedComponent(ClassName root, ClassName component) {
    return generatedComponentsWrapper(root).nestedClass(componentName(component));
  }

  /**
   * Returns the name of the generated {@link dagger.hilt.android.internal.TestComponentData} for a
   * test class.
   */
  public ClassName generatedComponentDataHolder(ClassName testName) {
    return Processors.append(
        Processors.getEnclosedClassName(maybeRenameComponent(testName)), "_ComponentDataHolder");
  }

  /** Returns the name of components generated for the default root. */
  public ClassName defaultRootComponentName(ClassName component) {
    return generatedComponentsWrapper(ClassNames.DEFAULT_ROOT)
        .nestedClass(componentName(component));
  }

  /**
   * Returns the shortened component name by replacing the ending "Component" with "C" if it exists.
   *
   * <p>This is a hack because nested subcomponents in Dagger generate extremely long class names
   * that hit the 256 character limit.
   */
  // TODO(bcorso): See if this issue can be fixed in Dagger, e.g. by using static subcomponents.
  private static String componentName(ClassName component) {
    // TODO(bcorso): How do we want to handle collisions across packages? Currently, we only handle
    // collisions across enclosing elements since namespacing by package would likely lead to too
    // long of class names.
    // Note: This uses regex matching so we only match if the name ends in "Component"
    return Processors.getEnclosedName(component).replaceAll("Component$", "C");
  }

  /**
   * Rewrites the provided HiltAndroidTest-annotated class name using the shared component
   * directory.
   */
  private ClassName maybeRenameComponent(ClassName className) {
    return (renameTestComponents && !className.equals(ClassNames.DEFAULT_ROOT))
        ? ClassName.get(destinationPackage, dedupeSimpleName(className))
        : className;
  }

  /**
   * Derives a new generated component base name, should the simple names of two roots have
   * conflicting simple names.
   *
   * <p>This is lifted nearly verbatim (albeit with new different struct types) from {@link
   * dagger.internal.codegen.writing.SubcomponentNames}.
   */
  private String dedupeSimpleName(ClassName className) {
    Preconditions.checkState(
        simpleNameByClassName.containsKey(className),
        "Class name %s not found in simple name map",
        className.canonicalName());
    return simpleNameByClassName.get(className);
  }

  private static ImmutableMap<ClassName, String> disambiguateConflictingSimpleNames(
      Collection<TypeElement> rootsWithConflictingNames) {
    // If there's only 1 root there's nothing to disambiguate so return the simple name.
    if (rootsWithConflictingNames.size() == 1) {
      TypeElement root = Iterables.getOnlyElement(rootsWithConflictingNames);
      return ImmutableMap.of(ClassName.get(root), root.getSimpleName().toString());
    }

    // There are conflicting simple names, so disambiguate them with a unique prefix.
    // We keep them small to fix https://github.com/google/dagger/issues/421.
    // Sorted in order to guarantee determinism if this is invoked by different processors.
    ImmutableList<TypeElement> sortedRootsWithConflictingNames =
        ImmutableList.sortedCopyOf(
            comparing(typeElement -> typeElement.getQualifiedName().toString()),
            rootsWithConflictingNames);
    Set<String> usedNames = new HashSet<>();
    ImmutableMap.Builder<ClassName, String> uniqueNames = ImmutableMap.builder();
    for (TypeElement root : sortedRootsWithConflictingNames) {
      String basePrefix = uniquingPrefix(root);
      String uniqueName = basePrefix;
      for (int differentiator = 2; !usedNames.add(uniqueName); differentiator++) {
        uniqueName = basePrefix + differentiator;
      }
      uniqueNames.put(ClassName.get(root), format("%s_%s", uniqueName, root.getSimpleName()));
    }
    return uniqueNames.build();
  }

  /** Returns a prefix that could make the component's simple name more unique. */
  private static String uniquingPrefix(TypeElement typeElement) {
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
