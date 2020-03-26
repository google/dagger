/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.hilt.processor.internal.aggregateddeps;

import static com.google.common.base.Preconditions.checkState;
import static dagger.hilt.processor.internal.Processors.toTypeElements;

import com.google.auto.common.MoreElements;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ComponentDescriptor;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import dagger.hilt.processor.internal.definecomponent.DefineComponents;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * Represents information needed to create a component (i.e. modules, entry points, etc)
 */
public final class ComponentDependencies {
  static final String AGGREGATING_PACKAGE = "hilt_aggregated_deps";

  /** A key used for grouping a test dependency by both its component and test name. */
  @AutoValue
  abstract static class TestDepKey {
    static TestDepKey of(ClassName component, ClassName test) {
      return new AutoValue_ComponentDependencies_TestDepKey(component, test);
    }

    /** Returns the name of the component this dependency should be installed in. */
    abstract ClassName component();

    /** Returns the name of the test that this dependency should be installed in. */
    abstract ClassName test();
  }

  /**
   * Holds a set of component dependencies, e.g. modules or entry points.
   *
   * <p>This class handles separating dependencies into global and test dependencies. Global
   * dependencies are installed with every test, where test dependencies are only installed with the
   * specified test. The total set of dependencies includes all global + test dependencies.
   */
  private static final class Dependencies {
    private static Dependencies of(ImmutableSetMultimap<ClassName, TypeElement> deps) {
      ImmutableSetMultimap.Builder<ClassName, TypeElement> globalDeps =
          ImmutableSetMultimap.builder();
      ImmutableSetMultimap.Builder<TestDepKey, TypeElement> testDeps =
          ImmutableSetMultimap.builder();
      for (ClassName component : deps.keySet()) {
        for (TypeElement dep : deps.get(component)) {
          Optional<TypeElement> testElement = getEnclosingTestElement(dep);
          if (testElement.isPresent()) {
            testDeps.put(TestDepKey.of(component, ClassName.get(testElement.get())), dep);
          } else {
            globalDeps.put(component, dep);
          }
        }
      }
      return new Dependencies(globalDeps.build(), testDeps.build());
    }

    // TODO(user): Consider checking for the enclosing class when processing the dependency
    // in AggregatedDepsProcessor and storing it on the @AggregatedDeps annotation instead.
    private static Optional<TypeElement> getEnclosingTestElement(Element element) {
      while (element.getKind() != ElementKind.PACKAGE) {
        if (Processors.hasAnnotation(element, ClassNames.ANDROID_ROBOLECTRIC_ENTRY_POINT)
            || Processors.hasAnnotation(element, ClassNames.ANDROID_EMULATOR_ENTRY_POINT)) {
          return Optional.of(MoreElements.asType(element));
        }
        element = element.getEnclosingElement();
      }
      return Optional.empty();
    }

    // Dependencies keyed by the component they're installed in.
    private final ImmutableSetMultimap<ClassName, TypeElement> globalDeps;
    private final ImmutableSetMultimap<TestDepKey, TypeElement> testDeps;

    Dependencies(
        ImmutableSetMultimap<ClassName, TypeElement> globalDeps,
        ImmutableSetMultimap<TestDepKey, TypeElement> testDeps) {
      this.globalDeps = globalDeps;
      this.testDeps = testDeps;
    }

    /** Returns the dependencies to be installed in the given component for the given test. */
    ImmutableSet<TypeElement> get(ClassName component, ClassName test) {
      return ImmutableSet.<TypeElement>builder()
          .addAll(globalDeps.get(component))
          .addAll(testDeps.get(TestDepKey.of(component, test)))
          .build();
    }
  }

  private final Dependencies modules;
  private final Dependencies entryPoints;
  private final Dependencies componentEntryPoints;

  private ComponentDependencies(
      ImmutableSetMultimap<ClassName, TypeElement> modules,
      ImmutableSetMultimap<ClassName, TypeElement> entryPoints,
      ImmutableSetMultimap<ClassName, TypeElement> componentEntryPoints) {
    this.modules = Dependencies.of(modules);
    this.entryPoints = Dependencies.of(entryPoints);
    this.componentEntryPoints = Dependencies.of(componentEntryPoints);
  }

