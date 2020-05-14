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

package dagger.hilt.processor.internal.root;

import static com.google.common.base.Suppliers.memoize;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ComponentDescriptor;
import dagger.hilt.processor.internal.ComponentTree;
import dagger.hilt.processor.internal.KotlinMetadata;
import dagger.hilt.processor.internal.Processors;
import dagger.hilt.processor.internal.aggregateddeps.ComponentDependencies;
import dagger.hilt.processor.internal.aliasof.AliasOfs;
import java.util.List;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

/** Contains metadata about the given hilt root. */
public final class RootMetadata {
  private static final ClassName APPLICATION_CONTEXT_MODULE =
      ClassName.get("dagger.hilt.android.internal.modules", "ApplicationContextModule");

  static RootMetadata create(
      Root root,
      ComponentTree componentTree,
      ComponentDependencies deps,
      Optional<ClassName> mergedTestApplication,
      ProcessingEnvironment env) {
    RootMetadata metadata = new RootMetadata(root, componentTree, deps, mergedTestApplication, env);
    metadata.validate();
    return metadata;
  }

  private final Root root;
  private final ProcessingEnvironment env;
  private final Elements elements;
  private final ComponentTree componentTree;
  private final ComponentDependencies deps;
  private final Optional<ClassName> mergedTestApplication;
  private final Supplier<ImmutableSetMultimap<ClassName, ClassName>> scopesByComponent =
      memoize(this::getScopesByComponentUncached);
  private final Supplier<TestRootMetadata> testRootMetadata =
      memoize(this::testRootMetadataUncached);

  private RootMetadata(
      Root root,
      ComponentTree componentTree,
      ComponentDependencies deps,
      Optional<ClassName> mergedTestApplication,
      ProcessingEnvironment env) {
    this.root = root;
    this.env = env;
    this.elements = env.getElementUtils();
    this.componentTree = componentTree;
    this.mergedTestApplication = mergedTestApplication;
    this.deps = deps;
  }

  public Root root() {
    return root;
  }

  public ComponentTree componentTree() {
    return componentTree;
  }

  public ComponentDependencies deps() {
    return deps;
  }

  public ImmutableSet<TypeElement> modules(ClassName componentName) {
    return deps.getModules(componentName, root.classname());
  }

  public ImmutableSet<TypeName> entryPoints(ClassName componentName) {
    return ImmutableSet.<TypeName>builder()
        .addAll(getUserDefinedEntryPoints(componentName))
        .add(componentName)
        .build();
  }

  public ImmutableSet<ClassName> scopes(ClassName componentName) {
    return scopesByComponent.get().get(componentName);
  }

  /**
   * Returns all modules in the given component that do not have accessible default constructors.
   * Note that a non-static module nested in an outer class is considered to have no default
   * constructors, since an instance of the outer class is needed to construct the module. This also
   * filters out framework modules directly referenced by the codegen, since those are already known
   * about and are specifically handled in the codegen.
   */
  public ImmutableSet<TypeElement> modulesThatDaggerCannotConstruct(ClassName componentName) {
    return modules(componentName).stream()
        .filter(module -> !daggerCanConstruct(module))
        .filter(module -> !APPLICATION_CONTEXT_MODULE.equals(ClassName.get(module)))
        .collect(toImmutableSet());
  }

  public TestRootMetadata testRootMetadata() {
    return testRootMetadata.get();
  }

  public boolean waitForBindValue() {
    return false;
  }

  private TestRootMetadata testRootMetadataUncached() {
    return TestRootMetadata.of(env, root().element());
  }

