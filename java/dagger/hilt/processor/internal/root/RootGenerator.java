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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static dagger.hilt.processor.internal.Processors.toClassNames;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.android.processor.internal.testing.InternalTestRootMetadata;
import dagger.hilt.android.processor.internal.testing.TestApplicationGenerator;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ComponentDescriptor;
import dagger.hilt.processor.internal.ComponentGenerator;
import dagger.hilt.processor.internal.ComponentNames;
import dagger.hilt.processor.internal.ComponentTree;
import dagger.hilt.processor.internal.KotlinMetadata;
import dagger.hilt.processor.internal.Processors;
import dagger.hilt.processor.internal.aggregateddeps.ComponentDependencies;
import dagger.hilt.processor.internal.aliasof.AliasOfs;
import dagger.hilt.processor.internal.definecomponent.DefineComponents;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

/** Generates components and any other classes needed for a root. */
final class RootGenerator {
  private static final ClassName APPLICATION_CONTEXT_MODULE =
      ClassName.get("dagger.hilt.android.internal.modules", "ApplicationContextModule");
  private static final ClassName DAGGER_PROCESSING_OPTIONS =
      ClassName.get("dagger", "DaggerProcessingOptions");

  static void generate(Root root, ProcessingEnvironment env) throws IOException {
    new RootGenerator(root, env).generate();
  }

  private final Root root;
  private final ProcessingEnvironment env;
  private final Elements elements;
  private final ComponentTree componentTree;
  private final ComponentDependencies deps;

  private RootGenerator(Root root, ProcessingEnvironment env) {
    this.root = root;
    this.env = env;
    this.elements = env.getElementUtils();
    this.componentTree = ComponentTree.from(DefineComponents.componentDescriptors(elements));
    this.deps = ComponentDependencies.from(elements);
  }

  private void generate() throws IOException {
    validateRoot();
    generateTestApplication();
    generateComponentForEachScope();
  }

  private void generateTestApplication() throws IOException {
    if (root.type().equals(RootType.TEST_ROOT)) {
      ImmutableSet<TypeElement> modules = getModules(ClassNames.APPLICATION_COMPONENT);
      new TestApplicationGenerator(
              env,
              InternalTestRootMetadata.of(env, root.element()),
              modulesThatDaggerCannotConstruct(modules),
              false)
          .generate();
    }
  }