  /** Returns the modules for a component, without any filtering. */
  public ImmutableSet<TypeElement> getModules(ClassName componentName, ClassName rootName) {
    return modules.get(componentName, rootName);
  }

  /** Returns the entry points associated with the given a component. */
  public ImmutableSet<TypeElement> getEntryPoints(ClassName componentName, ClassName rootName) {
    return entryPoints.get(componentName, rootName);
  }

  /** Returns the component entry point associated with the given a component. */
  public ImmutableSet<TypeElement> getComponentEntryPoints(
      ClassName componentName, ClassName rootName) {
    return componentEntryPoints.get(componentName, rootName);
  }

  /**
   * Pulls the component dependencies from the {@code packageName}.
   *
   * <p>Dependency files are generated by the {@link AggregatedDepsProcessor}, and have the form:
   *
   * <pre>{@code
   * {@literal @}AggregatedDeps(
   *   components = {
   *       "foo.FooComponent",
   *       "bar.BarComponent"
   *   },
   *   modules = "baz.BazModule"
   * )
   *
   * }</pre>
   */
  public static ComponentDependencies from(Elements elements) {
    ImmutableSetMultimap.Builder<ClassName, TypeElement> modules = ImmutableSetMultimap.builder();
    ImmutableSetMultimap.Builder<ClassName, TypeElement> entryPoints =
        ImmutableSetMultimap.builder();
    ImmutableSetMultimap.Builder<ClassName, TypeElement> componentEntryPoints =
        ImmutableSetMultimap.builder();
    Map<String, ComponentDescriptor> descriptorLookup = new HashMap<>();
    DefineComponents.componentDescriptors(elements)
        .forEach(descriptor -> descriptorLookup.put(descriptor.component().toString(), descriptor));

    for (AggregatedDeps deps : getAggregatedDeps(elements)) {
      for (String component : deps.components()) {
        checkState(
            descriptorLookup.containsKey(component),
            "%s is not a valid Component. Did you add or remove code in package %s?",
            component,
            AGGREGATING_PACKAGE,
            component);

        ComponentDescriptor desc = descriptorLookup.get(component);
        modules.putAll(desc.component(), toTypeElements(elements, deps.modules()));
        entryPoints.putAll(desc.component(), toTypeElements(elements, deps.entryPoints()));
        componentEntryPoints.putAll(
            desc.component(), toTypeElements(elements, deps.componentEntryPoints()));
      }
    }

    return new ComponentDependencies(
        modules.build(), entryPoints.build(), componentEntryPoints.build());
  }

  /** Returns the top-level elements of the aggregated deps package. */
  private static ImmutableList<AggregatedDeps> getAggregatedDeps(Elements elements) {
    PackageElement packageElement = elements.getPackageElement(AGGREGATING_PACKAGE);
    checkState(
        packageElement != null,
        "Couldn't find package %s. Did you mark your @Module classes with @InstallIn annotations?",
        AGGREGATING_PACKAGE);

    List<? extends Element> aggregatedDepsElements = packageElement.getEnclosedElements();
    checkState(
        !aggregatedDepsElements.isEmpty(),
        "No dependencies found. Did you mark your @Module classes with @InstallIn annotations?");

    ImmutableList.Builder<AggregatedDeps> builder = ImmutableList.builder();
    for (Element element : aggregatedDepsElements) {
      ProcessorErrors.checkState(
          element.getKind() == ElementKind.CLASS,
          element,
          "Only classes may be in package %s. Did you add custom code in the package?",
          AGGREGATING_PACKAGE);

      AggregatedDeps aggregatedDeps = element.getAnnotation(AggregatedDeps.class);
      ProcessorErrors.checkState(
          aggregatedDeps != null,
          element,
          "Classes in package %s must be annotated with @AggregatedDeps: %s. Found: %s.",
          AGGREGATING_PACKAGE,
          element.getSimpleName(),
          element.getAnnotationMirrors());

      builder.add(aggregatedDeps);
    }
    return builder.build();
  }
}