  /**
   * Validates that the {@link RootType} annotation is compatible with its {@link TypeElement} and
   * {@link ComponentDependencies}. If not, throws exception.
   */
  private void validate() {

    // Only test modules in the application component can be missing default constructor
    for (ComponentDescriptor componentDescriptor : componentTree.getComponentDescriptors()) {
      ClassName componentName = componentDescriptor.component();
      for (TypeElement extraModule : modulesThatDaggerCannotConstruct(componentName)) {
        if (root.type().isTestRoot() && !componentName.equals(ClassNames.APPLICATION_COMPONENT)) {
          env.getMessager()
              .printMessage(
                  Diagnostic.Kind.ERROR,
                  "[Hilt] All test modules (unless installed in ApplicationComponent) must use "
                      + "static provision methods or have a visible, no-arg constructor. Found: "
                      + extraModule.getQualifiedName(),
                  root.element());
        } else if (!root.type().isTestRoot()) {
          env.getMessager()
              .printMessage(
                  Diagnostic.Kind.ERROR,
                  "[Hilt] All modules must be static and use static provision methods or have a "
                      + "visible, no-arg constructor. Found: "
                      + extraModule.getQualifiedName(),
                  root.element());
        }
      }
    }
  }

  private ImmutableSet<TypeName> getUserDefinedEntryPoints(ClassName componentName) {
    ImmutableSet.Builder<TypeName> entryPointSet = ImmutableSet.builder();
    entryPointSet.add(ClassNames.GENERATED_COMPONENT);
    for (TypeElement element : deps.getEntryPoints(componentName, root.classname())) {
      entryPointSet.add(ClassName.get(element));
    }
    return entryPointSet.build();
  }

  private ImmutableSetMultimap<ClassName, ClassName> getScopesByComponentUncached() {
    ImmutableSetMultimap.Builder<ClassName, ClassName> builder = ImmutableSetMultimap.builder();

    ImmutableSet<ClassName> defineComponentScopes =
        componentTree.getComponentDescriptors().stream()
            .flatMap(descriptor -> descriptor.scopes().stream())
            .collect(toImmutableSet());

    AliasOfs aliasOfs = new AliasOfs(env, defineComponentScopes);

    for (ComponentDescriptor componentDescriptor : componentTree.getComponentDescriptors()) {
      for (ClassName scope : componentDescriptor.scopes()) {
        builder.put(componentDescriptor.component(), scope);
        builder.putAll(componentDescriptor.component(), aliasOfs.getAliasesFor(scope));
      }
    }

    return builder.build();
  }

  private static boolean daggerCanConstruct(TypeElement type) {
    Optional<KotlinMetadata> kotlinMetadata = KotlinMetadata.of(type);
    boolean isKotlinObject =
        kotlinMetadata
            .map(metadata -> metadata.isObjectClass() || metadata.isCompanionObjectClass())
            .orElse(false);
    if (isKotlinObject) {
      // Treat Kotlin object modules as if Dagger can construct them (it technically can't, but it
      // doesn't need to as it can use them since all their provision methods are static).
      return true;
    }

    return !isInnerClass(type)
        && !hasNonDaggerAbstractMethod(type)
        && (hasOnlyStaticProvides(type) || hasVisibleEmptyConstructor(type));
  }

  private static boolean isInnerClass(TypeElement type) {
    return type.getNestingKind().isNested() && !type.getModifiers().contains(STATIC);
  }

  private static boolean hasNonDaggerAbstractMethod(TypeElement type) {
    // TODO(user): Actually this isn't really supported b/28989613
    return ElementFilter.methodsIn(type.getEnclosedElements()).stream()
        .filter(method -> method.getModifiers().contains(ABSTRACT))
        .anyMatch(method -> !Processors.hasDaggerAbstractMethodAnnotation(method));
  }

  private static boolean hasOnlyStaticProvides(TypeElement type) {
    // TODO(user): Check for @Produces too when we have a producers story
    return ElementFilter.methodsIn(type.getEnclosedElements()).stream()
        .filter(method -> Processors.hasAnnotation(method, ClassNames.PROVIDES))
        .allMatch(method -> method.getModifiers().contains(STATIC));
  }

  private static boolean hasVisibleEmptyConstructor(TypeElement type) {
    List<ExecutableElement> constructors = ElementFilter.constructorsIn(type.getEnclosedElements());
    return constructors.isEmpty()
        || constructors.stream()
            .filter(constructor -> constructor.getParameters().isEmpty())
            .anyMatch(constructor -> !constructor.getModifiers().contains(PRIVATE));
  }
}