  /**
   * Validates that the {@link RootType} annotation is compatible with its {@link TypeElement} and
   * {@link ComponentDependencies}. If not, throws exception.
   */
  private void validateRoot() {

    // Only test modules in the application component can be missing default constructor
    for (ComponentDescriptor componentDescriptor : componentTree.getComponentDescriptors()) {
      ClassName componentName = componentDescriptor.component();
      for (TypeElement extraModule : modulesThatDaggerCannotConstruct(getModules(componentName))) {
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

  private void generateComponentForEachScope() throws IOException {
    // TODO(user): Should including modules for unsupported scopes be an error/warning?
    ImmutableMultimap<ComponentDescriptor, ClassName> scopes = getScopes();

    // TODO(user): Consider moving all of this logic into ComponentGenerator?
    TypeSpec.Builder componentsWrapper =
        TypeSpec.classBuilder(getComponentsWrapperClassName())
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    Processors.addGeneratedAnnotation(componentsWrapper, env, ClassNames.ROOT_PROCESSOR.toString());

    ImmutableMap<ComponentDescriptor, ClassName> subcomponentBuilderModules =
        subcomponentBuilderModules(componentsWrapper);

    for (ComponentDescriptor componentDescriptor : componentTree.getComponentDescriptors()) {
      ImmutableSet<ClassName> modules =
          ImmutableSet.<ClassName>builder()
              .addAll(toClassNames(getModules(componentDescriptor.component())))
              .addAll(
                  componentTree.childrenOf(componentDescriptor).stream()
                      .map(subcomponentBuilderModules::get)
                      .collect(toImmutableSet()))
              .build();

      componentsWrapper.addType(
          new ComponentGenerator(
                  env,
                  getComponentClassName(componentDescriptor),
                  root.element(),
                  Optional.empty(),
                  modules,
                  ImmutableSet.<TypeName>builder()
                      .addAll(getEntryPoints(componentDescriptor))
                      .add(componentDescriptor.component())
                      .build(),
                  scopes.get(componentDescriptor),
                  ImmutableList.of(),
                  componentAnnotation(componentDescriptor),
                  componentBuilder(componentDescriptor))
              .generate().toBuilder().addModifiers(Modifier.STATIC).build());

    }

    RootFileFormatter.write(
        JavaFile.builder(root.classname().packageName(), componentsWrapper.build()).build(),
        env.getFiler());
  }

  private ImmutableMap<ComponentDescriptor, ClassName> subcomponentBuilderModules(
      TypeSpec.Builder componentsWrapper) throws IOException {
    ImmutableMap.Builder<ComponentDescriptor, ClassName> modules = ImmutableMap.builder();
    for (ComponentDescriptor descriptor : componentTree.getComponentDescriptors()) {
      // Root component builders don't have subcomponent builder modules
      if (!descriptor.isRoot() && descriptor.creator().isPresent()) {
        ClassName component = getComponentClassName(descriptor);
        ClassName builder = descriptor.creator().get();
        ClassName module = component.peerClass(component.simpleName() + "BuilderModule");
        componentsWrapper.addType(subcomponentBuilderModule(component, builder, module));
        modules.put(descriptor, module);
      }
    }
    return modules.build();
  }

  // Generates:
  // @Module(subcomponents = FooSubcomponent.class)
  // interface FooSubcomponentBuilderModule {
  //   @Binds FooSubcomponentInterfaceBuilder bind(FooSubcomponent.Builder builder);
  // }
  private TypeSpec subcomponentBuilderModule(
      ClassName componentName, ClassName builderName, ClassName moduleName) throws IOException {
    TypeSpec.Builder subcomponentBuilderModule =
        TypeSpec.interfaceBuilder(moduleName)
            .addOriginatingElement(root.element())
            .addModifiers(ABSTRACT)
            .addAnnotation(
                AnnotationSpec.builder(ClassNames.MODULE)
                    .addMember("subcomponents", "$T.class", componentName)
                    .build())
            .addMethod(
                MethodSpec.methodBuilder("bind")
                    .addModifiers(ABSTRACT, PUBLIC)
                    .addAnnotation(ClassNames.BINDS)
                    .returns(builderName)
                    .addParameter(componentName.nestedClass("Builder"), "builder")
                    .build());

    Processors.addGeneratedAnnotation(
        subcomponentBuilderModule, env, ClassNames.ROOT_PROCESSOR.toString());

    return subcomponentBuilderModule.build();
  }

  private Optional<TypeSpec> componentBuilder(ComponentDescriptor descriptor) {
    return descriptor
        .creator()
        .map(
            creator ->
                TypeSpec.interfaceBuilder("Builder")
                    .addOriginatingElement(root.element())
                    .addModifiers(STATIC, ABSTRACT)
                    .addSuperinterface(creator)
                    .addAnnotation(componentBuilderAnnotation(descriptor))
                    .build());
  }

  private ClassName componentAnnotation(ComponentDescriptor componentDescriptor) {
    if (!componentDescriptor.isRoot()) {
      return ClassNames.SUBCOMPONENT;
    } else {
      return ClassNames.COMPONENT;
    }
  }

  private ClassName componentBuilderAnnotation(ComponentDescriptor componentDescriptor) {
    if (componentDescriptor.isRoot()) {
      return ClassNames.COMPONENT_BUILDER;
    } else {
      return ClassNames.SUBCOMPONENT_BUILDER;
    }
  }

  private ImmutableSet<TypeElement> getModules(ClassName componentName) {
    return deps.getModules(componentName, root.classname());
  }

  private ImmutableSet<TypeName> getEntryPoints(ComponentDescriptor componentDescriptor) {
    ClassName componentName = componentDescriptor.component();
    ImmutableSet.Builder<TypeName> entryPointSet = ImmutableSet.builder();
    entryPointSet.add(ClassNames.GENERATED_COMPONENT);
    for (TypeElement element : deps.getEntryPoints(componentName, root.classname())) {
      entryPointSet.add(ClassName.get(element));
    }

    // TODO(user): move the creation of these EntryPoints to a separate processor?
    if (root.type().isTestRoot()) {
      if (componentName.equals(ClassNames.APPLICATION_COMPONENT)) {
        entryPointSet.add(ParameterizedTypeName.get(ClassNames.TEST_INJECTOR, root.classname()));
      }
    }
    return entryPointSet.build();
  }

  private ImmutableMultimap<ComponentDescriptor, ClassName> getScopes() {
    ImmutableMultimap.Builder<ComponentDescriptor, ClassName> builder = ImmutableMultimap.builder();

    ImmutableSet<ClassName> defineComponentScopes =
        componentTree.getComponentDescriptors().stream()
            .flatMap(descriptor -> descriptor.scopes().stream())
            .collect(toImmutableSet());

    AliasOfs aliasOfs = new AliasOfs(env, defineComponentScopes);

    for (ComponentDescriptor componentDescriptor : componentTree.getComponentDescriptors()) {
      for (ClassName scope : componentDescriptor.scopes()) {
        builder.put(componentDescriptor, scope);
        builder.putAll(componentDescriptor, aliasOfs.getAliasesFor(scope));
      }
    }

    return builder.build();
  }

  private ClassName getPartialRootModuleClassName() {
    return getComponentsWrapperClassName().nestedClass("PartialRootModule");
  }

  private ClassName getComponentsWrapperClassName() {
    return ComponentNames.generatedComponentsWrapper(root.classname());
  }

  private ClassName getComponentClassName(ComponentDescriptor componentDescriptor) {
    return ComponentNames.generatedComponent(root.classname(), componentDescriptor.component());
  }

  /**
   * Returns all modules in the given component that do not have accessible default constructors.
   * Note that a non-static module nested in an outer class is considered to have no default
   * constructors, since an instance of the outer class is needed to construct the module. This also
   * filters out framework modules directly referenced by the codegen, since those are already known
   * about and are specifically handled in the codegen.
   */
  private static ImmutableSet<TypeElement> modulesThatDaggerCannotConstruct(
      ImmutableSet<TypeElement> modules) {
    return modules.stream()
        .filter(module -> !daggerCanConstruct(module))
        .filter(module -> !APPLICATION_CONTEXT_MODULE.equals(ClassName.get(module)))
        .collect(toImmutableSet());
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
