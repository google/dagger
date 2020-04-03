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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static dagger.hilt.processor.internal.aggregateddeps.AggregatedDepsGenerator.AGGREGATING_PACKAGE;

import com.google.auto.value.AutoValue;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.BadInputException;
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
    private static final class Builder {
      private final ImmutableSetMultimap.Builder<ClassName, TypeElement> globalDeps =
          ImmutableSetMultimap.builder();
      private final ImmutableSetMultimap.Builder<TestDepKey, TypeElement> testDeps =
          ImmutableSetMultimap.builder();
      private final ImmutableSetMultimap.Builder<ClassName, TypeElement> ignoreDeps =
          ImmutableSetMultimap.builder();

      Builder addDep(ClassName component, Optional<ClassName> test, TypeElement dep) {
        if (test.isPresent()) {
          testDeps.put(TestDepKey.of(component, test.get()), dep);
        } else {
          globalDeps.put(component, dep);
        }
        return this;
      }

      Builder ignoreDeps(ClassName test, ImmutableSet<TypeElement> deps) {
        ignoreDeps.putAll(test, deps);
        return this;
      }

      Dependencies build() {
        return new Dependencies(globalDeps.build(), testDeps.build(), ignoreDeps.build());
      }
    }

    // Stores global deps keyed by component.
    private final ImmutableSetMultimap<ClassName, TypeElement> globalDeps;

    // Stores test deps keyed by component and test.
    private final ImmutableSetMultimap<TestDepKey, TypeElement> testDeps;

    // Stores ignored deps keyed by test.
    private final ImmutableSetMultimap<ClassName, TypeElement> ignoreDeps;

    Dependencies(
        ImmutableSetMultimap<ClassName, TypeElement> globalDeps,
        ImmutableSetMultimap<TestDepKey, TypeElement> testDeps,
        ImmutableSetMultimap<ClassName, TypeElement> ignoreDeps) {
      this.globalDeps = globalDeps;
      this.testDeps = testDeps;
      this.ignoreDeps = ignoreDeps;
    }

    /** Returns the dependencies to be installed in the given component for the given test. */
    ImmutableSet<TypeElement> get(ClassName component, ClassName test) {
      ImmutableSet<TypeElement> ignoreTestDeps = ignoreDeps.get(test);
      return ImmutableSet.<TypeElement>builder()
          .addAll(
              globalDeps.get(component).stream()
                  .filter(dep -> !ignoreTestDeps.contains(dep))
                  .collect(toImmutableSet()))
          .addAll(testDeps.get(TestDepKey.of(component, test)))
          .build();
    }
  }

  private final Dependencies modules;
  private final Dependencies entryPoints;
  private final Dependencies componentEntryPoints;

  private ComponentDependencies(
      Dependencies modules, Dependencies entryPoints, Dependencies componentEntryPoints) {
    this.modules = modules;
    this.entryPoints = entryPoints;
    this.componentEntryPoints = componentEntryPoints;
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
    Dependencies.Builder moduleDeps = new Dependencies.Builder();
    Dependencies.Builder entryPointDeps = new Dependencies.Builder();
    Dependencies.Builder componentEntryPointDeps = new Dependencies.Builder();
    Map<String, TypeElement> testElements = new HashMap<>();
    ImmutableMap<String, ComponentDescriptor> descriptorLookup =
        DefineComponents.componentDescriptors(elements).stream()
            .collect(
                toImmutableMap(
                    descriptor -> descriptor.component().toString(),
                    descriptor -> descriptor));

    for (AggregatedDeps deps : getAggregatedDeps(elements)) {
      Optional<ClassName> test = Optional.empty();
      if (!deps.test().isEmpty()) {
        testElements.computeIfAbsent(deps.test(), testName -> elements.getTypeElement(testName));
        test = Optional.of(ClassName.get(testElements.get(deps.test())));
      }

      for (String component : deps.components()) {
        checkState(
            descriptorLookup.containsKey(component),
            "%s is not a valid Component. Did you add or remove code in package %s?",
            component,
            AGGREGATING_PACKAGE,
            component);

        ComponentDescriptor desc = descriptorLookup.get(component);
        for (String dep : deps.modules()) {
          moduleDeps.addDep(desc.component(), test, elements.getTypeElement(dep));
        }
        for (String dep : deps.entryPoints()) {
          entryPointDeps.addDep(desc.component(), test, elements.getTypeElement(dep));
        }
        for (String dep : deps.componentEntryPoints()) {
          componentEntryPointDeps.addDep(desc.component(), test, elements.getTypeElement(dep));
        }
      }
    }

    for (TypeElement testElement : testElements.values()) {
      if (Processors.hasAnnotation(testElement, ClassNames.IGNORE_MODULES)) {
        moduleDeps.ignoreDeps(ClassName.get(testElement), getIgnoredModules(testElement, elements));
      }
    }

    return new ComponentDependencies(
        validateModules(moduleDeps.build(), elements),
        entryPointDeps.build(),
        componentEntryPointDeps.build());
  }

  // Validate that the @UninstallModules doesn't contain any test modules.
  private static Dependencies validateModules(Dependencies moduleDeps, Elements elements) {
    SetMultimap<ClassName, TypeElement> invalidTestModules = HashMultimap.create();
    moduleDeps.testDeps.entries().stream()
        .filter(e -> moduleDeps.ignoreDeps.containsEntry(e.getKey().test(), e.getValue()))
        .forEach(e -> invalidTestModules.put(e.getKey().test(), e.getValue()));

    // Currently we don't have a good way to throw an error for all tests, so we sort (to keep the
    // error reporting order stable) and then choose the first test.
    // TODO(user): Consider using ProcessorErrorHandler directly to report all errors at once?
    Optional<ClassName> invalidTest =
        invalidTestModules.keySet().stream()
            .min((test1, test2) -> test1.toString().compareTo(test2.toString()));
    if (invalidTest.isPresent()) {
      throw new BadInputException(
          String.format(
              "@UninstallModules on test, %s, should not containing test modules, "
                  + "but found: %s",
              invalidTest.get(),
              invalidTestModules.get(invalidTest.get()).stream()
                  // Sort modules to keep stable error messages.
                  .sorted((test1, test2) -> test1.toString().compareTo(test2.toString()))
                  .collect(toImmutableList())),
          elements.getTypeElement(invalidTest.get().toString()));
    }
    return moduleDeps;
  }

  private static ImmutableSet<TypeElement> getIgnoredModules(
      TypeElement testElement, Elements elements) {
    ImmutableList<TypeElement> userUninstallModules =
        Processors.getAnnotationClassValues(
            elements,
            Processors.getAnnotationMirror(testElement, ClassNames.IGNORE_MODULES),
            "value");

    // For pkg-private modules, find the generated wrapper class and uninstall that instead.
    ImmutableSet.Builder<TypeElement> builder = ImmutableSet.builder();
    for (TypeElement ignoreModule : userUninstallModules) {
      Optional<PkgPrivateMetadata> pkgPrivateMetadata =
          PkgPrivateMetadata.of(elements, ignoreModule, ClassNames.MODULE);
      builder.add(
          pkgPrivateMetadata.isPresent()
              ? elements.getTypeElement(pkgPrivateMetadata.get().generatedClassName().toString())
              : ignoreModule);
    }
    return builder.build();
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
